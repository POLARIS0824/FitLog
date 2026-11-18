package com.example.fitlog.feature.log

import com.example.fitlog.feature.log.components.BatchReviewRow
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.triStateToggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fitlog.R
import com.example.fitlog.model.analysis.BatchReviewEligibility
import com.example.fitlog.model.source.IndexedSource
import com.example.fitlog.ui.components.FitLogNotice
import com.example.fitlog.ui.components.FitLogProgressFeedback
import com.example.fitlog.ui.components.FitLogTopAppBar
import com.example.fitlog.ui.components.rememberDelayedLoading
import com.example.fitlog.ui.components.rememberFitLogTopAppBarBehavior
import com.example.fitlog.ui.theme.FitLogTheme
import com.example.fitlog.ui.theme.fitLogPagePadding

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
    val spacing = FitLogTheme.spacing
    val wideContentWidth = FitLogTheme.dimens.page.wideContentWidth
    Column(Modifier.fillMaxSize().nestedScroll(behavior.nestedScrollConnection), horizontalAlignment = Alignment.CenterHorizontally) {
        FitLogTopAppBar(stringResource(R.string.batch_review_title), behavior, onBack = onBack,
            backEnabled = !saving, actions = { IconButton(onClick = onRefresh, enabled = !saving, shapes = IconButtonDefaults.shapes()) {
                Icon(painterResource(R.drawable.refresh_24px), stringResource(R.string.log_refresh))
            } })
        Box(Modifier.weight(1f).widthIn(max = wideContentWidth).fillMaxWidth()) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(pagePadding), verticalArrangement = Arrangement.spacedBy(spacing.s)) {
                item(key = "selection") {
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.s)) {
                        Text(stringResource(R.string.batch_review_explanation), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (ready.isNotEmpty()) {
                            val canSelectReady = !saving && !readFailed && activeVault
                            Row(Modifier.fillMaxWidth().heightIn(min = LocalMinimumInteractiveComponentSize.current).triStateToggleable(state = readyState,
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
                        if (!activeVault && !loading) FitLogNotice(stringResource(R.string.batch_review_vault_changed))
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
            FitLogProgressFeedback(rememberDelayedLoading(loading || saving), Modifier.fillMaxWidth().padding(horizontal = pagePadding))
        }
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            Button(onClick = onSubmit, enabled = selected.isNotEmpty() && !saving && !loading && !readFailed && activeVault,
                shapes = ButtonDefaults.shapesFor(ButtonDefaults.MediumContainerHeight),
                modifier = Modifier.widthIn(max = wideContentWidth).fillMaxWidth().navigationBarsPadding()
                    .padding(horizontal = pagePadding, vertical = spacing.l)
                    .heightIn(min = ButtonDefaults.MediumContainerHeight)) {
                Text(stringResource(if (saving) R.string.batch_review_saving else R.string.batch_review_confirm, selected.size),
                    style = MaterialTheme.typography.titleMediumEmphasized)
            }
        }
    }
}
