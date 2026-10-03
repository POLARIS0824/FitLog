package com.example.fitlog.data.analysis

import kotlinx.coroutines.flow.Flow

/** Read-only business boundary. Opening a diary never starts extraction. */
interface DiaryAnalysisReader {
    fun observeParses(sourceKey: SourceKey): Flow<DiaryParseRecords>
    fun observeConfirmed(sourceKey: SourceKey): Flow<ConfirmedDiaryRecord?>
}

/** Storage JSON and raw model responses deliberately stay behind the repository. */
data class DiaryParseAttempt(
    val id: String,
    val parseKey: DiaryParseKey,
    val status: ParseRunStatus,
    val startedAt: Long,
    val finishedAt: Long?,
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
        it.status == ParseRunStatus.FAILED || it.status == ParseRunStatus.INTERRUPTED
    }
}

enum class DiaryResultFreshness { CURRENT, NEEDS_UPDATE, UNVERIFIABLE }

fun resultFreshness(version: DiaryContentVersion, current: DiaryContentVersion?): DiaryResultFreshness = when {
    current == null || current.hashVersion != version.hashVersion -> DiaryResultFreshness.UNVERIFIABLE
    current.contentHash == version.contentHash -> DiaryResultFreshness.CURRENT
    else -> DiaryResultFreshness.NEEDS_UPDATE
}
