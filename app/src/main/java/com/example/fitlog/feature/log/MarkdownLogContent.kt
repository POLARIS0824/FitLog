package com.example.fitlog.feature.log

import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fitlog.R
import com.example.fitlog.data.settings.LogSortOrder
import com.example.fitlog.data.vault.MarkdownFile
import com.example.fitlog.model.source.IndexedScan
import com.example.fitlog.model.source.IndexedSource
import com.example.fitlog.ui.components.FitLogDimens
import com.example.fitlog.ui.components.FitLogNotice
import com.example.fitlog.ui.components.FitLogProgressFeedback
import com.example.fitlog.ui.components.FitLogTopAppBar
import com.example.fitlog.ui.components.fitLogPagePadding
import com.example.fitlog.ui.components.rememberDelayedLoading
import com.example.fitlog.ui.components.rememberFitLogTopAppBarBehavior

@Composable
internal fun MarkdownLogContent(
    vm: LogViewModel,
    onOpen: (String, MarkdownFile) -> Unit,
    onConnect: () -> Unit,
    onManage: () -> Unit,
    modifier: Modifier = Modifier,
    onReview: () -> Unit = {},
    onAiSettings: () -> Unit = {},
    showAppBar: Boolean = true,
    listState: LazyListState = rememberLazyListState(),
) {

    val state by vm.uiState.collectAsStateWithLifecycle()
    LogContent(
        state = state,
        actions = LogActions(
            refresh = { vm.refresh() },
            changeSort = { vm.changeSort(it) },
            toggleMissing = { vm.toggleMissing() },
            filter = vm::filter, startAnalysis = vm::startAnalysis, cancelAnalysis = vm::cancelAnalysis,
            openAiSettings = onAiSettings,
        ),
        onOpen = onOpen, onConnect = onConnect,
        onManage = onManage, modifier = modifier, onReview = onReview, showAppBar = showAppBar, listState = listState,
        searchFieldState = vm.searchFieldState,
    )
}

@Composable
internal fun LogContent(
    state: LogUiState, actions: LogActions,
    onOpen: (String, MarkdownFile) -> Unit,
    onConnect: () -> Unit,
    onManage: () -> Unit,
    modifier: Modifier = Modifier,
    onReview: () -> Unit = {},
    showAppBar: Boolean = true,
    listState: LazyListState = rememberLazyListState(),
    searchFieldState: TextFieldState = rememberTextFieldState(initialText = state.query),
) {
    var sortExpanded by remember { mutableStateOf(false) }
    var toolsExpanded by rememberSaveable { mutableStateOf(false) }
    val showLoading = rememberDelayedLoading(state.refreshing || state.loading)
    val behavior = rememberFitLogTopAppBarBehavior(expanded = true)
    val pagePadding = fitLogPagePadding()
    val files = state.visibleFiles
    Column(modifier.fillMaxSize().then(if (showAppBar) Modifier.nestedScroll(behavior.nestedScrollConnection) else Modifier), horizontalAlignment = Alignment.CenterHorizontally) {
        if (showAppBar) LogTopAppBar(true, state.refreshing, actions.refresh, onConnect, onManage, behavior)
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                state = listState,
                modifier = Modifier.widthIn(max = FitLogDimens.WideContentWidth).fillMaxSize(),
                contentPadding = PaddingValues(start = pagePadding, top = 16.dp, end = pagePadding, bottom = FitLogDimens.NavigationBottomPadding),
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                item(key = "header") {
                    OutlinedTextField(
                        state = searchFieldState, lineLimits = TextFieldLineLimits.SingleLine,
                        label = { Text(stringResource(R.string.log_search)) },
                        shape = CircleShape,
                        leadingIcon = { Icon(painterResource(R.drawable.search_24px), null) },
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            if (searchFieldState.text.isNotEmpty()) IconButton(onClick = searchFieldState::clearText, shapes = IconButtonDefaults.shapes()) {
                                Icon(painterResource(R.drawable.close_24px), stringResource(R.string.log_clear_search))
                            }
                        },
                    )
                    TextButton(onClick = { toolsExpanded = !toolsExpanded }, shapes = ButtonDefaults.shapes()) {
                        Text(stringResource(if (toolsExpanded) R.string.log_hide_tools else R.string.log_show_tools,
                            stringResource(state.statusFilter.label)))
                    }
                    if (toolsExpanded) {
                    LogAnalysisControls(state, actions, onReview)
                    Spacer(Modifier.height(12.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box {
                            OutlinedButton(onClick = { sortExpanded = true }, enabled = !state.sortBusy, shapes = ButtonDefaults.shapes()) {
                                Text(stringResource(if (state.sort == LogSortOrder.Ascending) R.string.log_sort_ascending else R.string.log_sort_descending))
                            }
                            DropdownMenu(expanded = sortExpanded, onDismissRequest = { sortExpanded = false }) {
                                LogSortOrder.entries.forEach { order ->
                                    DropdownMenuItem(
                                        text = { Text(stringResource(if (order == LogSortOrder.Ascending) R.string.log_sort_ascending else R.string.log_sort_descending)) },
                                        onClick = { sortExpanded = false; actions.changeSort(order) },
                                    )
                                }
                            }
                        }
                        FilterChip(selected = state.showMissing, onClick = actions.toggleMissing,
                            label = { Text(stringResource(R.string.index_show_missing)) })
                    }
                    }
                    Text(stringResource(when {
                        showLoading && state.vault != null -> R.string.index_scanning
                        state.scanStatus == IndexedScan.COMPLETE -> R.string.index_complete
                        state.scanStatus == IndexedScan.PARTIAL -> R.string.index_partial
                        state.scanStatus == IndexedScan.FAILED -> R.string.index_failed
                        else -> R.string.index_not_scanned
                    }), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp))
                    androidx.compose.animation.AnimatedVisibility(
                        visible = state.files.isNotEmpty() &&
                            (showLoading || state.error != null || state.scanStatus != IndexedScan.COMPLETE),
                        enter = expandVertically(
                            animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                            expandFrom = Alignment.Top,
                        ) + fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()),
                        exit = shrinkVertically(
                            animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                            shrinkTowards = Alignment.Top,
                        ) + fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
                    ) {
                        Text(stringResource(R.string.index_cached), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (state.summariesReadFailed) item(key = "analysis-error") {
                    FitLogNotice(stringResource(R.string.detail_analysis_failed), error = true)
                }
                if (state.run.batch && state.run.total > 0) item(key = "analysis-run") {
                    AnalysisRunFeedback(state.run, actions.cancelAnalysis,
                        { actions.filter(LogStatusFilter.FAILED) }, actions.openAiSettings)
                }
                state.error?.let { item { FitLogNotice(stringResource(it), error = true) } }
                state.sortError?.let { item { FitLogNotice(stringResource(it), error = true) } }
                if (state.partial) item { FitLogNotice(stringResource(R.string.log_partial)) }
                if (!state.loading && state.error == null && (state.vault == null || state.files.isEmpty() || files.isEmpty())) {
                    item(key = "empty") {
                        Surface(shape = MaterialTheme.shapes.extraLarge,
                            color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                Icon(painterResource(R.drawable.folder_open_24px), null, tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(48.dp))
                                Text(stringResource(when {
                                    state.vault == null -> R.string.log_no_vault
                                    state.files.isEmpty() -> R.string.log_empty
                                    else -> R.string.log_no_matches
                                }), style = MaterialTheme.typography.titleMedium)
                                if (state.vault == null) Button(onClick = onConnect,
                                    shapes = ButtonDefaults.shapesFor(ButtonDefaults.MediumContainerHeight),
                                    modifier = Modifier.heightIn(min = ButtonDefaults.MediumContainerHeight),
                                    contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight)) {
                                    Text(stringResource(R.string.log_connect), style = MaterialTheme.typography.titleMediumEmphasized)
                                }
                            }
                        }
                    }
                }
                itemsIndexed(files, key = { _, file -> file.uri }) { index, file ->
                    val status = state.sources.firstOrNull { it.uri == file.uri }?.status
                    val work = state.statuses[file.path] ?: LogDiaryStatus.UNPARSED
                    SegmentedListItem(
                        onClick = { state.vault?.let { onOpen(it, file) } },
                        enabled = status != IndexedSource.MISSING,
                        shapes = ListItemDefaults.segmentedShapes(index = index, count = files.size),
                        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        leadingContent = {
                            Icon(painterResource(R.drawable.list_alt_24px), null, modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                        supportingContent = { Column {
                            file.path.substringBeforeLast('/', "").takeIf { it.isNotBlank() }?.let { parent ->
                                Text(parent)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(painterResource(if (work == LogDiaryStatus.CONFIRMED) R.drawable.check_24px else R.drawable.list_alt_24px),
                                    null, modifier = Modifier.size(16.dp))
                                Text(stringResource(work.label), color = if (work in setOf(LogDiaryStatus.FAILED, LogDiaryStatus.READ_FAILED))
                                    MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            state.summaries[file.path]?.let { summary ->
                                if (summary.confirmed != null && work != LogDiaryStatus.CONFIRMED)
                                    Text(stringResource(R.string.log_has_confirmation), style = MaterialTheme.typography.labelSmall)
                            }
                        } },
                    ) { Text(file.name, style = MaterialTheme.typography.titleMediumEmphasized) }
                }
            }
            FitLogProgressFeedback(showLoading,
                Modifier.align(Alignment.TopCenter).widthIn(max = FitLogDimens.WideContentWidth).fillMaxWidth().padding(horizontal = pagePadding))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LogTopAppBar(
    markdown: Boolean,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onConnect: () -> Unit,
    onManage: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    FitLogTopAppBar(stringResource(R.string.log_title), scrollBehavior, expanded = true,
        actions = {
            if (markdown) {
                FilledTonalIconButton(onClick = onRefresh, enabled = !refreshing, shapes = IconButtonDefaults.shapes()) {
                    Icon(painterResource(R.drawable.refresh_24px), stringResource(R.string.log_refresh))
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }, shapes = IconButtonDefaults.shapes()) {
                        Icon(painterResource(R.drawable.more_vert_24px), stringResource(R.string.log_options))
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.log_connect)) },
                            onClick = { menuExpanded = false; onConnect() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.vault_management_title)) },
                            onClick = { menuExpanded = false; onManage() })
                    }
                }
            }
        },
    )
}



internal data class LogActions(
    val refresh: () -> Unit = {},
    val changeSort: (LogSortOrder) -> Unit = {},
    val toggleMissing: () -> Unit = {},
    val filter: (LogStatusFilter) -> Unit = {},
    val startAnalysis: (LogAnalysisRange, Boolean) -> Unit = { _, _ -> },
    val cancelAnalysis: () -> Unit = {},
    val openAiSettings: () -> Unit = {},
)
