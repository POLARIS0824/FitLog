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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.fitlog.R
import com.example.fitlog.data.vault.MarkdownFile

@Composable
fun LogScreen(
    vm: LogViewModel,
    onOpen: (String, MarkdownFile) -> Unit,
    onConnect: () -> Unit,
    onSettings: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }
    var sortExpanded by remember { mutableStateOf(false) }
    val files = vm.visibleFiles
    Column(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.log_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            Box {
                TextButton(onClick = { sortExpanded = true }, enabled = !vm.sortBusy) {
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
        }
        OutlinedTextField(
            value = vm.query, onValueChange = vm::search, singleLine = true,
            label = { Text(stringResource(R.string.log_search)) }, modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                if (vm.query.isNotEmpty()) IconButton(onClick = { vm.search("") }) {
                    Icon(painterResource(R.drawable.close_24px), stringResource(R.string.log_clear_search))
                }
            },
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = vm::refresh, enabled = !vm.loading) { Text(stringResource(R.string.log_refresh)) }
            TextButton(onClick = onConnect) { Text(stringResource(R.string.log_connect)) }
            TextButton(onClick = { vm.vault?.let(onSettings) }, enabled = vm.vault != null) { Text(stringResource(R.string.diary_settings_title)) }
        }
        if (vm.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        vm.error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
        vm.sortError?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
        if (vm.partial) Text(stringResource(R.string.log_partial))
        if (!vm.loading && vm.error == null) {
            when {
                vm.vault == null -> Text(stringResource(R.string.log_no_vault))
                vm.files.isEmpty() -> Text(stringResource(R.string.log_empty))
                files.isEmpty() -> Text(stringResource(R.string.log_no_matches))
            }
        }
        LazyColumn(
            modifier = Modifier.weight(1f), contentPadding = PaddingValues(top = 8.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            itemsIndexed(files, key = { _, file -> file.uri }) { index, file ->
                SegmentedListItem(
                    onClick = { vm.vault?.let { onOpen(it, file) } },
                    shapes = ListItemDefaults.segmentedShapes(index = index, count = files.size),
                    leadingContent = {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                            Icon(painterResource(R.drawable.list_alt_24px), null, modifier = Modifier.padding(10.dp).size(24.dp),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    },
                    supportingContent = { Text(file.path, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    trailingContent = { Icon(painterResource(R.drawable.chevron_right_24px), null) },
                ) { Text(file.name, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}
