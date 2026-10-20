package com.example.fitlog.diary

import com.example.fitlog.ui.components.FitLogLoadingIndicator
import com.example.fitlog.ui.components.FitLogProgressFeedback
import com.example.fitlog.ui.components.rememberDelayedLoading

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.example.fitlog.navigation.FitLogBackGuard
import com.example.fitlog.R
import com.example.fitlog.data.analysis.*
import com.example.fitlog.data.vault.MarkdownSnapshot
import com.example.fitlog.navigation.FitLogRoute
import com.example.fitlog.ui.components.FitLogNotice
import com.example.fitlog.ui.preview.*
import java.text.DateFormat
import java.util.Date

@Composable
fun DiaryDetailScreen(vm: DiaryDetailViewModel, onBack: () -> Unit, onAiSettings: () -> Unit) {
    LifecycleEventEffect(Lifecycle.Event.ON_START) { vm.onStart() }
    FitLogBackGuard(enabled = vm.review != null || vm.reviewSaving, onBackCompleted = onBack)
    DiaryDetailContent(
        DiaryDetailUiState(route = vm.route, tab = vm.tab, original = vm.original, sourceLoading = vm.sourceLoading,
            sourceReadFailed = vm.sourceReadFailed, parses = vm.parses, parsesLoading = vm.parsesLoading,
            parsesReadFailed = vm.parsesReadFailed, confirmed = vm.confirmed, confirmationLoading = vm.confirmationLoading,
            confirmationReadFailed = vm.confirmationReadFailed, confirmationStatus = vm.confirmationStatus,
            candidateStatus = vm.candidateStatus, parsing = vm.parsing, parseMessage = vm.parseMessage,
            analysisBusy = vm.analysisBusy, analysisIndexWarning = vm.analysisIndexWarning,
            showCandidate = vm.showCandidate, review = vm.review, reviewSaving = vm.reviewSaving,
            reviewMessage = vm.reviewMessage, canReview = vm.canReview, leaveRequested = vm.leaveRequested),
        DiaryDetailActions(refresh = vm::refresh, parse = vm::parse, cancelParse = vm::cancelParse, retryAnalysis = vm::retryAnalysis,
            selectTab = vm::selectTab, selectResult = vm::selectResult,
            updateWeight = { address, value -> vm.updateWeight(address, value.weight, value.unit, value.converted, value.basis) }, updateReps = vm::updateReps,
            updateBasis = vm::updateBasis, updateExercise = vm::updateExercise, addExercise = vm::addExercise,
            removeExercise = vm::removeExercise, removeSession = vm::removeSession, clearTraining = vm::clearTraining,
            draftForReview = vm::draftForReview,
            beginReview = vm::beginReview, cancelConfirmation = vm::cancelReviewConfirmation, saveReview = vm::saveReview,
            cancelLeave = vm::cancelLeave, discardAndLeave = vm::discardAndLeave, revoke = vm::revokeConfirmation),
        onBack, onAiSettings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DiaryDetailContent(state: DiaryDetailUiState, actions: DiaryDetailActions,
    onBack: () -> Unit, onAiSettings: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    var fileInfo by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf<DiarySetEdit?>(null) }
    var exerciseEdit by remember { mutableStateOf<DiaryExerciseEdit?>(null) }
    var removal by remember { mutableStateOf<DiaryReviewRemoval?>(null) }
    var revoking by remember { mutableStateOf(false) }
    var confirming by remember { mutableStateOf(false) }
    val showSourceLoading = rememberDelayedLoading(state.sourceLoading)
    val showParsesLoading = rememberDelayedLoading(state.parsesLoading)
    val showConfirmationLoading = rememberDelayedLoading(state.confirmationLoading)
    fun requestRemoval(title: Int, apply: () -> Unit) {
        if (state.canReview) actions.draftForReview()?.let { removal = DiaryReviewRemoval(title, it, apply) }
    }
    LaunchedEffect(state.review) { if (state.review == null) confirming = false }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 840.dp).fillMaxSize()) {
            TopAppBar(
                title = { Text(diaryDateFromFileName(state.route.fileName)?.toString() ?: state.route.fileName,
                    style = MaterialTheme.typography.titleLargeEmphasized) },
                navigationIcon = { IconButton(onClick = onBack, enabled = !state.reviewSaving, shapes = IconButtonDefaults.shapes()) {
                    Icon(painterResource(R.drawable.arrow_back_24px), stringResource(R.string.cd_back))
                } },
                actions = {
                    Box {
                        IconButton(onClick = { menu = true }, enabled = !state.reviewSaving, shapes = IconButtonDefaults.shapes()) {
                            Icon(painterResource(R.drawable.more_vert_24px), stringResource(R.string.log_options))
                        }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.log_refresh)) },
                                enabled = !state.sourceLoading, onClick = { menu = false; actions.refresh() })
                            DropdownMenuItem(text = { Text(stringResource(R.string.ai_settings_title)) },
                                onClick = { menu = false; onAiSettings() })
                            DropdownMenuItem(text = { Text(stringResource(R.string.detail_file_info)) }, onClick = { menu = false; fileInfo = true })
                            if (state.confirmed != null) DropdownMenuItem(text = { Text(stringResource(R.string.record_revoke)) },
                                enabled = state.review == null, onClick = { menu = false; revoking = true })
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
                    onEdit = { edit = it }, onConfirm = { if (actions.beginReview()) confirming = true },
                    onEditExercise = { address -> if (state.canReview) actions.draftForReview()?.let { draft ->
                        exerciseEdit = DiaryExerciseEdit(address, draft.sessions.getOrNull(address.session)?.exercises?.getOrNull(address.exercise),
                            draft.fragments.filter { it.session == address.session && it.exercise == address.exercise })
                    } }, onDeleteExercise = { address ->
                        requestRemoval(R.string.detail_delete_exercise_title) { actions.removeExercise(address.session, address.exercise) }
                    }, onDeleteSession = { session ->
                        requestRemoval(R.string.detail_delete_session_title) { actions.removeSession(session) }
                    }, onClearTraining = { requestRemoval(R.string.detail_clear_training_title, actions.clearTraining) })
                // Loading must not change the viewport height during entry or refresh.
                FitLogProgressFeedback(showSourceLoading, Modifier.fillMaxWidth().align(Alignment.TopCenter))
            }
        }
    }
    edit?.let { target ->
        DiarySetPicker(target, onDismiss = { edit = null },
            onWeight = { actions.updateWeight(target.address, it); edit = null },
            onReps = { actions.updateReps(target.address, it); edit = null },
            onBasis = { basis, all -> actions.updateBasis(target.address, basis, all); edit = null })
    }
    exerciseEdit?.let { target ->
        DiaryExerciseReviewEditor(target.address, target.exercise, target.fragments,
            onDismiss = { exerciseEdit = null }, onSave = { name, count, fragmentCounts ->
                if (target.exercise == null) actions.addExercise(target.address.session, name, count)
                else actions.updateExercise(target.address.session, target.address.exercise, name, count, fragmentCounts)
                exerciseEdit = null
            })
    }
    if (revoking) AlertDialog(onDismissRequest = { revoking = false },
        text = { Text(stringResource(R.string.record_revoke_confirm)) },
        confirmButton = { TextButton(onClick = { revoking = false; actions.revoke() }) { Text(stringResource(R.string.record_revoke)) } },
        dismissButton = { TextButton(onClick = { revoking = false }) { Text(stringResource(R.string.record_cancel)) } })
    if (fileInfo) AlertDialog(onDismissRequest = { fileInfo = false }, title = { Text(stringResource(R.string.detail_file_info)) },
        text = { SelectionContainer { Text(state.route.relPath) } },
        confirmButton = { TextButton(onClick = { fileInfo = false }) { Text(stringResource(R.string.detail_picker_done)) } })
    if (confirming) state.review?.let { draft ->
        ReviewConfirmationDialog(draft, state.reviewSaving, state.reviewMessage,
            onDismiss = { actions.cancelConfirmation(); confirming = false }, onSave = actions.saveReview)
    }
    removal?.let { target ->
        val unchanged = state.canReview && actions.draftForReview() == target.draft
        AlertDialog(onDismissRequest = { removal = null }, title = { Text(stringResource(target.title)) },
            text = { Text(stringResource(if (unchanged) R.string.detail_delete_review_help else R.string.detail_review_changed)) },
            confirmButton = { TextButton(onClick = {
                if (actions.draftForReview() == target.draft) target.apply()
                removal = null
            }, enabled = unchanged, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                Text(stringResource(R.string.detail_delete_review_confirm))
            } }, dismissButton = { TextButton(onClick = { removal = null }) { Text(stringResource(R.string.record_cancel)) } })
    }
    if (state.leaveRequested) AlertDialog(onDismissRequest = actions.cancelLeave,
        title = { Text(stringResource(R.string.detail_unsaved_title)) },
        text = { Text(stringResource(R.string.detail_unsaved_message)) },
        confirmButton = { TextButton(onClick = actions.cancelLeave) { Text(stringResource(R.string.detail_continue_review)) } },
        dismissButton = { TextButton(onClick = actions.discardAndLeave) { Text(stringResource(R.string.detail_discard_review)) } })
}

@Composable
private fun AnalysisContent(state: DiaryDetailUiState, actions: DiaryDetailActions, modifier: Modifier,
    showParsesLoading: Boolean, showConfirmationLoading: Boolean,
    onEdit: (DiarySetEdit) -> Unit, onConfirm: () -> Unit, onEditExercise: (DiaryExerciseAddress) -> Unit,
    onDeleteExercise: (DiaryExerciseAddress) -> Unit, onDeleteSession: (Int) -> Unit, onClearTraining: () -> Unit) {
    val candidateSelected = state.showCandidate || state.confirmed == null
    // The first confirmation read determines the result selector and candidate provenance.
    // Show that metadata together with the candidate, rather than inserting it above visible sets.
    val initialConfirmationRead = state.confirmationLoading && state.confirmed == null && state.review == null
    LazyColumn(modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item(key = "status") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.detail_review_help), style = MaterialTheme.typography.bodyMedium)
                AnalysisStatusCard(state, actions, showParsesLoading, showConfirmationLoading)
                if (state.sourceReadFailed) FitLogNotice(stringResource(R.string.detail_source_failed), error = true)
                if (state.parsesReadFailed || state.confirmationReadFailed || state.parses.candidateReadFailed) {
                    FitLogNotice(stringResource(if (state.parses.candidateReadFailed) R.string.detail_candidate_damaged else R.string.detail_analysis_failed), error = true)
                    TextButton(onClick = actions.retryAnalysis, enabled = !state.parsesLoading && !state.confirmationLoading) {
                        Text(stringResource(R.string.detail_retry_analysis))
                    }
                }
                state.parseMessage?.let { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium) }
                if (state.analysisIndexWarning) FitLogNotice(stringResource(R.string.log_analysis_index_warning), error = true)
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
                freshness = state.candidateStatus, usedForConfirmation = state.confirmed?.diary?.parseRunId == state.review.parseRunId,
                onEditExercise = onEditExercise, checkingSource = state.sourceLoading && state.original == null,
                onDeleteExercise = onDeleteExercise)
        } else if (!initialConfirmationRead && candidateSelected) state.parses.latestCandidate?.let { candidate ->
            candidateItems(candidate, state.candidateStatus, state.confirmed?.diary?.parseRunId == candidate.attempt.id,
                canEdit = state.canReview, onEdit = onEdit, onEditExercise = onEditExercise,
                checkingSource = state.sourceLoading && state.original == null, onDeleteExercise = onDeleteExercise)
        } else if (!initialConfirmationRead) confirmedItems(requireNotNull(state.confirmed), canEdit = state.canReview,
            onEdit = onEdit, onEditExercise = onEditExercise, onDeleteExercise = onDeleteExercise)
        if (!state.parsesLoading && !state.confirmationLoading && state.parses.latestCandidate == null && state.confirmed == null) {
            item(key = "empty") { Text(stringResource(R.string.detail_no_saved_result)) }
        }
        if (state.canReview || state.reviewSaving) {
            val sessions = state.review?.sessions?.map { it.exercises.size }
                ?: if (candidateSelected) state.parses.latestCandidate?.analysis?.sessions?.map { it.exercises.size }.orEmpty()
                else requireNotNull(state.confirmed).sessions.map { it.exercises.size }
            sessions.ifEmpty { listOf(0) }.forEachIndexed { session, exerciseCount ->
                item(key = "add-exercise:$session") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { onEditExercise(DiaryExerciseAddress(session, exerciseCount)) }, enabled = state.canReview) {
                            Text(stringResource(if (sessions.size > 1) R.string.detail_add_session_exercise else R.string.detail_add_exercise, session + 1))
                        }
                        if (session in sessions.indices) TextButton(onClick = { onDeleteSession(session) }, enabled = state.canReview,
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                            Text(stringResource(R.string.detail_delete_session, session + 1))
                        }
                    }
                }
            }
            if (sessions.any { it > 0 }) item(key = "clear-training") {
                TextButton(onClick = onClearTraining, enabled = state.canReview,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Text(stringResource(R.string.detail_clear_training))
                }
            }
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
                    if (state.parsing) TextButton(onClick = actions.cancelParse) { Text(stringResource(R.string.ai_cancel_request)) }
                    FilledTonalButton(onClick = actions.parse,
                        enabled = extractionIdle && !state.sourceLoading,
                        colors = if (state.sourceLoading && extractionIdle) ButtonDefaults.filledTonalButtonColors(
                            disabledContainerColor = extractionColors.containerColor,
                            disabledContentColor = extractionColors.contentColor) else extractionColors,
                        shapes = ButtonDefaults.shapes()) {
                        Icon(painterResource(R.drawable.auto_awesome_24px), null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(if (hasExtraction) R.string.ai_reparse else R.string.ai_parse))
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
private fun ReviewConfirmationDialog(draft: DiaryReviewDraft, saving: Boolean, message: Int?, onDismiss: () -> Unit,
    onSave: (String, Boolean) -> Unit) {
    var date by remember(draft.parseRunId) { mutableStateOf(draft.date) }
    var partial by remember(draft.parseRunId) { mutableStateOf(draft.acceptedPartial) }
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
        confirmButton = { TextButton(onClick = { onSave(date, partial) }, enabled = !saving && reviewDate(date) != null && (!draft.partial || partial)) {
            Text(stringResource(R.string.detail_confirm_save))
        } }, dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text(stringResource(R.string.record_cancel)) } })
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
    val confirmed: TrainingRecordDetails? = null,
    val confirmationLoading: Boolean = false,
    val confirmationReadFailed: Boolean = false,
    val confirmationStatus: ConfirmationFreshness = ConfirmationFreshness.UNCONFIRMED,
    val candidateStatus: DiaryResultFreshness? = null,
    val parsing: Boolean = false,
    val analysisBusy: Boolean = false,
    val analysisIndexWarning: Boolean = false,
    val parseMessage: Int? = null,
    val showCandidate: Boolean = false,
    val review: DiaryReviewDraft? = null,
    val reviewSaving: Boolean = false,
    val reviewMessage: Int? = null,
    val canReview: Boolean = false,
    val leaveRequested: Boolean = false,
)

internal data class DiaryDetailActions(
    val revoke: () -> Unit = {},
    val refresh: () -> Unit = {}, val parse: () -> Unit = {}, val cancelParse: () -> Unit = {}, val retryAnalysis: () -> Unit = {},
    val selectTab: (DiaryDetailTab) -> Unit = {}, val selectResult: (Boolean) -> Unit = {},
    val updateWeight: (DiarySetAddress, DiaryWeightValue) -> Unit = { _, _ -> }, val updateReps: (DiarySetAddress, Int?) -> Unit = { _, _ -> },
    val updateBasis: (DiarySetAddress, WeightBasis, Boolean) -> Unit = { _, _, _ -> },
    val updateExercise: (Int, Int, String, Int, Map<Int, Int>) -> Unit = { _, _, _, _, _ -> },
    val addExercise: (Int, String, Int) -> Unit = { _, _, _ -> },
    val removeExercise: (Int, Int) -> Unit = { _, _ -> },
    val removeSession: (Int) -> Unit = {}, val clearTraining: () -> Unit = {},
    val draftForReview: () -> DiaryReviewDraft? = { null },
    val beginReview: () -> Boolean = { false }, val cancelConfirmation: () -> Unit = {},
    val saveReview: (String, Boolean) -> Unit = { _, _ -> },
    val cancelLeave: () -> Unit = {}, val discardAndLeave: () -> Unit = {},
)

private data class DiaryReviewRemoval(val title: Int, val draft: DiaryReviewDraft, val apply: () -> Unit)

@FitLogPreviews
@Composable
private fun DiaryOriginalPreview() {
    val original = PreviewDiary.snapshot()
    FitLogPreview { DiaryDetailContent(DiaryDetailUiState(PreviewDiary.route, original = original), DiaryDetailActions(), {}, {}) }
}

@FitLogPreviews
@Composable
private fun DiaryAnalysisPreview() {
    val candidate = PreviewDiary.candidate()
    FitLogPreview {
        DiaryDetailContent(DiaryDetailUiState(PreviewDiary.route, tab = DiaryDetailTab.ANALYSIS,
            parses = DiaryParseRecords(listOf(candidate.attempt), candidate), candidateStatus = DiaryResultFreshness.CURRENT, canReview = true),
            DiaryDetailActions(), {}, {})
    }
}

@FitLogPreviews
@Composable
private fun DiaryAnalysisEmptyPreview() {
    FitLogPreview { DiaryDetailContent(DiaryDetailUiState(PreviewDiary.route, tab = DiaryDetailTab.ANALYSIS), DiaryDetailActions(), {}, {}) }
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
