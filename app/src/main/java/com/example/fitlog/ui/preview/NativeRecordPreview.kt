package com.example.fitlog.ui.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.example.fitlog.R
import com.example.fitlog.feature.record.NativeRecordActions
import com.example.fitlog.feature.record.NativeRecordContent
import com.example.fitlog.feature.record.NativeRecordInput
import com.example.fitlog.feature.record.NativeRecordUiState
import com.example.fitlog.feature.record.RecordSaveState
import com.example.fitlog.model.record.ReviewedExercise
import com.example.fitlog.model.record.ReviewedSet
import com.example.fitlog.model.record.WeightBasis
import com.example.fitlog.model.record.WeightUnit

@Preview(locale = "en", widthDp = 412, heightDp = 892, showBackground = true)
@Composable
private fun NativeRecordPreview() {
    val input = NativeRecordInput(PreviewDiary.DATE, listOf(
        ReviewedExercise(stringResource(R.string.preview_exercise_name), listOf(
            ReviewedSet(weight = 60.0, unit = WeightUnit.KG, basis = WeightBasis.TOTAL, reps = 8),
            ReviewedSet(weight = 60.0, unit = WeightUnit.KG, basis = WeightBasis.TOTAL, reps = 8),
        )),
    ))
    FitLogPreview {
        NativeRecordContent(
            NativeRecordUiState(input = input, loading = false, state = RecordSaveState.SAVED, persisted = input),
            NativeRecordActions(
                requestLeave = {}, retry = {}, rest = {}, name = { _, _ -> }, removeSet = { _, _ -> },
                addSet = {}, removeExercise = {}, exerciseNotes = { _, _ -> }, addExercise = {}, notes = {},
                set = { _, _, _ -> }, date = {}, delete = {}, cancelLeave = {}, retryAndLeave = {},
                discardAndLeave = {}, copySet = { _, _ -> }, undoRemoval = {}, dismissUndo = {},
            ),
        )
    }
}
