package com.example.fitlog.model.analysis

import com.example.fitlog.model.record.TrainingRecord
import com.example.fitlog.model.source.SourceKey

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
