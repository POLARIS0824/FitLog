package com.example.fitlog.data.analysis

import android.content.Context
import androidx.room.withTransaction
import com.example.fitlog.data.analysis.adapter.RecordingDiaryParser
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class DiaryAnalysisStorageException(cause: Exception) : IOException("Diary analysis persistence failed", cause)
enum class AnalysisStorageState { UNINITIALIZED, READY, FAILED }

/** Confirmation data is independent of index rebuilds and the currently connected vault. */
class DiaryAnalysisRepository internal constructor(
    private val database: DiaryAnalysisDatabase,
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val dao = database.analysis()
    private val initialization = Mutex()
    private var initialized = false
    private val mutableState = MutableStateFlow(AnalysisStorageState.UNINITIALIZED)
    val storageState: StateFlow<AnalysisStorageState> = mutableState.asStateFlow()
    private var executor: DiaryParseExecutor? = null

    companion object {
        @Volatile private var instance: DiaryAnalysisRepository? = null
        fun get(context: Context): DiaryAnalysisRepository = instance ?: synchronized(this) {
            instance ?: DiaryAnalysisRepository(DiaryAnalysisDatabase.get(context.applicationContext)).also { instance = it }
        }
    }

    /** Run once per application instance, before any new attempt can be inserted. */
    suspend fun initialize() = initialization.withLock {
        if (!initialized) {
            try {
                persist { database.withTransaction { dao.recoverInterrupted(now()) } }
                initialized = true
                mutableState.value = AnalysisStorageState.READY
            } catch (e: DiaryAnalysisStorageException) {
                mutableState.value = AnalysisStorageState.FAILED
                throw e
            }
        }
    }

    /** The eventual production adapter installs one application-owned serial executor. */
    @Synchronized
    internal fun executor(parser: RecordingDiaryParser): DiaryParseExecutor = executor ?: DiaryParseExecutor(
        this, parser, CoroutineScope(SupervisorJob() + Dispatchers.IO), now, newId,
    ).also { executor = it }

    suspend fun confirmed(sourceKey: SourceKey): ConfirmedDiaryRecord? {
        initialize()
        return persist { dao.confirmed(sourceKey.vaultId, sourceKey.relPath)?.ordered() }
    }

    fun observeConfirmed(sourceKey: SourceKey) = dao.observeConfirmed(sourceKey.vaultId, sourceKey.relPath)
        .map { it?.ordered() }

    suspend fun snapshots(sourceKey: SourceKey): List<ConfirmationSnapshotRow> {
        initialize()
        return persist { dao.snapshots(sourceKey.vaultId, sourceKey.relPath) }
    }

    internal suspend fun successful(key: DiaryParseKey): ParseRunRow? = persist {
        dao.successful(key.sourceKey.vaultId, key.sourceKey.relPath, key.contentHash, key.hashVersion, key.extractorVersion)
    }

    internal suspend fun startRun(row: ParseRunRow) = persist { dao.insertRun(row) }

    internal suspend fun finishRun(id: String, status: ParseRunStatus, at: Long,
        failureCode: String? = null, rawModelJson: String? = null, candidateJson: String? = null) = persist {
        database.withTransaction {
            check(dao.finishRun(id, status, at, failureCode, rawModelJson, candidateJson) == 1) {
                "Parse attempt is no longer running"
            }
        }
    }

    /** Cancellation can arrive after a terminal write committed; leave completed attempts untouched. */
    internal suspend fun interruptRun(id: String, at: Long, reason: String) = persist {
        database.withTransaction {
            dao.finishRun(id, ParseRunStatus.INTERRUPTED, at, reason, null, null)
        }
    }

    internal fun decodeCandidate(run: ParseRunRow): DiaryAnalysis = try {
        check(run.status == ParseRunStatus.SUCCEEDED)
        DiaryAnalysisCodec.decodeCandidate(checkNotNull(run.candidateJson)).also {
            check(it.parseKey == run.parseKey()) { "Candidate identity differs from its attempt" }
        }
    } catch (e: Exception) {
        mutableState.value = AnalysisStorageState.FAILED
        throw DiaryAnalysisStorageException(e)
    }

    suspend fun confirm(request: DiaryConfirmation): DiaryConfirmationResult {
        initialize()
        return persist {
            database.withTransaction {
                val run = dao.run(request.parseRunId)
                if (run == null || run.status != ParseRunStatus.SUCCEEDED)
                    return@withTransaction DiaryConfirmationResult.Invalid(ConfirmationFailure.INVALID_PARSE_RUN)
                if (run.parseKey().sourceKey != request.sourceKey)
                    return@withTransaction DiaryConfirmationResult.Invalid(ConfirmationFailure.SOURCE_MISMATCH)
                val previous = dao.confirmed(request.sourceKey.vaultId, request.sourceKey.relPath)?.ordered()
                val revision = previous?.diary?.revision ?: 0L
                if (request.expectedRevision != revision)
                    return@withTransaction DiaryConfirmationResult.RevisionConflict(revision)
                val analysis = decodeCandidate(run)
                if (analysis.hasErrors && !request.acceptedPartialResult)
                    return@withTransaction DiaryConfirmationResult.Invalid(ConfirmationFailure.PARTIAL_RESULT_NOT_ACCEPTED)
                val sessions = try { normalizeReview(request, analysis) } catch (_: IllegalArgumentException) {
                    return@withTransaction DiaryConfirmationResult.Invalid(ConfirmationFailure.INVALID_DATA)
                }
                val at = now()
                if (previous != null) {
                    dao.insertSnapshot(ConfirmationSnapshotRow(newId(), run.vaultId, run.relPath, revision,
                        previous.diary.confirmedAt, at, previous.diary.parseRunId,
                        DiaryAnalysisCodec.SNAPSHOT_VERSION, DiaryAnalysisCodec.encodeSnapshot(previous)))
                }
                val diary = ConfirmedDiaryRow(previous?.diary?.id ?: newId(), run.vaultId, run.relPath,
                    request.date.toString(), run.contentHash, run.hashVersion, run.id, at,
                    Math.addExact(revision, 1), request.acceptedPartialResult)
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
                            val kg = when (set.unit.value) {
                                WeightUnit.KG -> set.weight.value
                                WeightUnit.LB -> set.weight.value?.times(0.45359237)
                                else -> null
                            }
                            ConfirmedSetRow(newId(), storedExercise.id, setIndex, set.weight.value,
                                set.unit.value, set.basis.value, set.reps.value, kg, set.groupIndex, set.setInGroup,
                                set.countOrigin, set.weight.provenance, set.unit.provenance, set.basis.provenance,
                                set.reps.provenance)
                        })
                    }
                }
                DiaryConfirmationResult.Confirmed(checkNotNull(dao.confirmed(run.vaultId, run.relPath)).ordered())
            }
        }
    }

    /** Never trust UI-supplied extraction provenance or userEdited flags. Derive them from the candidate. */
    private fun normalizeReview(request: DiaryConfirmation, analysis: DiaryAnalysis): List<ReviewedSession> {
        require(request.expectedRevision >= 0)
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
                val evidence = exercise.evidence
                if (evidence != null) {
                    require(evidence.segmentId == DiaryParseInput.SEGMENT_ID && evidence.quote.isNotBlank())
                    require((evidence.normalizedStart == null) == (evidence.normalizedEndExclusive == null))
                    if (evidence != original?.evidence) {
                        // Without the original text we cannot verify coordinates for a user-supplied quote.
                        require(evidence.normalizedStart == null)
                    }
                }
                val sourceSets = mutableSetOf<Pair<Int, Int>>()
                exercise.copy(sets = exercise.sets.map { set ->
                    require(set.weight.value == null || (set.weight.value.isFinite() && set.weight.value >= 0))
                    require(set.reps.value == null || set.reps.value > 0)
                    require((set.groupIndex == null) == (set.setInGroup == null))
                    val baseline = if (set.groupIndex != null && set.setInGroup != null) {
                        require(sourceSets.add(set.groupIndex to set.setInGroup))
                        requireNotNull(original?.sets?.find {
                            it.groupIndex == set.groupIndex && it.setInGroup == set.setInGroup
                        })
                    } else null
                    set.copy(weight = reviewedValue(set.weight.value, baseline?.weight),
                        unit = reviewedValue(set.unit.value, baseline?.unit),
                        basis = reviewedValue(set.basis.value, baseline?.basis),
                        reps = reviewedValue(set.reps.value, baseline?.reps),
                        countOrigin = baseline?.countOrigin)
                })
            })
        }
    }

    private fun <T> reviewedValue(value: T?, original: CandidateValue<T>?): ReviewedValue<T> = ReviewedValue(
        value, if (original == null) FieldProvenance(userEdited = value != null) else FieldProvenance(
            original.origin, original.inferred, original.inheritedFromGroup, value != original.value,
        ),
    )

    private suspend fun <T> persist(block: suspend () -> T): T = try {
        block().also { if (initialized) mutableState.value = AnalysisStorageState.READY }
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        mutableState.value = AnalysisStorageState.FAILED
        if (e is DiaryAnalysisStorageException) throw e
        throw DiaryAnalysisStorageException(e)
    }
}
