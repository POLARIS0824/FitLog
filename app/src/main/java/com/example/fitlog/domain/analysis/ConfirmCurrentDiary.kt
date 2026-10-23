package com.example.fitlog.domain.analysis

import com.example.fitlog.data.analysis.DiaryConfirmations
import com.example.fitlog.data.hash.ContentTextSnapshot
import com.example.fitlog.data.vault.MarkdownDocuments
import com.example.fitlog.data.vault.MarkdownSnapshot
import com.example.fitlog.domain.record.validateRecordValues
import com.example.fitlog.model.analysis.BatchReviewEligibility
import com.example.fitlog.model.analysis.ConfirmationFailure
import com.example.fitlog.model.analysis.DiaryConfirmation
import com.example.fitlog.model.analysis.DiaryConfirmationResult
import com.example.fitlog.model.analysis.DiaryContentVersion
import com.example.fitlog.model.analysis.DiaryResultFreshness
import com.example.fitlog.model.record.RecordKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Source verification belongs to the use case; confirmation/version conflicts belong to the Room transaction. */
internal suspend fun confirmCurrentDiary(
    request: DiaryConfirmation,
    documentUri: String,
    documents: MarkdownDocuments,
    repository: DiaryConfirmations,
    computationDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.Default,
    recordRead: suspend (MarkdownSnapshot) -> Unit = {},
): DiaryConfirmationResult {
    val candidate = if (request.editConfirmed) null else {
        repository.readCandidate(request.sourceKey, request.parseRunId)
            ?: return DiaryConfirmationResult.Invalid(ConfirmationFailure.INVALID_PARSE_RUN)
    }
    if (candidate != null && requiresPartialAcceptance(candidate.analysis) && !request.acceptedPartialResult)
        return DiaryConfirmationResult.Invalid(ConfirmationFailure.PARTIAL_RESULT_NOT_ACCEPTED)
    val normalized = try { withContext(computationDispatcher) {
        if (request.editConfirmed) {
            require(request.date.year in 1..9999)
            validateRecordValues(request.sessions)
            request
        } else request.copy(sessions = normalizeReview(request, requireNotNull(candidate).analysis))
    } } catch (_: IllegalArgumentException) {
        return DiaryConfirmationResult.Invalid(ConfirmationFailure.INVALID_DATA)
    }
    val expected = if (request.editConfirmed) request.expectedContentVersion else {
        val key = requireNotNull(candidate).analysis.parseKey
        DiaryContentVersion(key.contentHash, key.hashVersion)
    }
    if (expected == null) return DiaryConfirmationResult.Invalid(ConfirmationFailure.INVALID_DATA)
    val snapshot = try { documents.read(documentUri) } catch (e: Exception) {
        if (e is CancellationException) throw e
        return DiaryConfirmationResult.Invalid(ConfirmationFailure.SOURCE_UNAVAILABLE)
    }
    val version = withContext(computationDispatcher) {
        val value = ContentTextSnapshot.fromRawText((if (snapshot.bom) "\uFEFF" else "") + snapshot.text)
        DiaryContentVersion(value.hash, value.hashVersion)
    }
    if (resultFreshness(expected, version) != DiaryResultFreshness.CURRENT) {
        recordRead(snapshot)
        return DiaryConfirmationResult.Invalid(ConfirmationFailure.SOURCE_CHANGED)
    }
    if (request.requireLatestCandidate) {
        val summary = repository.readSummary(request.sourceKey)
        if (summary.candidate?.attempt?.id != request.parseRunId)
            return DiaryConfirmationResult.Invalid(ConfirmationFailure.CANDIDATE_CHANGED)
        val assessment = assessBatchReview(summary, version, available = true)
        if (assessment.eligibility !in setOf(BatchReviewEligibility.READY, BatchReviewEligibility.WITH_NOTICES) || assessment.date != request.date)
            return DiaryConfirmationResult.Invalid(ConfirmationFailure.INVALID_DATA)
    }
    val kind = if (normalized.sessions.all { it.exercises.isEmpty() }) RecordKind.REST else RecordKind.TRAINING
    val result = repository.confirm(normalized.copy(kind = kind))
    // Index work follows the transaction so a concurrent scan cannot delay source verification.
    recordRead(snapshot)
    return result
}
