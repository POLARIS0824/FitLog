package com.example.fitlog.feature.log

import com.example.fitlog.feature.log.components.RecordsLogHeader
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fitlog.data.settings.UiLook
import com.example.fitlog.ui.glass.LocalUiLook
import com.example.fitlog.ui.glass.glassSurface
import com.example.fitlog.ui.glass.LookSurface
import com.example.fitlog.ui.glass.glassPageSource
import com.example.fitlog.R
import com.example.fitlog.model.source.MarkdownFile
import com.example.fitlog.model.record.RecordKind
import com.example.fitlog.ui.components.FitLogNotice
import com.example.fitlog.ui.components.rememberFitLogTopAppBarBehavior
import com.example.fitlog.ui.theme.FitLogContainment
import com.example.fitlog.ui.theme.FitLogTheme
import com.example.fitlog.ui.theme.FitLogType
import com.example.fitlog.ui.theme.fitLogPagePadding

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
    val spacing = FitLogTheme.spacing
    val page = FitLogTheme.dimens.page
    val glass = LocalUiLook.current == UiLook.LIQUID_GLASS
    Column(Modifier.fillMaxSize().nestedScroll(behavior.nestedScrollConnection), horizontalAlignment = Alignment.CenterHorizontally) {
        RecordsLogHeader(markdown, state.refreshing, onMarkdown, { vm.refresh() }, onConnect, onManage, behavior)
        if (state.run.active && !state.run.batch) Surface(
            modifier = Modifier.widthIn(max = page.wideContentWidth).fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Row(Modifier.padding(horizontal = pagePadding), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
                Text(stringResource(R.string.log_batch_running, state.run.items.size, state.run.total), Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge)
                TextButton(onClick = vm::cancelAnalysis, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.ai_cancel_request)) }
            }
        }
        if (markdown) MarkdownLogContent(vm, onOpenMarkdown, onConnect, onManage,
            modifier = Modifier.weight(1f), onReview = onReview, onAiSettings = onAiSettings, showAppBar = false, listState = markdownListState)
        else LazyColumn(state = nativeListState, modifier = Modifier.widthIn(max = page.wideContentWidth).fillMaxWidth().weight(1f).glassPageSource(),
            contentPadding = PaddingValues(start = pagePadding, top = spacing.l, end = pagePadding, bottom = page.navigationBottomPadding),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            item {
                val empty = !error && !state.recordsLoading && records.isEmpty()
                Column(Modifier.fillMaxWidth().padding(bottom = spacing.m), verticalArrangement = Arrangement.spacedBy(spacing.m)) {
                    val intro: @Composable () -> Unit = {
                        Column(Modifier.fillMaxWidth().then(if (empty) Modifier.padding(if (glass) spacing.xl else spacing.l) else Modifier),
                            verticalArrangement = Arrangement.spacedBy(spacing.l)) {
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
                    // Liquid Glass keeps its own surface; the Material look shows a filled card only for the empty state.
                    when {
                        glass -> LookSurface(shape = if (empty) MaterialTheme.shapes.extraLarge else RectangleShape) { intro() }
                        empty -> Card(Modifier.fillMaxWidth()) { intro() }
                        else -> intro()
                    }
                    if (error) {
                        FitLogNotice(stringResource(R.string.record_read_failed), error = true)
                        TextButton(onClick = vm::retryRecords, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.record_retry)) }
                    }
                }
            }
            itemsIndexed(records, key = { _, record -> record.diary.id }) { index, record ->
                val shapes = ListItemDefaults.segmentedShapes(index = index, count = records.size)
                val names = record.sessions.flatMap { it.exercises }.map { it.exercise.rawName.trim() }.filter { it.isNotEmpty() }
                val validSets = record.sessions.flatMap { it.exercises }.sumOf { exercise ->
                    if (exercise.exercise.rawName.isBlank()) 0 else exercise.sets.count { (it.reps ?: 0) > 0 }
                }
                SegmentedListItem(
                    onClick = { onOpenNative(record.diary.id) },
                    shapes = shapes,
                    modifier = if (glass) Modifier.glassSurface(shapes.shape) else Modifier,
                    colors = if (glass) ListItemDefaults.segmentedColors(containerColor = Color.Transparent)
                        else FitLogContainment.pageListItemColors(),
                    supportingContent = {
                        Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                            Text(when {
                                record.diary.kind == RecordKind.REST -> stringResource(R.string.record_rest)
                                names.isEmpty() -> stringResource(R.string.record_incomplete)
                                else -> names.joinToString(stringResource(R.string.record_name_separator))
                            }, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                            if (record.diary.kind == RecordKind.TRAINING) {
                                if (validSets > 0) Text(pluralStringResource(R.plurals.record_ui_completed_sets, validSets, validSets),
                                    style = MaterialTheme.typography.labelLargeEmphasized,
                                    color = MaterialTheme.colorScheme.onSurface)
                            }
                            if (record.diary.kind == RecordKind.TRAINING && names.isNotEmpty() && validSets == 0) {
                                Text(stringResource(R.string.record_incomplete), style = MaterialTheme.typography.bodySmall)
                            }
                            Text(stringResource(R.string.record_source_native), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                ) { Text(record.diary.date, style = FitLogType.dataEmphasized) }
            }
        }
    }
}
