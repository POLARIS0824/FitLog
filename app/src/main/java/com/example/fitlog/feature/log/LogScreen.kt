package com.example.fitlog.feature.log

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fitlog.R
import com.example.fitlog.data.vault.MarkdownFile
import com.example.fitlog.model.record.RecordKind
import com.example.fitlog.ui.components.FitLogConnectedButtonGroup
import com.example.fitlog.ui.components.FitLogDimens
import com.example.fitlog.ui.components.FitLogNotice
import com.example.fitlog.ui.components.fitLogPagePadding
import com.example.fitlog.ui.components.rememberFitLogTopAppBarBehavior
import com.example.fitlog.ui.preview.FitLogPreview

@Composable
internal fun LogScreen(vm: LogViewModel,
    onOpenNative: (String?) -> Unit, onOpenMarkdown: (String, MarkdownFile) -> Unit,
    onConnect: () -> Unit, onManage: () -> Unit, onReview: () -> Unit, onAiSettings: () -> Unit,
    markdown: Boolean, onMarkdown: (Boolean) -> Unit) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val records = state.nativeRecords
    val error = state.recordsReadFailed
    val behavior = rememberFitLogTopAppBarBehavior(expanded = true)
    val pagePadding = fitLogPagePadding()
    Column(Modifier.fillMaxSize().nestedScroll(behavior.nestedScrollConnection), horizontalAlignment = Alignment.CenterHorizontally) {
        RecordsLogHeader(markdown, state.refreshing, onMarkdown, { vm.refresh() }, onConnect, onManage, behavior)
        if (state.run.active && !state.run.batch) Surface(
            modifier = Modifier.widthIn(max = FitLogDimens.WideContentWidth).fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Row(Modifier.padding(horizontal = pagePadding), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.log_batch_running, state.run.items.size, state.run.total), Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge)
                TextButton(onClick = vm::cancelAnalysis, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.ai_cancel_request)) }
            }
        }
        if (markdown) MarkdownLogContent(vm, onOpenMarkdown, onConnect, onManage,
            modifier = Modifier.weight(1f), onReview = onReview, onAiSettings = onAiSettings, showAppBar = false)
        else LazyColumn(Modifier.widthIn(max = FitLogDimens.WideContentWidth).fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(start = pagePadding, top = 16.dp, end = pagePadding, bottom = FitLogDimens.NavigationBottomPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text(stringResource(R.string.record_ui_list_subtitle), style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item {
                Button(onClick = { onOpenNative(null) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = ButtonDefaults.MediumContainerHeight),
                    shapes = ButtonDefaults.shapesFor(ButtonDefaults.MediumContainerHeight),
                    contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight)) {
                    Icon(painterResource(R.drawable.add_24px), null, Modifier.size(ButtonDefaults.iconSizeFor(ButtonDefaults.MediumContainerHeight)))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.record_new), style = MaterialTheme.typography.titleMediumEmphasized)
                }
            }
            if (error) item {
                FitLogNotice(stringResource(R.string.record_read_failed), error = true)
                TextButton(onClick = vm::retryRecords, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.record_retry)) }
            }
            if (!error && !state.recordsLoading && records.isEmpty()) item { FitLogNotice(stringResource(R.string.record_no_records)) }
            items(records, key = { it.diary.id }) { record ->
                Surface(onClick = { onOpenNative(record.diary.id) }, shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(record.diary.date, style = MaterialTheme.typography.titleLargeEmphasized)
                        Text(stringResource(R.string.record_source_native), style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val names = record.sessions.flatMap { it.exercises }.map { it.exercise.rawName.trim() }.filter { it.isNotEmpty() }
                        Text(when {
                            record.diary.kind == RecordKind.REST -> stringResource(R.string.record_rest)
                            names.isEmpty() -> stringResource(R.string.record_incomplete)
                            else -> names.joinToString(stringResource(R.string.record_name_separator))
                        }, style = MaterialTheme.typography.bodyLarge)
                        if (record.diary.kind == RecordKind.TRAINING) {
                            val validSets = record.sessions.flatMap { it.exercises }.sumOf { exercise ->
                                if (exercise.exercise.rawName.isBlank()) 0 else exercise.sets.count { (it.reps ?: 0) > 0 }
                            }
                            if (validSets > 0) Text(pluralStringResource(R.plurals.record_ui_completed_sets, validSets, validSets),
                                style = MaterialTheme.typography.labelLargeEmphasized,
                                color = MaterialTheme.colorScheme.primary)
                        }
                        if (record.diary.kind == RecordKind.TRAINING && record.sessions.flatMap { it.exercises }
                                .none { it.exercise.rawName.isNotBlank() && it.sets.any { set -> (set.reps ?: 0) > 0 } }) {
                            Text(stringResource(R.string.record_incomplete), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordsLogHeader(markdown: Boolean, refreshing: Boolean, onMarkdown: (Boolean) -> Unit,
    onRefresh: () -> Unit, onConnect: () -> Unit, onManage: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior = rememberFitLogTopAppBarBehavior(expanded = true)) {
    LogTopAppBar(markdown, refreshing, onRefresh, onConnect, onManage, scrollBehavior)
    RecordSourceSelector(markdown, onMarkdown)
}

@Composable
private fun RecordSourceSelector(markdown: Boolean, onMarkdown: (Boolean) -> Unit) {
    FitLogConnectedButtonGroup(listOf(false, true), markdown,
        label = { stringResource(if (it) R.string.record_markdown else R.string.record_native) },
        onSelect = onMarkdown,
        modifier = Modifier.widthIn(max = FitLogDimens.WideContentWidth)
            .padding(horizontal = fitLogPagePadding(), vertical = 8.dp))
}

@Preview(name = "Record sources", widthDp = 412, heightDp = 400, locale = "en")
@Composable
private fun RecordSourcesReviewPreview() {
    FitLogPreview {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            RecordSourceSelector(markdown = false, onMarkdown = {})
            RecordSourceSelector(markdown = true, onMarkdown = {})
        }
    }
}

@Preview(name = "Log app bar · Local records", widthDp = 412, heightDp = 240, locale = "en")
@Composable
private fun RecordsLogHeaderReviewPreview() {
    FitLogPreview { Column { RecordsLogHeader(false, false, {}, {}, {}, {}) } }
}

@Preview(name = "Log app bar · Markdown", widthDp = 412, heightDp = 240, locale = "en")
@Composable
private fun RecordsLogMarkdownHeaderReviewPreview() {
    FitLogPreview { Column { RecordsLogHeader(true, false, {}, {}, {}, {}) } }
}

@Preview(name = "Record sources · Large text", widthDp = 320, heightDp = 560, fontScale = 2f, locale = "zh")
@Composable
private fun RecordSourcesLargeTextReviewPreview() {
    RecordSourcesReviewPreview()
}
