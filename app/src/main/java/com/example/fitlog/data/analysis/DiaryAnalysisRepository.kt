package com.example.fitlog.data.analysis

import androidx.room.withTransaction
import com.example.fitlog.data.analysis.adapter.RecordingDiaryParser
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
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
    private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO,
    private val computationDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.Default,
) : DiaryConfirmations {
    private val dao = database.analysis()

    /** Caller owns the coroutine and prevents duplicate UI actions. Every call is an explicit attempt. */
    internal suspend fun parse(input: DiaryParseInput, parser: RecordingDiaryParser): StoredDiaryParse = withContext(ioDispatcher) {
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

    suspend fun confirmed(sourceKey: SourceKey): TrainingRecordDetails? =
        withContext(computationDispatcher) { persist { dao.confirmed(sourceKey.vaultId, sourceKey.relPath)?.ordered() } }

    override fun observeConfirmed(sourceKey: SourceKey) =
        dao.observeConfirmed(sourceKey.vaultId, sourceKey.relPath).map { it?.ordered() }.flowOn(computationDispatcher)
            .catch { throw readFailure(it) }

    override fun observeSummaries(vaultId: String) = combine(dao.observeVaultHeads(vaultId), dao.observeConfirmedRows(vaultId)) { runs, confirmations ->
        val byPath = runs.groupBy { it.relPath }
        val confirmed = confirmations.associateBy { requireNotNull(it.relPath) }
        (byPath.keys + confirmed.keys).map { path ->
            val records = parseRecords(byPath[path].orEmpty())
            DiaryAnalysisSummary(SourceKey(vaultId, path), records.latestAttempt, records.latestCandidate,
                confirmed[path], records.candidateReadFailed)
        }
    }.flowOn(computationDispatcher).catch { throw readFailure(it) }

    override suspend fun readSummary(sourceKey: SourceKey): DiaryAnalysisSummary {
        val records = readParses(sourceKey)
        return DiaryAnalysisSummary(sourceKey, records.latestAttempt, records.latestCandidate,
            confirmed(sourceKey)?.diary, records.candidateReadFailed)
    }

    suspend fun readParses(sourceKey: SourceKey): DiaryParseRecords {
        val rows = persist { dao.runs(sourceKey.vaultId, sourceKey.relPath) }
        return withContext(computationDispatcher) { parseRecords(rows) }
    }

    override fun observeParses(sourceKey: SourceKey) =
        dao.observeRuns(sourceKey.vaultId, sourceKey.relPath).map(::parseRecords)
            .flowOn(computationDispatcher).catch { throw readFailure(it) }

    override suspend fun readCandidate(sourceKey: SourceKey, parseRunId: String): StoredDiaryCandidate? {
        val run = persist { dao.run(parseRunId) } ?: return null
        if (run.status != ParseRunStatus.SUCCEEDED || run.parseKey().sourceKey != sourceKey) return null
        return withContext(computationDispatcher) {
            StoredDiaryCandidate(DiaryParseAttempt(run.id, run.parseKey(), run.status, run.startedAt, run.finishedAt,
                run.failureCode), decodeCandidate(run))
        }
    }

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

    override suspend fun confirm(request: DiaryConfirmation): DiaryConfirmationResult = persist {
        database.withTransaction {
            if (request.editConfirmed) {
                val previous = dao.confirmed(request.sourceKey.vaultId, request.sourceKey.relPath)
                    ?: return@withTransaction DiaryConfirmationResult.Invalid(ConfirmationFailure.CONFIRMATION_CHANGED)
                if (previous.diary.confirmedAt != request.expectedConfirmedAt)
                    return@withTransaction DiaryConfirmationResult.Invalid(ConfirmationFailure.CONFIRMATION_CHANGED)
                val expected = request.expectedContentVersion
                if (expected == null || previous.diary.contentHash != expected.contentHash || previous.diary.hashVersion != expected.hashVersion)
                    return@withTransaction DiaryConfirmationResult.Invalid(ConfirmationFailure.SOURCE_CHANGED)
                val updated = previous.diary.copy(date = request.date.toString(),
                    confirmedAt = maxOf(now(), previous.diary.confirmedAt + 1), acceptedPartialResult = request.acceptedPartialResult,
                    kind = request.kind)
                replaceRecord(dao, updated, request.sessions, true)
                return@withTransaction DiaryConfirmationResult.Confirmed(checkNotNull(dao.record(updated.id)).ordered())
            }
            val run = dao.run(request.parseRunId)
            if (run == null || run.status != ParseRunStatus.SUCCEEDED)
                return@withTransaction DiaryConfirmationResult.Invalid(ConfirmationFailure.INVALID_PARSE_RUN)
            if (run.parseKey().sourceKey != request.sourceKey)
                return@withTransaction DiaryConfirmationResult.Invalid(ConfirmationFailure.SOURCE_MISMATCH)
            val sessions = request.sessions
            val previous = dao.confirmed(run.vaultId, run.relPath)
            if (previous?.diary?.confirmedAt != request.expectedConfirmedAt)
                return@withTransaction DiaryConfirmationResult.Invalid(ConfirmationFailure.CONFIRMATION_CHANGED)
            if (request.requireLatestCandidate && dao.latestCandidateId(run.vaultId, run.relPath) != run.id)
                return@withTransaction DiaryConfirmationResult.Invalid(ConfirmationFailure.CANDIDATE_CHANGED)
            val diary = TrainingRecord(previous?.diary?.id ?: newId(), run.vaultId, run.relPath,
                request.date.toString(), run.contentHash, run.hashVersion, run.id,
                maxOf(now(), (previous?.diary?.confirmedAt ?: -1L) + 1L), request.acceptedPartialResult,
                kind = request.kind)
            replaceRecord(dao, diary, sessions, previous != null)
            DiaryConfirmationResult.Confirmed(checkNotNull(dao.confirmed(run.vaultId, run.relPath)).ordered())
        }
    }

    private suspend fun <T> persist(block: suspend () -> T): T = try {
        block()
    } catch (e: Exception) {
        if (e is CancellationException || e is DiaryAnalysisStorageException) throw e
        throw DiaryAnalysisStorageException(e)
    }
}
