package com.example.fitlog.domain.analysis

import com.example.fitlog.model.analysis.BatchReviewAssessment
import com.example.fitlog.model.analysis.BatchReviewBlock
import com.example.fitlog.model.analysis.BatchReviewEligibility
import com.example.fitlog.model.analysis.CandidateOrigin
import com.example.fitlog.model.analysis.ConfirmationFreshness
import com.example.fitlog.model.analysis.DiaryAnalysis
import com.example.fitlog.model.analysis.DiaryAnalysisSummary
import com.example.fitlog.model.analysis.DiaryContentVersion
import com.example.fitlog.model.analysis.DiaryResultFreshness
import com.example.fitlog.model.analysis.ParseRunStatus
import com.example.fitlog.model.analysis.ValidationCode
import com.example.fitlog.model.record.TrainingRecord
import com.example.fitlog.model.record.WeightBasis

/**
 * Rules that decide whether a stored candidate/confirmation is still current and whether it can be
 * confirmed. They depend only on data models, so they stay out of Room and out of feature packages.
 */

/** Caller must supply a normalized content version, never an index byte fingerprint. */
fun resultFreshness(version: DiaryContentVersion, current: DiaryContentVersion?): DiaryResultFreshness = when {
    current == null || current.hashVersion != version.hashVersion -> DiaryResultFreshness.UNVERIFIABLE
    current.contentHash == version.contentHash -> DiaryResultFreshness.CURRENT
    else -> DiaryResultFreshness.NEEDS_UPDATE
}

fun confirmationFreshness(confirmed: TrainingRecord?, current: DiaryContentVersion?): ConfirmationFreshness = when {
    confirmed == null -> ConfirmationFreshness.UNCONFIRMED
    current == null || current.hashVersion != confirmed.hashVersion -> ConfirmationFreshness.UNVERIFIABLE
    current.contentHash == confirmed.contentHash -> ConfirmationFreshness.CONFIRMED
    else -> ConfirmationFreshness.NEEDS_UPDATE
}

fun requiresPartialAcceptance(analysis: DiaryAnalysis): Boolean = analysis.hasErrors ||
    analysis.sessions.any { session ->
        session.exercises.any { exercise ->
            exercise.candidate.groups.any { it.count == null && it.repsList == null }
        }
    }

/** The validator permits unknown values; these review notices must not be mistaken for complete data. */
fun batchReviewNotices(analysis: DiaryAnalysis): Set<ValidationCode> = buildSet {
    addAll(analysis.issues.map { it.code })
    analysis.sessions.flatMap { it.exercises }.forEach { exercise ->
        if (exercise.evidence.quote.isBlank()) add(ValidationCode.EVIDENCE_NOT_FOUND)
        if (exercise.candidate.groups.isEmpty()) add(ValidationCode.MISSING_COUNT)
        if (exercise.candidate.groups.any { it.inferredFields.isNotEmpty() }) add(ValidationCode.INFERRED_VALUE)
        exercise.sets.forEach { set ->
            if (set.weight.value == null && set.basis.value != WeightBasis.BODYWEIGHT) add(ValidationCode.MISSING_WEIGHT)
            if (set.unit.value == null && set.basis.value != WeightBasis.BODYWEIGHT) add(ValidationCode.MISSING_UNIT)
            if (set.basis.value == null) add(ValidationCode.MISSING_BASIS)
            if (set.reps.value == null) add(ValidationCode.MISSING_REPS)
            if (set.countOrigin == CandidateOrigin.INFERRED ||
                listOf(set.weight, set.unit, set.basis, set.reps).any { it.inferred }
            ) add(ValidationCode.INFERRED_VALUE)
        }
    }
}

fun assessBatchReview(
    summary: DiaryAnalysisSummary,
    version: DiaryContentVersion?,
    available: Boolean,
): BatchReviewAssessment {
    val date = summary.candidate?.analysis?.let { suggestDiaryDate(summary.sourceKey, it) }
    if (summary.confirmed != null) return BatchReviewAssessment(BatchReviewEligibility.ALREADY_CONFIRMED, date?.suggested)
    if (!available) return BatchReviewAssessment(BatchReviewEligibility.BLOCKED, date?.suggested, BatchReviewBlock.SOURCE)
    val candidate = summary.candidate
        ?: return BatchReviewAssessment(BatchReviewEligibility.BLOCKED, block = BatchReviewBlock.DAMAGED)
    val key = candidate.analysis.parseKey
    when (resultFreshness(DiaryContentVersion(key.contentHash, key.hashVersion), version)) {
        DiaryResultFreshness.UNVERIFIABLE -> return BatchReviewAssessment(
            BatchReviewEligibility.BLOCKED, date?.suggested, BatchReviewBlock.VERSION,
        )
        DiaryResultFreshness.NEEDS_UPDATE -> return BatchReviewAssessment(
            BatchReviewEligibility.BLOCKED, date?.suggested, BatchReviewBlock.STALE,
        )
        DiaryResultFreshness.CURRENT -> Unit
    }
    if (summary.latestAttempt?.status == ParseRunStatus.FAILED) {
        return BatchReviewAssessment(BatchReviewEligibility.BLOCKED, date?.suggested, BatchReviewBlock.ATTEMPT)
    }
    val analysis = candidate.analysis
    if (date?.suggested == null || date.requiresReview || analysis.issues.any { it.code == ValidationCode.DATE_CONFLICT }) {
        return BatchReviewAssessment(BatchReviewEligibility.BLOCKED, date?.suggested, BatchReviewBlock.DATE)
    }
    if (requiresPartialAcceptance(analysis)) {
        return BatchReviewAssessment(BatchReviewEligibility.BLOCKED, date.suggested, BatchReviewBlock.PARTIAL)
    }
    val notices = batchReviewNotices(analysis).isNotEmpty() || analysis.modelIssues.isNotEmpty() ||
        analysis.sessions.all { it.exercises.isEmpty() }
    return BatchReviewAssessment(
        if (notices) BatchReviewEligibility.WITH_NOTICES else BatchReviewEligibility.READY,
        date.suggested,
    )
}

fun needsDiaryAnalysis(summary: DiaryAnalysisSummary?, version: DiaryContentVersion?): Boolean {
    if (summary?.latestAttempt?.status == ParseRunStatus.FAILED) return true
    val key = summary?.candidate?.analysis?.parseKey ?: return true
    return resultFreshness(DiaryContentVersion(key.contentHash, key.hashVersion), version) != DiaryResultFreshness.CURRENT
}
