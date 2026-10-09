package com.example.fitlog.data.analysis

import com.example.fitlog.data.hash.ContentTextSnapshot
import com.example.fitlog.data.vault.MarkdownDocuments
import com.example.fitlog.data.vault.MarkdownSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Source verification belongs to the use case; confirmation/version conflicts belong to the Room transaction. */
internal suspend fun confirmCurrentDiary(
    request: DiaryConfirmation,
    documentUri: String,
    documents: MarkdownDocuments,
    repository: DiaryAnalysisRepository,
    recordRead: suspend (MarkdownSnapshot) -> Unit = {},
): DiaryConfirmationResult {
    val candidate = repository.readCandidate(request.sourceKey, request.parseRunId)
        ?: return DiaryConfirmationResult.Invalid(ConfirmationFailure.INVALID_PARSE_RUN)
    val snapshot = try { documents.read(documentUri) } catch (e: Exception) {
        if (e is CancellationException) throw e
        return DiaryConfirmationResult.Invalid(ConfirmationFailure.SOURCE_UNAVAILABLE)
    }
    val version = withContext(Dispatchers.Default) {
        val value = ContentTextSnapshot.fromRawText((if (snapshot.bom) "\uFEFF" else "") + snapshot.text)
        DiaryContentVersion(value.hash, value.hashVersion)
    }
    val key = candidate.analysis.parseKey
    if (resultFreshness(DiaryContentVersion(key.contentHash, key.hashVersion), version) != DiaryResultFreshness.CURRENT) {
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
    val result = repository.confirm(request)
    // Index work follows the transaction so a concurrent scan cannot delay source verification.
    recordRead(snapshot)
    return result
}
