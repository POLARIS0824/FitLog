package com.example.fitlog.log

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.fitlog.R
import com.example.fitlog.ai.aiConfigurationMessage
import com.example.fitlog.ai.aiRequestMessage
import com.example.fitlog.data.analysis.*
import java.time.LocalDate

internal fun analysisIssueMessage(issue: AnalysisIssue): Int = issue.configurationFailure?.let(::aiConfigurationMessage)
    ?: issue.modelFailure?.let(::aiRequestMessage) ?: when (issue.problem) {
        AnalysisProblem.SOURCE -> R.string.detail_source_failed
        AnalysisProblem.SOURCE_CHANGED -> R.string.detail_needs_update
        AnalysisProblem.STORAGE -> R.string.ai_parse_save_failed
        AnalysisProblem.CONFIGURATION -> R.string.ai_not_configured
        AnalysisProblem.REQUEST -> R.string.ai_request_failed
    }

@Composable
internal fun LogAnalysisControls(state: LogUiState, actions: LogActions, onReview: () -> Unit) {
    var showSheet by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { showSheet = true }, enabled = state.vault != null && !state.analysisBusy &&
                !state.refreshing && !state.summariesLoading && !state.summariesReadFailed,
                shapes = ButtonDefaults.shapesFor(ButtonDefaults.MediumContainerHeight),
                modifier = Modifier.heightIn(min = ButtonDefaults.MediumContainerHeight)) {
                Text(stringResource(R.string.log_analyze), style = MaterialTheme.typography.labelLargeEmphasized)
            }
            FilledTonalButton(onClick = onReview, enabled = state.vault != null && !state.summariesLoading &&
                !state.summariesReadFailed && state.pendingCount > 0) {
                Text(stringResource(R.string.log_review_count, state.pendingCount))
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LogStatusFilter.entries.forEach { filter ->
                FilterChip(selected = state.statusFilter == filter, onClick = { actions.filter(filter) },
                    label = { Text(stringResource(filter.label)) })
            }
        }
        if (state.summariesReadFailed) Text(stringResource(R.string.detail_analysis_failed), color = MaterialTheme.colorScheme.error)
        if (state.run.batch && state.run.total > 0) AnalysisRunFeedback(state.run,
            onCancel = actions.cancelAnalysis, onFailures = { actions.filter(LogStatusFilter.FAILED) }, onAiSettings = actions.openAiSettings)
    }
    if (showSheet) AnalysisRangeSheet(state, actions, onDismiss = { showSheet = false })
}

@Composable
internal fun AnalysisRunFeedback(run: DiaryAnalysisRun, onCancel: () -> Unit, onFailures: () -> Unit, onAiSettings: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(when {
                run.active -> R.string.log_batch_running
                run.stopped || run.issue != null -> R.string.log_batch_stopped
                else -> R.string.log_batch_finished
            }, run.items.size, run.total), style = MaterialTheme.typography.titleMediumEmphasized)
            Text(stringResource(R.string.log_batch_counts, run.succeeded, run.failed, run.skipped, run.remaining),
                style = MaterialTheme.typography.bodyMedium)
            if (run.active) LinearWavyProgressIndicator(progress = { run.items.size.toFloat() / run.total.coerceAtLeast(1) },
                modifier = Modifier.fillMaxWidth())
            run.issue?.let { Text(stringResource(analysisIssueMessage(it)), color = MaterialTheme.colorScheme.error) }
            if (run.items.any { it.issue?.problem == AnalysisProblem.SOURCE_CHANGED })
                Text(stringResource(R.string.log_analysis_source_changed), color = MaterialTheme.colorScheme.error)
            if (run.indexWarning) Text(stringResource(R.string.log_analysis_index_warning), color = MaterialTheme.colorScheme.error)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (run.active) TextButton(onClick = onCancel) { Text(stringResource(R.string.ai_cancel_request)) }
                if (run.failed > 0 || run.items.any { it.issue?.problem == AnalysisProblem.SOURCE })
                    TextButton(onClick = onFailures) { Text(stringResource(R.string.log_view_failures)) }
                if (run.issue?.problem == AnalysisProblem.CONFIGURATION)
                    TextButton(onClick = onAiSettings) { Text(stringResource(R.string.ai_settings_title)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnalysisRangeSheet(state: LogUiState, actions: LogActions, onDismiss: () -> Unit) {
    var range by remember { mutableStateOf(LogAnalysisRange.INCREMENTAL) }
    var reanalyze by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    val allMatched = matchAnalysisSources(state.sources, state.summaries, range, today, includeUnavailable = true)
    val matched = allMatched.filter { it.status == com.example.fitlog.data.index.IndexedSource.AVAILABLE }
    val requests = matched.count { reanalyze || needsDiaryAnalysis(state.summaries[it.path], indexedVersion(it)) }
    val unreadable = allMatched.size - matched.size
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.log_analyze), style = MaterialTheme.typography.headlineSmallEmphasized)
                Text(stringResource(R.string.log_analysis_scope), color = MaterialTheme.colorScheme.onSurfaceVariant)
                LogAnalysisRange.entries.forEach { choice ->
                    Row(Modifier.fillMaxWidth().selectable(selected = range == choice, role = Role.RadioButton,
                        onClick = { range = choice }).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = range == choice, onClick = null)
                        Text(stringResource(choice.label), Modifier.padding(start = 12.dp))
                    }
                }
                Row(Modifier.fillMaxWidth().toggleable(value = reanalyze, role = Role.Checkbox,
                    onValueChange = { reanalyze = it }), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = reanalyze, onCheckedChange = null)
                    Text(stringResource(R.string.log_reanalyze), Modifier.weight(1f))
                }
                Text(stringResource(R.string.log_analysis_preview, allMatched.size, requests, matched.size - requests, unreadable))
                Text(stringResource(R.string.log_analysis_preview_note), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(onClick = { actions.startAnalysis(range, reanalyze); onDismiss() }, enabled = matched.isNotEmpty() &&
                !state.analysisBusy && !state.refreshing && !state.summariesLoading && !state.summariesReadFailed,
                modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.log_analysis_start)) }
            Spacer(Modifier.height(8.dp))
        }
    }
}
