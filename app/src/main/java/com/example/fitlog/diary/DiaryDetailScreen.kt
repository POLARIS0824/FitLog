package com.example.fitlog.diary

import com.example.fitlog.ui.components.FitLogWavyProgressIndicator

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.fitlog.R
import com.example.fitlog.data.analysis.*
import com.example.fitlog.data.vault.MarkdownSnapshot
import com.example.fitlog.navigation.FitLogRoute
import com.example.fitlog.ui.components.FitLogNotice
import com.example.fitlog.ui.preview.*
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.delay

@Composable
fun DiaryDetailScreen(vm: DiaryDetailViewModel, onEdit: () -> Unit, onBack: () -> Unit, onAiSettings: () -> Unit) {
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }
    DiaryDetailContent(
        DiaryDetailUiState(route = vm.route, tab = vm.tab, original = vm.original, sourceLoading = vm.sourceLoading,
            sourceReadFailed = vm.sourceReadFailed, canEdit = vm.canEdit, parses = vm.parses, parsesLoading = vm.parsesLoading,
            parsesReadFailed = vm.parsesReadFailed, confirmed = vm.confirmed, confirmationLoading = vm.confirmationLoading,
            confirmationReadFailed = vm.confirmationReadFailed, confirmationStatus = vm.confirmationStatus,
            candidateStatus = vm.candidateStatus, parsing = vm.parsing, parseMessage = vm.parseMessage,
            showCandidate = vm.showCandidate, review = vm.review, reviewSaving = vm.reviewSaving,
            reviewMessage = vm.reviewMessage, canReview = vm.canReview, leaveRequested = vm.leaveRequested),
        DiaryDetailActions(refresh = vm::refresh, parse = vm::parse, cancelParse = vm::cancelParse, retryAnalysis = vm::retryAnalysis,
            selectTab = vm::selectTab, selectResult = vm::selectResult,
            updateWeight = { address, value -> vm.updateWeight(address, value.weight, value.unit, value.converted, value.basis) }, updateReps = vm::updateReps,
            beginReview = vm::beginReview, cancelConfirmation = vm::cancelReviewConfirmation, saveReview = vm::saveReview,
            cancelLeave = vm::cancelLeave, discardAndLeave = vm::discardAndLeave),
        onEdit, onBack, onAiSettings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DiaryDetailContent(state: DiaryDetailUiState, actions: DiaryDetailActions,
    onEdit: () -> Unit, onBack: () -> Unit, onAiSettings: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    var fileInfo by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf<DiarySetEdit?>(null) }
    var confirming by remember { mutableStateOf(false) }
    val showSourceLoading = delayedReadFeedback(state.sourceLoading)
    val showParsesLoading = delayedReadFeedback(state.parsesLoading)
    val showConfirmationLoading = delayedReadFeedback(state.confirmationLoading)
    LaunchedEffect(state.review) { if (state.review == null) confirming = false }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 840.dp).fillMaxSize()) {
            TopAppBar(
                title = { Text(diaryDateFromFileName(state.route.fileName)?.toString() ?: state.route.fileName,
                    style = MaterialTheme.typography.titleLargeEmphasized) },
                navigationIcon = { IconButton(onClick = onBack, enabled = !state.reviewSaving) {
                    Icon(painterResource(R.drawable.arrow_back_24px), stringResource(R.string.cd_back))
                } },
                actions = {
                    if (state.original != null || !state.sourceLoading) {
                        IconButton(onClick = onEdit, enabled = state.canEdit && !state.reviewSaving) {
                            Icon(painterResource(R.drawable.edit_24px), stringResource(R.string.detail_edit))
                        }
                    } else {
                        // Reserve the action slot without flashing a disabled icon during the initial read.
                        Spacer(Modifier.size(48.dp))
                    }
                    Box {
                        IconButton(onClick = { menu = true }, enabled = !state.reviewSaving) {
                            Icon(painterResource(R.drawable.more_vert_24px), stringResource(R.string.log_options))
                        }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.log_refresh)) },
                                enabled = !state.sourceLoading, onClick = { menu = false; actions.refresh() })
                            DropdownMenuItem(text = { Text(stringResource(R.string.ai_settings_title)) },
                                onClick = { menu = false; onAiSettings() })
                            DropdownMenuItem(text = { Text(stringResource(R.string.detail_file_info)) }, onClick = { menu = false; fileInfo = true })
                        }
                    }
                }, windowInsets = WindowInsets(0))
            PrimaryTabRow(selectedTabIndex = state.tab.ordinal) {
                DiaryDetailTab.entries.forEach { tab ->
                    Tab(selected = state.tab == tab, onClick = { actions.selectTab(tab) }, text = {
                        Text(stringResource(if (tab == DiaryDetailTab.ORIGINAL) R.string.detail_original else R.string.detail_analysis))
                    })
                }
            }
            Box(Modifier.weight(1f)) {
                if (state.tab == DiaryDetailTab.ORIGINAL) {
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (state.sourceReadFailed) item { FitLogNotice(stringResource(R.string.detail_source_failed), error = true) }
                        state.original?.let { snapshot ->
                            if (!snapshot.file.writable) item { Text(stringResource(R.string.detail_read_only)) }
                            item { if (snapshot.text.isEmpty()) Text(stringResource(R.string.detail_original_empty))
                                else SelectionContainer { Text(snapshot.text, fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.bodyLarge) } }
                        }
                    }
                } else AnalysisContent(state, actions, Modifier.fillMaxSize(), showParsesLoading, showConfirmationLoading,
                    onEdit = { edit = it }, onConfirm = { if (actions.beginReview()) confirming = true })
                // Loading must not change the viewport height during entry or refresh.
                if (showSourceLoading) FitLogWavyProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
            }
        }
    }
    edit?.let { target ->
        DiarySetPicker(target, onDismiss = { edit = null },
            onWeight = { actions.updateWeight(target.address, it); edit = null },
            onReps = { actions.updateReps(target.address, it); edit = null })
    }
    if (fileInfo) AlertDialog(onDismissRequest = { fileInfo = false }, title = { Text(stringResource(R.string.detail_file_info)) },
        text = { SelectionContainer { Text(state.route.relPath) } },
        confirmButton = { TextButton(onClick = { fileInfo = false }) { Text(stringResource(R.string.detail_picker_done)) } })
    if (confirming) state.review?.let { draft ->
        ReviewConfirmationDialog(draft, state.reviewSaving, state.reviewMessage,
            onDismiss = { actions.cancelConfirmation(); confirming = false }, onSave = actions.saveReview)
    }
    if (state.leaveRequested) AlertDialog(onDismissRequest = actions.cancelLeave,
        title = { Text(stringResource(R.string.detail_unsaved_title)) },
        text = { Text(stringResource(R.string.detail_unsaved_message)) },
        confirmButton = { TextButton(onClick = actions.cancelLeave) { Text(stringResource(R.string.detail_continue_review)) } },
        dismissButton = { TextButton(onClick = actions.discardAndLeave) { Text(stringResource(R.string.detail_discard_review)) } })
}

/** Only delay feedback: completed reads become visible immediately and cancel the pending hint. */
@Composable
private fun delayedReadFeedback(loading: Boolean): Boolean {
    var visible by remember(loading) { mutableStateOf(false) }
    LaunchedEffect(loading) {
        if (loading) {
            delay(300L)
            visible = true
        }
    }
    return loading && visible
}

@Composable
private fun AnalysisContent(state: DiaryDetailUiState, actions: DiaryDetailActions, modifier: Modifier,
    showParsesLoading: Boolean, showConfirmationLoading: Boolean,
    onEdit: (DiarySetEdit) -> Unit, onConfirm: () -> Unit) {
    val candidateSelected = state.showCandidate || state.confirmed == null
    LazyColumn(modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item(key = "status") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AnalysisStatusCard(state, actions, showParsesLoading, showConfirmationLoading)
                if (state.sourceReadFailed) FitLogNotice(stringResource(R.string.detail_source_failed), error = true)
                if (state.parsesReadFailed || state.confirmationReadFailed || state.parses.candidateReadFailed) {
                    FitLogNotice(stringResource(if (state.parses.candidateReadFailed) R.string.detail_candidate_damaged else R.string.detail_analysis_failed), error = true)
                    TextButton(onClick = actions.retryAnalysis, enabled = !state.parsesLoading && !state.confirmationLoading) {
                        Text(stringResource(R.string.detail_retry_analysis))
                    }
                }
                state.parseMessage?.let { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium) }
                state.reviewMessage?.let { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium,
                    color = if (it == R.string.detail_review_saved) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error) }
                if (state.confirmed != null && state.parses.latestCandidate != null) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !candidateSelected, onClick = { actions.selectResult(false) },
                            enabled = state.review == null && !state.reviewSaving, label = { Text(stringResource(R.string.detail_confirmed_result)) })
                        FilterChip(selected = candidateSelected, onClick = { actions.selectResult(true) },
                            enabled = state.review == null && !state.reviewSaving, label = { Text(stringResource(R.string.detail_candidate_result)) })
                    }
                }
            }
        }
        state.parses.latestFailure?.let { failure ->
            item(key = "failure") {
                FitLogNotice(stringResource(R.string.detail_failure_summary,
                    DateFormat.getDateTimeInstance().format(Date(failure.finishedAt)), stringResource(failureLabel(failure))), error = true)
            }
        }
        if (state.review != null) {
            reviewItems(state.review, state.parses.latestCandidate?.takeIf { it.attempt.id == state.review.parseRunId && state.review.fromCandidate }, state.canReview, onEdit,
                freshness = state.candidateStatus, usedForConfirmation = state.confirmed?.diary?.parseRunId == state.review.parseRunId)
        } else if (candidateSelected) state.parses.latestCandidate?.let { candidate ->
            candidateItems(candidate, state.candidateStatus, state.confirmed?.diary?.parseRunId == candidate.attempt.id,
                canEdit = state.canReview, onEdit = onEdit)
        } else confirmedItems(requireNotNull(state.confirmed), canEdit = state.canReview, onEdit = onEdit)
        if (!state.parsesLoading && !state.confirmationLoading && state.parses.latestCandidate == null && state.confirmed == null) {
            item(key = "empty") { Text(stringResource(R.string.detail_no_saved_result)) }
        }
        if (state.canReview || state.reviewSaving) {
            item(key = "review-action") {
                Button(onClick = onConfirm, enabled = state.canReview, modifier = Modifier.fillMaxWidth(),
                    shapes = ButtonDefaults.shapes()) {
                    Icon(painterResource(R.drawable.check_24px), null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(if (state.review == null) R.string.detail_confirm_save else R.string.detail_save_corrections))
                }
            }
        }
    }
}

@Composable
private fun AnalysisStatusCard(state: DiaryDetailUiState, actions: DiaryDetailActions,
    showParsesLoading: Boolean, showConfirmationLoading: Boolean) {
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
                    if (state.parsing) TextButton(onClick = actions.cancelParse) { Text(stringResource(R.string.ai_cancel_request)) }
                    FilledTonalButton(onClick = actions.parse,
                        enabled = !state.parsing && !state.sourceLoading && state.review == null && !state.reviewSaving,
                        shapes = ButtonDefaults.shapes()) {
                        Icon(painterResource(R.drawable.auto_awesome_24px), null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(if (state.parses.latestCandidate == null) R.string.ai_parse else R.string.ai_reparse))
                    }
                }
            }
            if (state.parsing || showParsesLoading || showConfirmationLoading || state.reviewSaving) {
                FitLogWavyProgressIndicator(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 4.dp))
            }
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
private fun ReviewConfirmationDialog(draft: DiaryReviewDraft, saving: Boolean, message: Int?, onDismiss: () -> Unit,
    onSave: (String, Boolean) -> Unit) {
    var date by remember(draft.parseRunId) { mutableStateOf(draft.date) }
    var partial by remember(draft.parseRunId) { mutableStateOf(draft.acceptedPartial) }
    AlertDialog(onDismissRequest = { if (!saving) onDismiss() }, title = { Text(stringResource(R.string.detail_confirm_save)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.detail_confirm_explanation))
            if (draft.expectedConfirmedAt != null) Text(stringResource(R.string.detail_confirm_replace))
            OutlinedTextField(date, { date = it }, singleLine = true, enabled = !saving,
                label = { Text(stringResource(R.string.detail_review_date)) },
                supportingText = { Text(stringResource(R.string.detail_review_date_format)) },
                isError = reviewDate(date) == null)
            if (draft.partial) Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(partial, { partial = it }, enabled = !saving)
                Text(stringResource(R.string.detail_accept_partial), style = MaterialTheme.typography.bodyMedium)
            }
            if (saving) FitLogWavyProgressIndicator(Modifier.fillMaxWidth())
            message?.takeIf { it != R.string.detail_review_saved }?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
        } },
        confirmButton = { TextButton(onClick = { onSave(date, partial) }, enabled = !saving && reviewDate(date) != null && (!draft.partial || partial)) {
            Text(stringResource(R.string.detail_confirm_save))
        } }, dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text(stringResource(R.string.editor_cancel)) } })
}

internal data class DiaryDetailUiState(
    val route: FitLogRoute.DiaryDetail,
    val tab: DiaryDetailTab = DiaryDetailTab.ORIGINAL,
    val original: MarkdownSnapshot? = null,
    val sourceLoading: Boolean = false,
    val sourceReadFailed: Boolean = false,
    val canEdit: Boolean = true,
    val parses: DiaryParseRecords = DiaryParseRecords(),
    val parsesLoading: Boolean = false,
    val parsesReadFailed: Boolean = false,
    val confirmed: ConfirmedDiaryRecord? = null,
    val confirmationLoading: Boolean = false,
    val confirmationReadFailed: Boolean = false,
    val confirmationStatus: ConfirmationFreshness = ConfirmationFreshness.UNCONFIRMED,
    val candidateStatus: DiaryResultFreshness? = null,
    val parsing: Boolean = false,
    val parseMessage: Int? = null,
    val showCandidate: Boolean = false,
    val review: DiaryReviewDraft? = null,
    val reviewSaving: Boolean = false,
    val reviewMessage: Int? = null,
    val canReview: Boolean = false,
    val leaveRequested: Boolean = false,
)

internal data class DiaryDetailActions(
    val refresh: () -> Unit = {}, val parse: () -> Unit = {}, val cancelParse: () -> Unit = {}, val retryAnalysis: () -> Unit = {},
    val selectTab: (DiaryDetailTab) -> Unit = {}, val selectResult: (Boolean) -> Unit = {},
    val updateWeight: (DiarySetAddress, DiaryWeightValue) -> Unit = { _, _ -> }, val updateReps: (DiarySetAddress, Int?) -> Unit = { _, _ -> },
    val beginReview: () -> Boolean = { false }, val cancelConfirmation: () -> Unit = {},
    val saveReview: (String, Boolean) -> Unit = { _, _ -> },
    val cancelLeave: () -> Unit = {}, val discardAndLeave: () -> Unit = {},
)

@FitLogPreviews
@Composable
private fun DiaryOriginalPreview() {
    val original = PreviewDiary.snapshot()
    FitLogPreview { DiaryDetailContent(DiaryDetailUiState(PreviewDiary.route, original = original), DiaryDetailActions(), {}, {}, {}) }
}

@FitLogPreviews
@Composable
private fun DiaryAnalysisPreview() {
    val candidate = PreviewDiary.candidate()
    FitLogPreview {
        DiaryDetailContent(DiaryDetailUiState(PreviewDiary.route, tab = DiaryDetailTab.ANALYSIS,
            parses = DiaryParseRecords(listOf(candidate.attempt), candidate), candidateStatus = DiaryResultFreshness.CURRENT, canReview = true),
            DiaryDetailActions(), {}, {}, {})
    }
}

@FitLogPreviews
@Composable
private fun DiaryAnalysisEmptyPreview() {
    FitLogPreview { DiaryDetailContent(DiaryDetailUiState(PreviewDiary.route, tab = DiaryDetailTab.ANALYSIS), DiaryDetailActions(), {}, {}, {}) }
}

@FitLogPreviews
@Composable
private fun DiaryWeightPickerPreview() {
    FitLogPreview { DiarySetPickerContent(DiarySetEdit(DiarySetAddress(0, 0, 1), 2, DiarySetField.WEIGHT, 60.0, WeightUnit.KG, 8), {}, {}, {}) }
}

@FitLogPreviews
@Composable
private fun DiaryPoundsPickerPreview() {
    FitLogPreview { DiarySetPickerContent(DiarySetEdit(DiarySetAddress(0, 0, 1), 2, DiarySetField.WEIGHT, 100.0, WeightUnit.LB, 8), {}, {}, {}) }
}

@FitLogPreviews
@Composable
private fun DiaryUnspecifiedUnitPickerPreview() {
    FitLogPreview { DiarySetPickerContent(DiarySetEdit(DiarySetAddress(0, 0, 1), 2, DiarySetField.WEIGHT, 60.0, null, 8), {}, {}, {}) }
}

@FitLogPreviews
@Composable
private fun DiaryRepsPickerPreview() {
    FitLogPreview { DiarySetPickerContent(DiarySetEdit(DiarySetAddress(0, 0, 1), 2, DiarySetField.REPS, 60.0, WeightUnit.KG, 8), {}, {}, {}) }
}
