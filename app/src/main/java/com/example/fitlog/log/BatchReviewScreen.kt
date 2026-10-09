package com.example.fitlog.log

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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.fitlog.R
import com.example.fitlog.data.analysis.*
import com.example.fitlog.data.index.IndexedSource
import com.example.fitlog.diary.basisLabel
import com.example.fitlog.diary.validationLabel
import com.example.fitlog.diary.weightLabel
import com.example.fitlog.ui.components.FitLogNotice
import com.example.fitlog.ui.components.FitLogProgressFeedback
import com.example.fitlog.ui.components.rememberDelayedLoading
import com.example.fitlog.ui.preview.FitLogPreview
import com.example.fitlog.ui.preview.FitLogPreviews
import com.example.fitlog.ui.preview.PreviewDiary

@Composable
fun BatchReviewScreen(vm: BatchReviewViewModel, onOpen: (IndexedSource) -> Unit, onBack: () -> Unit) {
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.cancelSubmission() }
    BatchReviewContent(vm.items, vm.selected, vm.loading, vm.readFailed, vm.activeVault, vm.saving, vm.message,
        vm.progress, vm.indexWarning, vm::toggle, vm::toggleReady, vm::submit, vm::refresh,
        onOpen = onOpen, onBack = { vm.requestLeave(onBack) })
}

@Composable
internal fun BatchReviewContent(items: List<BatchReviewItem>, selected: Set<String>, loading: Boolean, readFailed: Boolean,
    activeVault: Boolean, saving: Boolean, message: Int?, progress: BatchConfirmationProgress, indexWarning: Boolean,
    onToggle: (BatchReviewItem) -> Unit, onToggleReady: () -> Unit, onSubmit: () -> Unit, onRefresh: () -> Unit,
    onOpen: (IndexedSource) -> Unit, onBack: () -> Unit) {
    val ready = items.filter { it.assessment.eligibility == BatchReviewEligibility.READY }
    val checked = ready.count { it.id in selected }
    val readyState = when { checked == 0 -> ToggleableState.Off; checked == ready.size -> ToggleableState.On; else -> ToggleableState.Indeterminate }
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        TopAppBar(title = { Text(stringResource(R.string.batch_review_title)) },
            navigationIcon = { IconButton(onClick = onBack, enabled = !saving) {
                Icon(painterResource(R.drawable.arrow_back_24px), stringResource(R.string.cd_back))
            } }, actions = { IconButton(onClick = onRefresh, enabled = !saving) {
                Icon(painterResource(R.drawable.refresh_24px), stringResource(R.string.log_refresh))
            } })
        Box(Modifier.weight(1f).widthIn(max = 840.dp).fillMaxWidth()) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item(key = "selection") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.batch_review_explanation), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val canSelectReady = !saving && !readFailed && activeVault && ready.isNotEmpty()
                        Row(Modifier.fillMaxWidth().triStateToggleable(state = readyState,
                            enabled = canSelectReady, role = Role.Checkbox, onClick = onToggleReady),
                            verticalAlignment = Alignment.CenterVertically) {
                            TriStateCheckbox(state = readyState, onClick = null, enabled = canSelectReady)
                            Text(stringResource(R.string.batch_review_select_ready), Modifier.weight(1f))
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
                modifier = Modifier.widthIn(max = 840.dp).fillMaxWidth().navigationBarsPadding().padding(16.dp)
                    .heightIn(min = ButtonDefaults.MediumContainerHeight)) {
                Text(stringResource(if (saving) R.string.batch_review_saving else R.string.batch_review_confirm, selected.size),
                    style = MaterialTheme.typography.labelLargeEmphasized)
            }
        }
    }
}

@Composable
private fun BatchReviewRow(item: BatchReviewItem, selected: Boolean, enabled: Boolean, onToggle: () -> Unit, onOpen: () -> Unit) {
    var expanded by rememberSaveable(item.id) { mutableStateOf(false) }
    val candidate = item.summary.candidate?.analysis
    val exercises = candidate?.sessions?.flatMap { it.exercises }.orEmpty()
    Surface(shape = MaterialTheme.shapes.large,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val selectionModifier = if (item.selectable) Modifier.fillMaxWidth().toggleable(value = selected,
                enabled = enabled, role = Role.Checkbox, onValueChange = { onToggle() }) else Modifier.fillMaxWidth()
            Row(selectionModifier, verticalAlignment = Alignment.Top) {
                if (item.selectable) Checkbox(checked = selected, onCheckedChange = null, enabled = enabled)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(item.source?.name ?: item.summary.sourceKey.relPath.substringAfterLast('/'),
                        style = MaterialTheme.typography.titleMediumEmphasized)
                    Text(item.summary.sourceKey.relPath, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.batch_review_summary, item.assessment.date?.toString()
                        ?: stringResource(R.string.detail_value_unknown), exercises.size, exercises.sumOf { it.sets.size }))
                }
            }
            if (!item.selectable) Text(stringResource(blockMessage(item.assessment)), color = MaterialTheme.colorScheme.error)
            if (candidate != null) {
                if (exercises.isEmpty()) Text(stringResource(R.string.batch_review_no_training))
                batchReviewNotices(candidate).forEach { Text(stringResource(validationLabel(it)), style = MaterialTheme.typography.bodySmall) }
                candidate.modelIssues.forEach { Text(it.question, style = MaterialTheme.typography.bodySmall) }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { expanded = !expanded }) { Text(stringResource(
                    if (expanded) R.string.batch_review_collapse else R.string.batch_review_expand)) }
                TextButton(onClick = onOpen, enabled = enabled && item.source != null && item.source.status != IndexedSource.MISSING) {
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
                                stringResource(basisLabel(set.basis.value)), set.reps.value?.toString()
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
