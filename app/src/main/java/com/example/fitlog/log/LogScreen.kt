package com.example.fitlog.log

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
    var menuExpanded by remember { mutableStateOf(false) }
    var sortExpanded by remember { mutableStateOf(false) }
    val files = vm.visibleFiles
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.widthIn(max = 840.dp).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            item(key = "header") {
                FitLogPageHeader(stringResource(R.string.log_title), stringResource(R.string.log_description)) {
                    FilledTonalIconButton(onClick = vm::refresh, enabled = !vm.refreshing,
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
                            DropdownMenuItem(text = { Text(stringResource(R.string.diary_settings_title)) }, enabled = vm.vault != null,
                                onClick = { menuExpanded = false; vm.vault?.let(onSettings) })
                            DropdownMenuItem(text = { Text(stringResource(R.string.recovery_title)) },
                                onClick = { menuExpanded = false; onRecovery() })
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
                OutlinedTextField(
                    value = vm.query, onValueChange = vm::search, singleLine = true,
                    placeholder = { Text(stringResource(R.string.log_search)) },
                    shape = CircleShape,
                    leadingIcon = { Icon(painterResource(R.drawable.search_24px), null) },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        if (vm.query.isNotEmpty()) IconButton(onClick = { vm.search("") }) {
                            Icon(painterResource(R.drawable.close_24px), stringResource(R.string.log_clear_search))
                        }
                    },
                )
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box {
                        OutlinedButton(onClick = { sortExpanded = true }, enabled = !vm.sortBusy) {
                            Text(stringResource(if (vm.sort == LogSortOrder.Ascending) R.string.log_sort_ascending else R.string.log_sort_descending))
                        }
                        DropdownMenu(expanded = sortExpanded, onDismissRequest = { sortExpanded = false }) {
                            LogSortOrder.entries.forEach { order ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(if (order == LogSortOrder.Ascending) R.string.log_sort_ascending else R.string.log_sort_descending)) },
                                    onClick = { sortExpanded = false; vm.changeSort(order) },
                                )
                            }
                        }
                    }
                    FilterChip(selected = vm.showMissing, onClick = vm::toggleMissing,
                        label = { Text(stringResource(R.string.index_show_missing)) })
                }
                if (vm.refreshing) LinearWavyProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp))
                Text(stringResource(when {
                    vm.refreshing && vm.vault != null -> R.string.index_scanning
                    vm.scanStatus == IndexedScan.COMPLETE -> R.string.index_complete
                    vm.scanStatus == IndexedScan.PARTIAL -> R.string.index_partial
                    vm.scanStatus == IndexedScan.FAILED -> R.string.index_failed
                    else -> R.string.index_not_scanned
                }), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp))
                if (vm.files.isNotEmpty() && (vm.refreshing || vm.error != null || vm.scanStatus != IndexedScan.COMPLETE)) {
                    Text(stringResource(R.string.index_cached), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            vm.error?.let { item { FitLogNotice(stringResource(it), error = true) } }
            vm.sortError?.let { item { FitLogNotice(stringResource(it), error = true) } }
            if (vm.partial) item { FitLogNotice(stringResource(R.string.log_partial)) }
            if (!vm.loading && vm.error == null && (vm.vault == null || vm.files.isEmpty() || files.isEmpty())) {
                item(key = "empty") {
                    Surface(shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Icon(painterResource(R.drawable.folder_open_24px), null, tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(48.dp))
                            Text(stringResource(when {
                                vm.vault == null -> R.string.log_no_vault
                                vm.files.isEmpty() -> R.string.log_empty
                                else -> R.string.log_no_matches
                            }), style = MaterialTheme.typography.titleMedium)
                            if (vm.vault == null) FilledTonalButton(onClick = onConnect) {
                                Text(stringResource(R.string.log_connect))
                            }
                        }
                    }
                }
            }
            itemsIndexed(files, key = { _, file -> file.uri }) { index, file ->
                val status = vm.sources.firstOrNull { it.uri == file.uri }?.status
                SegmentedListItem(
                    onClick = { vm.vault?.let { onOpen(it, file) } },
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
