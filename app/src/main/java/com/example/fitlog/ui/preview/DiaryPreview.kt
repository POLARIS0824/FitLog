package com.example.fitlog.ui.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.fitlog.feature.diary.DiaryDetailActions
import com.example.fitlog.feature.diary.DiaryDetailContent
import com.example.fitlog.feature.diary.DiaryDetailTab
import com.example.fitlog.feature.diary.DiaryDetailUiState
import com.example.fitlog.feature.diary.DiarySetAddress
import com.example.fitlog.feature.diary.DiarySetEdit
import com.example.fitlog.feature.diary.DiarySetField
import com.example.fitlog.feature.diary.DiarySetPickerContent
import com.example.fitlog.feature.diary.navigation.DiaryDetailNavKey
import com.example.fitlog.model.analysis.DiaryParseRecords
import com.example.fitlog.model.analysis.DiaryResultFreshness
import com.example.fitlog.model.record.WeightBasis
import com.example.fitlog.model.record.WeightUnit

@Preview(locale = "en", widthDp = 412, heightDp = 892, showBackground = true)
@Composable
private fun DiaryOriginalPreview() {
    val original = PreviewDiary.snapshot()
    FitLogPreview { DiaryDetailContent(DiaryDetailUiState(previewDiaryRoute, original = original), DiaryDetailActions(), {}, {}) }
}

@Preview(locale = "en", widthDp = 412, heightDp = 892, showBackground = true)
@Composable
private fun DiaryAnalysisPreview() {
    val candidate = PreviewDiary.candidate()
    FitLogPreview {
        DiaryDetailContent(DiaryDetailUiState(previewDiaryRoute, tab = DiaryDetailTab.ANALYSIS,
            parses = DiaryParseRecords(listOf(candidate.attempt), candidate), candidateStatus = DiaryResultFreshness.CURRENT, canReview = true),
            DiaryDetailActions(), {}, {})
    }
}

@Preview(locale = "en", widthDp = 412, heightDp = 892, showBackground = true)
@Composable
private fun DiaryWeightPickerReviewPreview() {
    FitLogPreview {
        DiarySetPickerContent(DiarySetEdit(DiarySetAddress(0, 0, 0), 1, DiarySetField.WEIGHT,
            weight = 60.0, unit = WeightUnit.KG, reps = 8, basis = WeightBasis.TOTAL),
            onDismiss = {}, onWeight = {}, onReps = {})
    }
}

@Preview(locale = "en", widthDp = 412, heightDp = 892, showBackground = true)
@Composable
private fun DiaryRepsPickerReviewPreview() {
    FitLogPreview {
        DiarySetPickerContent(DiarySetEdit(DiarySetAddress(0, 0, 0), 1, DiarySetField.REPS,
            weight = 60.0, unit = WeightUnit.KG, reps = 8, basis = WeightBasis.TOTAL),
            onDismiss = {}, onWeight = {}, onReps = {})
    }
}

private val previewDiaryRoute = DiaryDetailNavKey(PreviewDiary.VAULT_URI, PreviewDiary.VAULT_ID,
    PreviewDiary.DOCUMENT, PreviewDiary.PATH, PreviewDiary.VAULT_URI, PreviewDiary.FILE_NAME)
