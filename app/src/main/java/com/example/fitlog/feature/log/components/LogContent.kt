package com.example.fitlog.feature.log.components

import com.example.fitlog.feature.log.LogActions
import com.example.fitlog.feature.log.LogDiaryStatus
import com.example.fitlog.feature.log.LogStatusFilter
import com.example.fitlog.feature.log.LogUiState

import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.example.fitlog.ui.glass.LocalUiLook
import com.example.fitlog.data.settings.UiLook
import com.example.fitlog.ui.glass.GlassSearchField
import com.example.fitlog.ui.glass.glassPageSource
import com.example.fitlog.R
import com.example.fitlog.data.settings.LogSortOrder
import com.example.fitlog.model.source.MarkdownFile
import com.example.fitlog.model.source.IndexedScan
import com.example.fitlog.model.source.IndexedSource
import com.example.fitlog.ui.components.FitLogConnectedButtonGroup
import com.example.fitlog.ui.components.FitLogNotice
import com.example.fitlog.ui.components.FitLogProgressFeedback
import com.example.fitlog.ui.components.FitLogTopAppBar
import com.example.fitlog.ui.components.rememberDelayedLoading
import com.example.fitlog.ui.components.rememberFitLogTopAppBarBehavior
import com.example.fitlog.ui.theme.FitLogContainment
import com.example.fitlog.ui.theme.FitLogTheme
import com.example.fitlog.ui.theme.fitLogPagePadding

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
    var toolsExpanded by rememberSaveable { mutableStateOf(false) }
    val showLoading = rememberDelayedLoading(state.refreshing || state.loading)
    val behavior = rememberFitLogTopAppBarBehavior()
    val pagePadding = fitLogPagePadding()
    val spacing = FitLogTheme.spacing
    val page = FitLogTheme.dimens.page
    val logDimens = FitLogTheme.dimens.log
    val files = state.visibleFiles
    Column(modifier.fillMaxSize().then(if (showAppBar) Modifier.nestedScroll(behavior.nestedScrollConnection) else Modifier), horizontalAlignment = Alignment.CenterHorizontally) {
        if (showAppBar) LogTopAppBar(true, state.refreshing, actions.refresh, onConnect, onManage, behavior)
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                state = listState,
                modifier = Modifier.widthIn(max = page.wideContentWidth).fillMaxSize().glassPageSource(),
                contentPadding = PaddingValues(start = pagePadding, top = spacing.l, end = pagePadding, bottom = page.navigationBottomPadding),
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                if (state.vault != null) item(key = "header") {
                    Column(Modifier.fillMaxWidth().padding(bottom = spacing.s), verticalArrangement = Arrangement.spacedBy(spacing.m)) {
                        if (LocalUiLook.current == UiLook.LIQUID_GLASS) GlassSearchField(searchFieldState)
                        else OutlinedTextField(
                            state = searchFieldState, lineLimits = TextFieldLineLimits.SingleLine,
                            label = { Text(stringResource(R.string.log_search)) },
                            leadingIcon = { Icon(painterResource(R.drawable.search_24px), null) },
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                if (searchFieldState.text.isNotEmpty()) IconButton(onClick = searchFieldState::clearText, shapes = IconButtonDefaults.shapes()) {
                                    Icon(painterResource(R.drawable.close_24px), stringResource(R.string.log_clear_search))
                                }
                            },
                        )
                        OutlinedToggleButton(checked = toolsExpanded, onCheckedChange = { toolsExpanded = it }) {
                            Text(stringResource(if (toolsExpanded) R.string.log_hide_tools else R.string.log_show_tools,
                                stringResource(state.statusFilter.label)))
                        }
                        if (toolsExpanded) {
                            LogAnalysisControls(state, actions, onReview)
                            FitLogConnectedButtonGroup(LogSortOrder.entries, state.sort,
                                label = { stringResource(if (it == LogSortOrder.Ascending) R.string.log_sort_ascending else R.string.log_sort_descending) },
                                onSelect = actions.changeSort, enabled = !state.sortBusy)
                            FilterChip(selected = state.showMissing, onClick = actions.toggleMissing,
                                label = { Text(stringResource(R.string.index_show_missing)) })
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                            Text(stringResource(when {
                                showLoading -> R.string.index_scanning
                                state.scanStatus == IndexedScan.COMPLETE -> R.string.index_complete
                                state.scanStatus == IndexedScan.PARTIAL -> R.string.index_partial
                                state.scanStatus == IndexedScan.FAILED -> R.string.index_failed
                                else -> R.string.index_not_scanned
                            }), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(spacing.l), verticalArrangement = Arrangement.spacedBy(spacing.l)) {
                                Icon(painterResource(R.drawable.folder_open_24px), null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(logDimens.emptyIllustrationSize))
                                Text(stringResource(when {
                                    state.vault == null -> R.string.log_no_vault
                                    state.files.isEmpty() -> R.string.log_empty
                                    else -> R.string.log_no_matches
                                }), style = MaterialTheme.typography.headlineSmallEmphasized)
                                if (state.vault == null) Button(onClick = onConnect,
                                    shapes = ButtonDefaults.shapesFor(ButtonDefaults.MediumContainerHeight),
                                    modifier = Modifier.fillMaxWidth().heightIn(min = ButtonDefaults.MediumContainerHeight),
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
                        colors = FitLogContainment.pageListItemColors(),
                        leadingContent = {
                            Icon(painterResource(R.drawable.list_alt_24px), null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                        supportingContent = { Column {
                            file.path.substringBeforeLast('/', "").takeIf { it.isNotBlank() }?.let { parent ->
                                Text(parent)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                                Icon(painterResource(if (work == LogDiaryStatus.CONFIRMED) R.drawable.check_24px else R.drawable.list_alt_24px),
                                    null, modifier = Modifier.size(logDimens.inlineStatusIconSize))
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
                Modifier.align(Alignment.TopCenter).widthIn(max = page.wideContentWidth).fillMaxWidth().padding(horizontal = pagePadding))
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
    FitLogTopAppBar(stringResource(R.string.log_title), scrollBehavior, editorial = true,
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
