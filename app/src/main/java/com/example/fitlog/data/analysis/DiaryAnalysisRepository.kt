package com.example.fitlog.data.analysis

import android.content.Context
import androidx.room.withTransaction
import com.example.fitlog.data.analysis.adapter.RecordingDiaryParser
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class DiaryAnalysisStorageException(cause: Exception) : IOException("Diary analysis persistence failed", cause)
data class StoredDiaryParse(val parseRunId: String, val result: DiaryParseResult)

/** Confirmation data is independent of index rebuilds and the currently connected vault. */
class DiaryAnalysisRepository internal constructor(
    private val database: DiaryAnalysisDatabase,
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : DiaryAnalysisReader {
    private val dao = database.analysis()

    companion object {
        @Volatile private var instance: DiaryAnalysisRepository? = null
        fun get(context: Context): DiaryAnalysisRepository = instance ?: synchronized(this) {
            instance ?: DiaryAnalysisRepository(DiaryAnalysisDatabase.get(context.applicationContext)).also { instance = it }
        }
    }

    /** Caller owns the coroutine and prevents duplicate UI actions. Every call is an explicit attempt. */
    internal suspend fun parse(input: DiaryParseInput, parser: RecordingDiaryParser): StoredDiaryParse = withContext(Dispatchers.IO) {
        // Fail before requesting a paid model when the local database cannot be opened.
        persist { database.openHelper.writableDatabase }
        val id = newId()
        val startedAt = now()
        val execution = parser.execute(input)
        currentCoroutineContext().ensureActive()
        val result = execution.result
        val candidate = if (result is DiaryParseResult.Success) {
            check(result.analysis.parseKey == input.parseKey) { "Parser returned a different content identity" }
            DiaryAnalysisCodec.encodeCandidate(result.analysis)
        } else null
        val key = input.parseKey
        persist {
            dao.insertRun(ParseRunRow(id, key.sourceKey.vaultId, key.sourceKey.relPath, key.contentHash,
                key.hashVersion, key.extractorVersion,
                if (result is DiaryParseResult.Success) ParseRunStatus.SUCCEEDED else ParseRunStatus.FAILED,
                startedAt, now(), (result as? DiaryParseResult.Failure)?.reason?.name,
                execution.rawModelJson, candidate))
        }
        StoredDiaryParse(id, result)
    }

    suspend fun confirmed(sourceKey: SourceKey): ConfirmedDiaryRecord? =
        persist { dao.confirmed(sourceKey.vaultId, sourceKey.relPath)?.ordered() }

    override fun observeConfirmed(sourceKey: SourceKey) =
        dao.observeConfirmed(sourceKey.vaultId, sourceKey.relPath).map { it?.ordered() }
            .catch { throw readFailure(it) }

    suspend fun readParses(sourceKey: SourceKey): DiaryParseRecords {
        val rows = persist { dao.runs(sourceKey.vaultId, sourceKey.relPath) }
        return withContext(Dispatchers.Default) { parseRecords(rows) }
    }

    override fun observeParses(sourceKey: SourceKey) =
        dao.observeRuns(sourceKey.vaultId, sourceKey.relPath).map(::parseRecords)
            .flowOn(Dispatchers.IO).catch { throw readFailure(it) }

    private fun parseRecords(rows: List<ParseRunRow>): DiaryParseRecords {
        val attempts = rows.map { row ->
            DiaryParseAttempt(row.id, row.parseKey(), row.status, row.startedAt, row.finishedAt, row.failureCode)
        }
        val successful = rows.indexOfLast { it.status == ParseRunStatus.SUCCEEDED }
        if (successful < 0) return DiaryParseRecords(attempts)
        // A damaged candidate must not hide metadata or independently stored confirmation.
        return try {
            DiaryParseRecords(attempts, StoredDiaryCandidate(attempts[successful], decodeCandidate(rows[successful])))
        } catch (_: DiaryAnalysisStorageException) {
            DiaryParseRecords(attempts, candidateReadFailed = true)
        }
    }

    private fun readFailure(error: Throwable): Throwable =
        if (error is Exception && error !is CancellationException && error !is DiaryAnalysisStorageException)
            DiaryAnalysisStorageException(error) else error

    internal fun decodeCandidate(run: ParseRunRow): DiaryAnalysis = try {
        check(run.status == ParseRunStatus.SUCCEEDED)
        DiaryAnalysisCodec.decodeCandidate(checkNotNull(run.candidateJson)).also {
            check(it.parseKey == run.parseKey()) { "Candidate identity differs from its attempt" }
        }
    } catch (e: Exception) {
        throw DiaryAnalysisStorageException(e)
    }

    suspend fun confirm(request: DiaryConfirmation): DiaryConfirmationResult = persist {
        database.withTransaction {
            val run = dao.run(request.parseRunId)
            if (run == null || run.status != ParseRunStatus.SUCCEEDED)
                return@withTransaction DiaryConfirmationResult.Invalid(ConfirmationFailure.INVALID_PARSE_RUN)
            if (run.parseKey().sourceKey != request.sourceKey)
                return@withTransaction DiaryConfirmationResult.Invalid(ConfirmationFailure.SOURCE_MISMATCH)
            val analysis = decodeCandidate(run)
            if (analysis.hasErrors && !request.acceptedPartialResult)
                return@withTransaction DiaryConfirmationResult.Invalid(ConfirmationFailure.PARTIAL_RESULT_NOT_ACCEPTED)
            val sessions = try { normalizeReview(request, analysis) } catch (_: IllegalArgumentException) {
                return@withTransaction DiaryConfirmationResult.Invalid(ConfirmationFailure.INVALID_DATA)
            }
            val previous = dao.confirmed(run.vaultId, run.relPath)
            val diary = ConfirmedDiaryRow(previous?.diary?.id ?: newId(), run.vaultId, run.relPath,
                request.date.toString(), run.contentHash, run.hashVersion, run.id, now(), request.acceptedPartialResult)
            if (previous == null) dao.insertDiary(diary) else {
                check(dao.updateDiary(diary) == 1)
                dao.deleteSessions(diary.id)
            }
            sessions.forEachIndexed { sessionIndex, session ->
                val storedSession = ConfirmedSessionRow(newId(), diary.id, sessionIndex, session.notes, session.sourcePath)
                dao.insertSession(storedSession)
                session.exercises.forEachIndexed { exerciseIndex, exercise ->
                    val storedExercise = ConfirmedExerciseRow(newId(), storedSession.id, exerciseIndex,
                        exercise.rawName, exercise.notes, exercise.sourcePath, exercise.evidence)
                    dao.insertExercise(storedExercise)
                    dao.insertSets(exercise.sets.mapIndexed { setIndex, set ->
                        val kg = when (set.unit) {
                            WeightUnit.KG -> set.weight
                            WeightUnit.LB -> set.weight?.times(0.45359237)
                            else -> null
                        }
                        ConfirmedSetRow(newId(), storedExercise.id, setIndex, set.weight,
                            set.unit, set.basis, set.reps, kg, set.groupIndex, set.setInGroup, set.userEdited)
                    })
                }
            }
            DiaryConfirmationResult.Confirmed(checkNotNull(dao.confirmed(run.vaultId, run.relPath)).ordered())
        }
    }

    /** Validate final values and source references, deriving the group-level edit flag locally. */
    private fun normalizeReview(request: DiaryConfirmation, analysis: DiaryAnalysis): List<ReviewedSession> {
        require(request.date.year in 1..9999)
        val sessionPaths = mutableSetOf<String>()
        val exercisePaths = mutableSetOf<String>()
        val originalExercises = analysis.sessions.flatMap { it.exercises }.associateBy { it.path }
        return request.sessions.map { session ->
            session.sourcePath?.let { path ->
                require(sessionPaths.add(path))
                requireNotNull(analysis.sessions.find { it.path == path })
            }
            session.copy(exercises = session.exercises.map { exercise ->
                require(exercise.rawName.isNotBlank())
                require(exercise.sets.size <= 1000)
                val original = exercise.sourcePath?.let { path ->
                    require(exercisePaths.add(path))
                    requireNotNull(originalExercises[path])
                }
                exercise.evidence?.let {
                    require(it.segmentId == DiaryParseInput.SEGMENT_ID && it.quote.isNotBlank())
                }
                val sourceSets = mutableSetOf<Pair<Int, Int>>()
                exercise.copy(sets = exercise.sets.map { set ->
                    require(set.weight == null || (set.weight.isFinite() && set.weight >= 0))
                    require(set.reps == null || set.reps > 0)
                    require((set.groupIndex == null) == (set.setInGroup == null))
                    val baseline = if (set.groupIndex != null && set.setInGroup != null) {
                        require(sourceSets.add(set.groupIndex to set.setInGroup))
                        requireNotNull(original?.sets?.find {
                            it.groupIndex == set.groupIndex && it.setInGroup == set.setInGroup
                        })
                    } else null
                    set.copy(userEdited = baseline == null || set.weight != baseline.weight.value ||
                        set.unit != baseline.unit.value || set.basis != baseline.basis.value || set.reps != baseline.reps.value)
                })
            })
        }
    }

    private suspend fun <T> persist(block: suspend () -> T): T = try {
        block()
    } catch (e: Exception) {
        if (e is CancellationException || e is DiaryAnalysisStorageException) throw e
        throw DiaryAnalysisStorageException(e)
    }
}
