package com.example.fitlog.feature.diary.components

import com.example.fitlog.feature.diary.DiaryDetailActions
import com.example.fitlog.feature.diary.DiaryDetailUiState
import com.example.fitlog.feature.diary.DiaryReviewDraft
import com.example.fitlog.feature.diary.confirmationLabel
import com.example.fitlog.feature.diary.parseStatusLabel
import com.example.fitlog.feature.diary.reviewDate
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.fitlog.R
import com.example.fitlog.model.analysis.DiaryResultFreshness
import com.example.fitlog.model.analysis.ParseRunStatus
import com.example.fitlog.model.record.WeightBasis
import com.example.fitlog.model.record.WeightUnit
import com.example.fitlog.ui.components.FitLogLoadingIndicator
import com.example.fitlog.ui.components.FitLogProgressFeedback
import com.example.fitlog.ui.components.rememberDelayedLoading

@Composable
internal fun AnalysisStatusCard(state: DiaryDetailUiState, actions: DiaryDetailActions,
    showParsesLoading: Boolean, showConfirmationLoading: Boolean) {
    val showOperationLoading = rememberDelayedLoading(state.parsing || state.reviewSaving)
    val extractionIdle = !state.parsing && !state.analysisBusy && state.review == null && !state.reviewSaving
    val extractionColors = ButtonDefaults.filledTonalButtonColors()
    val hasExtraction = state.route.openCandidate || state.parses.latestCandidate != null ||
        state.parses.latestAttempt != null || state.confirmed != null || state.review != null
    val parseLabel = when {
        state.parsing -> R.string.ai_parsing
        showParsesLoading -> R.string.detail_status_loading
        state.parsesLoading && state.parses.latestAttempt == null -> null
        state.parsesReadFailed -> R.string.detail_status_unavailable
        state.parses.latestAttempt?.status == ParseRunStatus.FAILED -> R.string.detail_parse_failed
        state.candidateStatus == DiaryResultFreshness.NEEDS_UPDATE -> R.string.detail_status_stale
        else -> parseStatusLabel(state.parses.latestAttempt?.status)
    }
    val confirmLabel = when {
        state.reviewSaving -> R.string.detail_review_saving
        showConfirmationLoading -> R.string.detail_status_loading
        state.confirmationLoading && state.confirmed == null -> null
        state.confirmationReadFailed -> R.string.detail_status_unavailable
        else -> confirmationLabel(state.confirmationStatus)
    }
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer) {
        Box(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusRow(R.string.detail_status_parse, parseLabel, state.parses.latestAttempt?.status == ParseRunStatus.FAILED || state.parsesReadFailed)
                StatusRow(R.string.detail_status_confirmation, confirmLabel, state.confirmationReadFailed)
                FlowRow(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.parsing) TextButton(onClick = actions.cancelParse, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.ai_cancel_request)) }
                    FilledTonalButton(onClick = actions.parse,
                        enabled = extractionIdle && !state.sourceLoading,
                        colors = extractionColors,
                        shapes = ButtonDefaults.shapes()) {
                        Icon(painterResource(R.drawable.auto_awesome_24px), null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(when {
                            state.sourceLoading && extractionIdle -> R.string.detail_reading_source
                            hasExtraction -> R.string.ai_reparse
                            else -> R.string.ai_parse
                        }))
                    }
                }
            }
            FitLogProgressFeedback(showOperationLoading || showParsesLoading || showConfirmationLoading,
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 4.dp))
        }
    }
}

@Composable
private fun StatusRow(label: Int, value: Int?, error: Boolean) {
    Surface(color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.medium) {
        Row(Modifier.fillMaxWidth().padding(if (error) 8.dp else 0.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(label), style = MaterialTheme.typography.labelLarge)
            if (error) Icon(painterResource(R.drawable.warning_24px), null, Modifier.size(20.dp))
            Text(value?.let { stringResource(it) }.orEmpty(), style = MaterialTheme.typography.bodyMediumEmphasized, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
internal fun ReviewConfirmationDialog(draft: DiaryReviewDraft, saving: Boolean, message: Int?, onDismiss: () -> Unit,
    onSave: (String, Boolean) -> Unit) {
    var date by androidx.compose.runtime.saveable.rememberSaveable(draft.parseRunId) { mutableStateOf(draft.date) }
    var partial by androidx.compose.runtime.saveable.rememberSaveable(draft.parseRunId) { mutableStateOf(draft.acceptedPartial) }
    AlertDialog(onDismissRequest = { if (!saving) onDismiss() }, title = { Text(stringResource(R.string.detail_confirm_save)) },
        text = { Box {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.detail_confirm_explanation))
                if (draft.sessions.all { it.exercises.isEmpty() }) Text(stringResource(R.string.detail_review_rest_day))
                if (draft.sessions.any { session -> session.exercises.any { exercise -> exercise.sets.any { set ->
                    set.basis == null || set.basis == WeightBasis.UNKNOWN || set.reps == null ||
                        (set.basis != WeightBasis.BODYWEIGHT && (set.weight == null || set.unit == null || set.unit == WeightUnit.UNKNOWN))
                } } }) Text(stringResource(R.string.detail_confirm_unknown_help), style = MaterialTheme.typography.bodySmall)
                if (draft.expectedConfirmedAt != null) Text(stringResource(R.string.detail_confirm_replace))
                OutlinedTextField(date, { date = it }, singleLine = true, enabled = !saving,
                    label = { Text(stringResource(R.string.detail_review_date)) },
                    supportingText = { Text(stringResource(R.string.detail_review_date_format)) },
                    isError = reviewDate(date) == null)
                if (draft.partial) Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(partial, { partial = it }, enabled = !saving)
                    Text(stringResource(R.string.detail_accept_partial), style = MaterialTheme.typography.bodyMedium)
                }
                message?.takeIf { it != R.string.detail_review_saved }?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
            }
            FitLogLoadingIndicator(saving, Modifier.align(Alignment.BottomCenter).fillMaxWidth())
        } },
        confirmButton = { TextButton(onClick = { onSave(date, partial) }, enabled = !saving && reviewDate(date) != null && (!draft.partial || partial), shapes = ButtonDefaults.shapes()) {
            Text(stringResource(R.string.detail_confirm_save))
        } }, dismissButton = { TextButton(onClick = onDismiss, enabled = !saving, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.record_cancel)) } })
}
