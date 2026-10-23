package com.example.fitlog.data.analysis

import kotlinx.coroutines.flow.Flow

interface DiaryVaultAnalysisReader {
    fun observeSummaries(vaultId: String): Flow<List<DiaryAnalysisSummary>>
}

data class DiaryAnalysisSummary(
    val sourceKey: SourceKey,
    val latestAttempt: DiaryParseAttempt? = null,
    val candidate: StoredDiaryCandidate? = null,
    val confirmed: TrainingRecord? = null,
    val candidateReadFailed: Boolean = false,
)

enum class BatchReviewEligibility { READY, WITH_NOTICES, BLOCKED, ALREADY_CONFIRMED }
enum class BatchReviewBlock { SOURCE, VERSION, STALE, DATE, PARTIAL, ATTEMPT, DAMAGED }

data class BatchReviewAssessment(
    val eligibility: BatchReviewEligibility,
    val date: java.time.LocalDate? = null,
    val block: BatchReviewBlock? = null,
)
