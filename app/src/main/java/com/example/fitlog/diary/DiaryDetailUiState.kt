package com.example.fitlog.diary

import com.example.fitlog.data.analysis.*
import com.example.fitlog.domain.analysis.confirmationFreshness
import com.example.fitlog.domain.analysis.resultFreshness
import com.example.fitlog.data.vault.MarkdownSnapshot
import com.example.fitlog.navigation.FitLogRoute

internal enum class DiaryLeaveTarget { BACK, AI_SETTINGS }

internal data class DiaryDetailUiState(
    val route: FitLogRoute.DiaryDetail,
    val contentVersion: DiaryContentVersion? = null,
    val reviewAvailable: Boolean = false,
    val tab: DiaryDetailTab = DiaryDetailTab.ORIGINAL,
    val original: MarkdownSnapshot? = null,
    val sourceLoading: Boolean = false,
    val sourceReadFailed: Boolean = false,
    val canEdit: Boolean = true,
    val parses: DiaryParseRecords = DiaryParseRecords(),
    val parsesLoading: Boolean = false,
    val parsesReadFailed: Boolean = false,
    val confirmed: TrainingRecordDetails? = null,
    val confirmationLoading: Boolean = false,
    val confirmationReadFailed: Boolean = false,
    val confirmationStatus: ConfirmationFreshness = ConfirmationFreshness.UNCONFIRMED,
    val candidateStatus: DiaryResultFreshness? = null,
    val parsing: Boolean = false,
    val analysis: DiaryAnalysisRun = DiaryAnalysisRun(),
    val parseMessage: Int? = null,
    val showCandidate: Boolean = false,
    val review: DiaryReviewDraft? = null,
    val reviewSaving: Boolean = false,
    val reviewMessage: Int? = null,
    val canReview: Boolean = false,
    val leaveRequested: Boolean = false,
    val leaveTarget: DiaryLeaveTarget? = null,
    val leaveReady: Boolean = false,
) {
    val analysisBusy: Boolean get() = analysis.active
    val analysisIndexWarning: Boolean get() = analysis.indexWarning
}

internal fun DiaryDetailUiState.withDerivedValues() = copy(
    canReview = reviewAvailable && !reviewSaving && !parsing && !confirmationLoading && !parsesLoading && !confirmationReadFailed &&
        (review != null || ((showCandidate || confirmed == null) && parses.latestCandidate != null) || (!showCandidate && confirmed != null)),
    confirmationStatus = confirmationFreshness(confirmed?.diary, contentVersion),
    candidateStatus = parses.latestCandidate?.let { resultFreshness(DiaryContentVersion(it.analysis.parseKey.contentHash, it.analysis.parseKey.hashVersion), contentVersion) },
)
