package com.example.fitlog.feature.log

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fitlog.data.settings.UiLook
import com.example.fitlog.ui.glass.LocalUiLook
import androidx.compose.ui.graphics.Color
import com.example.fitlog.ui.glass.glassSurface
import com.example.fitlog.ui.glass.LookSurface
import com.example.fitlog.ui.glass.glassPageSource
import com.example.fitlog.R
import com.example.fitlog.data.vault.MarkdownFile
import com.example.fitlog.model.record.RecordKind
import com.example.fitlog.ui.components.FitLogConnectedButtonGroup
import com.example.fitlog.ui.components.FitLogDimens
import com.example.fitlog.ui.components.FitLogNotice
import com.example.fitlog.ui.components.fitLogPagePadding
import com.example.fitlog.ui.components.rememberFitLogTopAppBarBehavior

@Composable
internal fun LogScreen(vm: LogViewModel,
    onOpenNative: (String?) -> Unit, onOpenMarkdown: (String, MarkdownFile) -> Unit,
    onConnect: () -> Unit, onManage: () -> Unit, onReview: () -> Unit, onAiSettings: () -> Unit,
    markdown: Boolean, onMarkdown: (Boolean) -> Unit) {
    val nativeListState = rememberLazyListState()
    val markdownListState = rememberLazyListState()
    val state by vm.uiState.collectAsStateWithLifecycle()
    val records = state.nativeRecords
    val error = state.recordsReadFailed
    val behavior = rememberFitLogTopAppBarBehavior()
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
            modifier = Modifier.weight(1f), onReview = onReview, onAiSettings = onAiSettings, showAppBar = false, listState = markdownListState)
        else LazyColumn(state = nativeListState, modifier = Modifier.widthIn(max = FitLogDimens.WideContentWidth).fillMaxWidth().weight(1f).glassPageSource(),
            contentPadding = PaddingValues(start = pagePadding, top = 16.dp, end = pagePadding, bottom = FitLogDimens.NavigationBottomPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                val empty = !error && !state.recordsLoading && records.isEmpty()
                LookSurface(shape = if (empty) MaterialTheme.shapes.extraLarge else RectangleShape,
                    color = if (empty) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surface) {
                    Column(Modifier.fillMaxWidth().then(if (empty) Modifier.padding(24.dp) else Modifier),
                        verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (empty) Text(stringResource(R.string.record_no_records),
                            style = MaterialTheme.typography.headlineSmallEmphasized)
                        Text(stringResource(R.string.record_ui_list_subtitle), style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = { onOpenNative(null) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = ButtonDefaults.MediumContainerHeight),
                            shapes = ButtonDefaults.shapesFor(ButtonDefaults.MediumContainerHeight),
                            contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight)) {
                            Icon(painterResource(R.drawable.add_24px), null, Modifier.size(ButtonDefaults.iconSizeFor(ButtonDefaults.MediumContainerHeight)))
                            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                            Text(stringResource(R.string.record_new), style = MaterialTheme.typography.titleMediumEmphasized)
                        }
                    }
                }
            }
            if (error) item {
                FitLogNotice(stringResource(R.string.record_read_failed), error = true)
                TextButton(onClick = vm::retryRecords, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.record_retry)) }
            }
            items(records, key = { it.diary.id }) { record ->
                Surface(onClick = { onOpenNative(record.diary.id) }, shape = MaterialTheme.shapes.large,
                    modifier = if (LocalUiLook.current == UiLook.LIQUID_GLASS)
                        Modifier.glassSurface(MaterialTheme.shapes.large) else Modifier,
                    color = if (LocalUiLook.current == UiLook.LIQUID_GLASS)
                        Color.Transparent else MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(record.diary.date, style = MaterialTheme.typography.titleLargeEmphasized)
                        val names = record.sessions.flatMap { it.exercises }.map { it.exercise.rawName.trim() }.filter { it.isNotEmpty() }
                        val validSets = record.sessions.flatMap { it.exercises }.sumOf { exercise ->
                            if (exercise.exercise.rawName.isBlank()) 0 else exercise.sets.count { (it.reps ?: 0) > 0 }
                        }
                        Text(when {
                            record.diary.kind == RecordKind.REST -> stringResource(R.string.record_rest)
                            names.isEmpty() -> stringResource(R.string.record_incomplete)
                            else -> names.joinToString(stringResource(R.string.record_name_separator))
                        }, style = MaterialTheme.typography.bodyLarge)
                        if (record.diary.kind == RecordKind.TRAINING) {
                            if (validSets > 0) Text(pluralStringResource(R.plurals.record_ui_completed_sets, validSets, validSets),
                                style = MaterialTheme.typography.labelLargeEmphasized,
                                color = MaterialTheme.colorScheme.primary)
                        }
                        if (record.diary.kind == RecordKind.TRAINING && names.isNotEmpty() && validSets == 0) {
                            Text(stringResource(R.string.record_incomplete), style = MaterialTheme.typography.bodySmall)
                        }
                        Text(stringResource(R.string.record_source_native), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordsLogHeader(markdown: Boolean, refreshing: Boolean, onMarkdown: (Boolean) -> Unit,
    onRefresh: () -> Unit, onConnect: () -> Unit, onManage: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior = rememberFitLogTopAppBarBehavior()) {
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
