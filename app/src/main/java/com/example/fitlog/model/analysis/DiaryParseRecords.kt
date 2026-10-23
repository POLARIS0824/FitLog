package com.example.fitlog.model.analysis



enum class ParseRunStatus { SUCCEEDED, FAILED }

/** Storage JSON and raw model responses deliberately stay behind the repository. */
data class DiaryParseAttempt(
    val id: String,
    val parseKey: DiaryParseKey,
    val status: ParseRunStatus,
    val startedAt: Long,
    val finishedAt: Long,
    val failureCode: String?,
)

data class StoredDiaryCandidate(val attempt: DiaryParseAttempt, val analysis: DiaryAnalysis)

data class DiaryParseRecords(
    val attempts: List<DiaryParseAttempt> = emptyList(),
    val latestCandidate: StoredDiaryCandidate? = null,
    val candidateReadFailed: Boolean = false,
) {
    val latestAttempt: DiaryParseAttempt? get() = attempts.lastOrNull()
    val latestFailure: DiaryParseAttempt? get() = attempts.lastOrNull {
        it.status == ParseRunStatus.FAILED
    }
}

enum class DiaryResultFreshness { CURRENT, NEEDS_UPDATE, UNVERIFIABLE }
