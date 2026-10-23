package com.example.fitlog.feature.log

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.triStateToggleable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fitlog.R
import com.example.fitlog.domain.analysis.assessBatchReview
import com.example.fitlog.domain.analysis.batchReviewNotices
import com.example.fitlog.feature.diary.validationLabel
import com.example.fitlog.feature.diary.weightLabel
import com.example.fitlog.model.analysis.BatchReviewAssessment
import com.example.fitlog.model.analysis.BatchReviewBlock
import com.example.fitlog.model.analysis.BatchReviewEligibility
import com.example.fitlog.model.analysis.DiaryAnalysisSummary
import com.example.fitlog.model.source.IndexedSource
import com.example.fitlog.ui.components.FitLogDimens
import com.example.fitlog.ui.components.FitLogNotice
import com.example.fitlog.ui.components.FitLogProgressFeedback
import com.example.fitlog.ui.components.FitLogTopAppBar
import com.example.fitlog.ui.components.fitLogPagePadding
import com.example.fitlog.ui.components.rememberDelayedLoading
import com.example.fitlog.ui.components.rememberFitLogTopAppBarBehavior
import com.example.fitlog.ui.preview.FitLogPreview
import com.example.fitlog.ui.preview.FitLogPreviews
import com.example.fitlog.ui.preview.PreviewDiary
import com.example.fitlog.ui.training.weightBasisLabel

@Composable
internal fun BatchReviewScreen(vm: BatchReviewViewModel, onOpen: (IndexedSource) -> Unit, onBack: () -> Unit) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        vm.onStopped((context as? android.app.Activity)?.isChangingConfigurations == true)
    }
    BatchReviewContent(state.items, state.selected, state.loading, state.readFailed, state.activeVault, state.saving, state.message,
        state.progress, state.indexWarning, vm::toggle, vm::toggleReady, vm::submit, vm::refresh,
        onOpen = onOpen, onBack = { if (!state.saving) onBack() })
}

@Composable
internal fun BatchReviewContent(items: List<BatchReviewItem>, selected: Set<String>, loading: Boolean, readFailed: Boolean,
    activeVault: Boolean, saving: Boolean, message: Int?, progress: BatchConfirmationProgress, indexWarning: Boolean,
    onToggle: (BatchReviewItem) -> Unit, onToggleReady: () -> Unit, onSubmit: () -> Unit, onRefresh: () -> Unit,
    onOpen: (IndexedSource) -> Unit, onBack: () -> Unit) {
    val ready = items.filter { it.assessment.eligibility == BatchReviewEligibility.READY }
    val checked = ready.count { it.id in selected }
    val readyState = when { checked == 0 -> ToggleableState.Off; checked == ready.size -> ToggleableState.On; else -> ToggleableState.Indeterminate }
    val behavior = rememberFitLogTopAppBarBehavior()
    val pagePadding = fitLogPagePadding()
    Column(Modifier.fillMaxSize().nestedScroll(behavior.nestedScrollConnection), horizontalAlignment = Alignment.CenterHorizontally) {
        FitLogTopAppBar(stringResource(R.string.batch_review_title), behavior, onBack = onBack,
            backEnabled = !saving, actions = { IconButton(onClick = onRefresh, enabled = !saving, shapes = IconButtonDefaults.shapes()) {
                Icon(painterResource(R.drawable.refresh_24px), stringResource(R.string.log_refresh))
            } })
        Box(Modifier.weight(1f).widthIn(max = FitLogDimens.WideContentWidth).fillMaxWidth()) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(pagePadding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item(key = "selection") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.batch_review_explanation), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (ready.isNotEmpty()) {
                            val canSelectReady = !saving && !readFailed && activeVault
                            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).triStateToggleable(state = readyState,
                                enabled = canSelectReady, role = Role.Checkbox, onClick = onToggleReady),
                                verticalAlignment = Alignment.CenterVertically) {
                                TriStateCheckbox(state = readyState, onClick = null, enabled = canSelectReady)
                                Text(stringResource(R.string.batch_review_select_ready), Modifier.weight(1f))
                            }
                            Text(pluralStringResource(R.plurals.batch_review_ready_count, ready.size, ready.size),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else if (!loading && !readFailed && activeVault && items.isNotEmpty()) {
                            Text(stringResource(R.string.batch_review_no_ready), style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (readFailed) FitLogNotice(stringResource(R.string.detail_analysis_failed), error = true)
                        if (!activeVault && !loading) FitLogNotice(stringResource(R.string.batch_review_vault_changed), error = true)
                        message?.let { FitLogNotice(stringResource(it), error = true) }
                        if (indexWarning) FitLogNotice(stringResource(R.string.log_analysis_index_warning), error = true)
                        if (progress.total > 0) Text(stringResource(R.string.batch_review_progress,
                            progress.confirmed, progress.skipped, progress.failed, progress.remaining))
                        if (!loading && items.isEmpty() && !readFailed) Text(stringResource(R.string.batch_review_empty))
                    }
                }
                listOf(BatchReviewEligibility.READY, BatchReviewEligibility.WITH_NOTICES, BatchReviewEligibility.BLOCKED).forEach { group ->
                    val members = items.filter { item -> when (group) {
                        BatchReviewEligibility.BLOCKED -> !item.selectable
                        else -> item.assessment.eligibility == group
                    } }
                    if (members.isNotEmpty()) {
                        item(key = group.name) { Text(stringResource(when (group) {
                            BatchReviewEligibility.READY -> R.string.batch_review_ready
                            BatchReviewEligibility.WITH_NOTICES -> R.string.batch_review_notices
                            else -> R.string.batch_review_blocked
                        }, members.size), style = MaterialTheme.typography.titleMediumEmphasized,
                            modifier = Modifier.semantics { heading() }) }
                        items(members, key = { it.id }) { item ->
                            BatchReviewRow(item, item.id in selected, enabled = !saving && activeVault && !readFailed,
                                onToggle = { onToggle(item) }, onOpen = { item.source?.let(onOpen) })
                        }
                    }
                }
            }
            FitLogProgressFeedback(rememberDelayedLoading(loading || saving), Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        }
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            Button(onClick = onSubmit, enabled = selected.isNotEmpty() && !saving && !loading && !readFailed && activeVault,
                shapes = ButtonDefaults.shapesFor(ButtonDefaults.MediumContainerHeight),
                modifier = Modifier.widthIn(max = FitLogDimens.WideContentWidth).fillMaxWidth().navigationBarsPadding().padding(16.dp)
                    .heightIn(min = ButtonDefaults.MediumContainerHeight)) {
                Text(stringResource(if (saving) R.string.batch_review_saving else R.string.batch_review_confirm, selected.size),
                    style = MaterialTheme.typography.titleMediumEmphasized)
            }
        }
    }
}

@Composable
private fun BatchReviewRow(item: BatchReviewItem, selected: Boolean, enabled: Boolean, onToggle: () -> Unit, onOpen: () -> Unit) {
    var expanded by rememberSaveable(item.id) { mutableStateOf(false) }
    val candidate = item.summary.candidate?.analysis
    val exercises = candidate?.sessions?.flatMap { it.exercises }.orEmpty()
    val supportingColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(shape = MaterialTheme.shapes.large,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val selectionModifier = if (item.selectable) Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(value = selected,
                enabled = enabled, role = Role.Checkbox, onValueChange = { onToggle() }) else Modifier.fillMaxWidth()
            Row(selectionModifier, verticalAlignment = Alignment.Top) {
                if (item.selectable) Checkbox(checked = selected, onCheckedChange = null, enabled = enabled)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(item.source?.name ?: item.summary.sourceKey.relPath.substringAfterLast('/'),
                        style = MaterialTheme.typography.titleMediumEmphasized)
                    Text(item.summary.sourceKey.relPath, style = MaterialTheme.typography.bodySmall,
                        color = supportingColor)
                    Text(stringResource(R.string.batch_review_summary, item.assessment.date?.toString()
                        ?: stringResource(R.string.detail_value_unknown), exercises.size, exercises.sumOf { it.sets.size }))
                }
            }
            if (!item.selectable) Text(stringResource(blockMessage(item.assessment)),
                color = if (item.assessment.eligibility == BatchReviewEligibility.ALREADY_CONFIRMED) supportingColor
                    else MaterialTheme.colorScheme.error)
            if (candidate != null) {
                if (exercises.isEmpty()) Text(stringResource(R.string.batch_review_no_training))
                batchReviewNotices(candidate).forEach { Text(stringResource(validationLabel(it)), style = MaterialTheme.typography.bodySmall) }
                candidate.modelIssues.forEach { Text(it.question, style = MaterialTheme.typography.bodySmall) }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { expanded = !expanded }, shapes = ButtonDefaults.shapes()) { Text(stringResource(
                    if (expanded) R.string.batch_review_collapse else R.string.batch_review_expand)) }
                TextButton(onClick = onOpen, enabled = enabled && item.source != null && item.source.status != IndexedSource.MISSING, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.batch_review_open_diary))
                }
            }
            AnimatedVisibility(expanded,
                enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                exit = shrinkVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec())) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    exercises.forEach { exercise ->
                        Text(exercise.candidate.rawName, style = MaterialTheme.typography.titleSmall)
                        exercise.sets.forEachIndexed { index, set ->
                            Text(stringResource(R.string.batch_review_set, index + 1,
                                weightLabel(set.weight.value, set.unit.value, basis = set.basis.value),
                                stringResource(weightBasisLabel(set.basis.value)), set.reps.value?.toString()
                                    ?: stringResource(R.string.detail_value_unknown)), style = MaterialTheme.typography.bodyMedium)
                        }
                        if (exercise.evidence.quote.isNotBlank()) SelectionContainer {
                            Text(exercise.evidence.quote, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

private fun blockMessage(value: BatchReviewAssessment) = when {
    value.eligibility == BatchReviewEligibility.ALREADY_CONFIRMED -> R.string.batch_review_has_confirmation
    else -> when (value.block) {
        BatchReviewBlock.SOURCE -> R.string.detail_source_failed
        BatchReviewBlock.VERSION -> R.string.detail_unverifiable
        BatchReviewBlock.STALE -> R.string.detail_needs_update
        BatchReviewBlock.DATE -> R.string.batch_review_date_problem
        BatchReviewBlock.PARTIAL -> R.string.batch_review_partial_problem
        BatchReviewBlock.ATTEMPT -> R.string.batch_review_attempt_failed
        else -> R.string.detail_candidate_damaged
    }
}

@FitLogPreviews
@Composable
private fun BatchReviewEmptyPreview() { FitLogPreview {
    BatchReviewContent(emptyList(), emptySet(), false, false, true, false, null, BatchConfirmationProgress(), false,
        {}, {}, {}, {}, {}, {})
} }

@FitLogPreviews
@Composable
private fun BatchReviewCandidatesPreview() { FitLogPreview {
    val candidate = PreviewDiary.candidate()
    val key = candidate.analysis.parseKey
    val source = IndexedSource(key.sourceKey.vaultId, PreviewDiary.DOCUMENT, PreviewDiary.FILE_NAME,
        key.sourceKey.relPath, PreviewDiary.VAULT_URI, true, contentHash = key.contentHash, hashVersion = key.hashVersion)
    val summary = DiaryAnalysisSummary(key.sourceKey, candidate.attempt, candidate)
    val ready = BatchReviewItem(source, summary, assessBatchReview(summary, indexedVersion(source), true))
    BatchReviewContent(listOf(ready), setOf(ready.id), false, false, true, false, null,
        BatchConfirmationProgress(), false, {}, {}, {}, {}, {}, {})
} }
