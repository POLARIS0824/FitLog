package com.example.fitlog.log

import com.example.fitlog.ui.preview.FitLogPreviews
import com.example.fitlog.ui.preview.FitLogPreview
import com.example.fitlog.ui.preview.PreviewDiary
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.fitlog.R
import com.example.fitlog.data.vault.MarkdownFile
import com.example.fitlog.data.index.IndexedScan
import com.example.fitlog.data.index.IndexedSource
import com.example.fitlog.ui.components.FitLogNotice
import com.example.fitlog.ui.components.FitLogPageHeader

@Composable
fun LogScreen(
    vm: LogViewModel,
    onOpen: (String, MarkdownFile) -> Unit,
    onConnect: () -> Unit,
    onSettings: (String) -> Unit,
    onRecovery: () -> Unit,
    onManage: () -> Unit,
    modifier: Modifier = Modifier,
) {

    LogContent(
        state = LogUiState(
            visibleFiles = vm.visibleFiles,
            vault = vm.vault,
            files = vm.files,
            query = vm.query,
            sort = vm.sort,
            sortBusy = vm.sortBusy,
            sortError = vm.sortError,
            loading = vm.loading,
            refreshing = vm.refreshing,
            partial = vm.partial,
            error = vm.error,
            scanStatus = vm.scanStatus,
            showMissing = vm.showMissing,
            sources = vm.sources,
        ),
        actions = LogActions(
            refresh = { vm.refresh() },
            search = { vm.search(it) },
            changeSort = { vm.changeSort(it) },
            toggleMissing = { vm.toggleMissing() },
        ),
        onOpen = onOpen, onConnect = onConnect, onSettings = onSettings,
        onRecovery = onRecovery, onManage = onManage, modifier = modifier,
    )
}

@Composable
private fun LogContent(
    state: LogUiState, actions: LogActions,
    onOpen: (String, MarkdownFile) -> Unit,
    onConnect: () -> Unit,
    onSettings: (String) -> Unit,
    onRecovery: () -> Unit,
    onManage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var sortExpanded by remember { mutableStateOf(false) }
    val files = state.visibleFiles
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.widthIn(max = 840.dp).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            item(key = "header") {
                FitLogPageHeader(stringResource(R.string.log_title), stringResource(R.string.log_description)) {
                    FilledTonalIconButton(onClick = actions.refresh, enabled = !state.refreshing,
                        shapes = IconButtonDefaults.shapes()) {
                        Icon(painterResource(R.drawable.refresh_24px), stringResource(R.string.log_refresh))
                    }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(painterResource(R.drawable.more_vert_24px), stringResource(R.string.log_options))
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.log_connect)) },
                                onClick = { menuExpanded = false; onConnect() })
                            DropdownMenuItem(text = { Text(stringResource(R.string.vault_management_title)) },
                                onClick = { menuExpanded = false; onManage() })
                            DropdownMenuItem(text = { Text(stringResource(R.string.diary_settings_title)) }, enabled = state.vault != null,
                                onClick = { menuExpanded = false; state.vault?.let(onSettings) })
                            DropdownMenuItem(text = { Text(stringResource(R.string.recovery_title)) },
                                onClick = { menuExpanded = false; onRecovery() })
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
                OutlinedTextField(
                    value = state.query, onValueChange = actions.search, singleLine = true,
                    placeholder = { Text(stringResource(R.string.log_search)) },
                    shape = CircleShape,
                    leadingIcon = { Icon(painterResource(R.drawable.search_24px), null) },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        if (state.query.isNotEmpty()) IconButton(onClick = { actions.search("") }) {
                            Icon(painterResource(R.drawable.close_24px), stringResource(R.string.log_clear_search))
                        }
                    },
                )
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box {
                        OutlinedButton(onClick = { sortExpanded = true }, enabled = !state.sortBusy) {
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
                if (state.refreshing) LinearWavyProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp))
                Text(stringResource(when {
                    state.refreshing && state.vault != null -> R.string.index_scanning
                    state.scanStatus == IndexedScan.COMPLETE -> R.string.index_complete
                    state.scanStatus == IndexedScan.PARTIAL -> R.string.index_partial
                    state.scanStatus == IndexedScan.FAILED -> R.string.index_failed
                    else -> R.string.index_not_scanned
                }), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp))
                if (state.files.isNotEmpty() && (state.refreshing || state.error != null || state.scanStatus != IndexedScan.COMPLETE)) {
                    Text(stringResource(R.string.index_cached), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
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
                            if (state.vault == null) FilledTonalButton(onClick = onConnect) {
                                Text(stringResource(R.string.log_connect))
                            }
                        }
                    }
                }
            }
            itemsIndexed(files, key = { _, file -> file.uri }) { index, file ->
                val status = state.sources.firstOrNull { it.uri == file.uri }?.status
                SegmentedListItem(
                    onClick = { state.vault?.let { onOpen(it, file) } },
                    enabled = status != IndexedSource.MISSING,
                    shapes = ListItemDefaults.segmentedShapes(index = index, count = files.size),
                    leadingContent = {
                        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.secondaryContainer) {
                            Icon(painterResource(R.drawable.list_alt_24px), null, modifier = Modifier.padding(12.dp).size(24.dp),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    },
                    supportingContent = { Column {
                        Text(file.path, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (status == IndexedSource.MISSING || status == IndexedSource.READ_FAILED) Text(stringResource(
                            if (status == IndexedSource.MISSING) R.string.index_source_missing else R.string.index_source_unreadable))
                    } },
                ) { Text(file.name, style = MaterialTheme.typography.titleMediumEmphasized,
                    maxLines = 2, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}

private data class LogUiState(
    val visibleFiles: List<MarkdownFile> = emptyList(),
    val vault: String? = null,
    val files: List<MarkdownFile> = emptyList(),
    val query: String = "",
    val sort: LogSortOrder = LogSortOrder.Descending,
    val sortBusy: Boolean = false,
    val sortError: Int? = null,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val partial: Boolean = false,
    val error: Int? = null,
    val scanStatus: String? = null,
    val showMissing: Boolean = false,
    val sources: List<IndexedSource> = emptyList(),
)

private data class LogActions(
    val refresh: () -> Unit = {},
    val search: (String) -> Unit = {},
    val changeSort: (LogSortOrder) -> Unit = {},
    val toggleMissing: () -> Unit = {},
)

@FitLogPreviews
@Composable
private fun LogPreview() {
    val files = listOf(PreviewDiary.file)
    FitLogPreview {
        LogContent(LogUiState(vault = PreviewDiary.VAULT_URI,
            files = files, visibleFiles = files, scanStatus = IndexedScan.COMPLETE),
            LogActions(), { _, _ -> }, {}, {}, {}, {})
    }
}

@FitLogPreviews
@Composable
private fun LogEmptyPreview() {
    FitLogPreview { LogContent(LogUiState(), LogActions(), { _, _ -> }, {}, {}, {}, {}) }
}
