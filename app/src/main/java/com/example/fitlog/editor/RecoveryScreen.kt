package com.example.fitlog.editor

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.fitlog.R
import com.example.fitlog.navigation.FitLogRoute
import java.text.DateFormat
import java.util.Date

@Composable
fun RecoveryScreen(vm: RecoveryViewModel, currentVault: String?, onOpen: (FitLogRoute.Editor) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val exporter = remember(context) { RecoveryExporter(context.contentResolver) }
    val fallbackName = stringResource(R.string.recovery_export_name)
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        if (uri == null) vm.cancelExport() else vm.export { exporter.export(uri, it) }
    }
    var deleting by remember { mutableStateOf<String?>(null) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }
    val back = { if (vm.selectedId != null) vm.select(null) else onBack() }
    BackHandler(onBack = back)
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        TextButton(onClick = back) { Text(stringResource(R.string.cd_back)) }
        Text(stringResource(R.string.recovery_title), style = MaterialTheme.typography.headlineMedium)
        if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        vm.notice?.let { Text(stringResource(it)) }
        val selected = vm.selected
        if (selected != null) {
            val draft = selected.draft
            Text(draft?.name?.ifBlank { null } ?: stringResource(R.string.recovery_unknown), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(if (selected.backup) R.string.recovery_backup else R.string.recovery_draft))
            Text(draft?.displayPath ?: draft?.originalDirectory() ?: stringResource(R.string.recovery_unknown_location))
            if (selected.pending) Text(stringResource(R.string.recovery_pending_backup))
            if (selected.damaged) Text(stringResource(R.string.recovery_damaged), color = MaterialTheme.colorScheme.error)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { vm.restore(onOpen) }, enabled = !vm.busy && draft?.originalDirectory() != null && draft.vault.isNotBlank()) {
                    Text(stringResource(R.string.recovery_continue))
                }
                TextButton(onClick = { vm.prepareExport({ export.launch(it) }, fallbackName) }, enabled = !vm.busy && draft != null) {
                    Text(stringResource(R.string.recovery_export))
                }
                TextButton(onClick = { deleting = selected.id }, enabled = !vm.busy) { Text(stringResource(R.string.recovery_delete)) }
            }
            LazyColumn(Modifier.weight(1f)) { item { SelectionContainer { Text(draft?.text.orEmpty()) } } }
        } else {
            TextButton(onClick = { vm.refresh() }, enabled = !vm.busy) { Text(stringResource(R.string.log_refresh)) }
            if (!vm.busy && vm.entries.isEmpty()) Text(stringResource(R.string.recovery_empty))
            val groups = vm.entries.groupBy { it.draft?.vault.orEmpty() }.entries.sortedWith(
                compareByDescending<Map.Entry<String, List<RecoveryEntry>>> { it.key == currentVault }
                    .thenByDescending { it.value.maxOfOrNull { entry -> entry.draft?.updatedAt ?: 0 } ?: 0 })
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                groups.forEach { group ->
                    item(key = "vault:${group.key}") {
                        Text(group.key.ifBlank { stringResource(R.string.recovery_unknown) }, style = MaterialTheme.typography.titleSmall)
                    }
                    items(group.value.sortedByDescending { it.draft?.updatedAt ?: 0 }, key = { it.id }) { entry ->
                        OutlinedCard(onClick = { vm.select(entry.id) }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Text(entry.draft?.name?.ifBlank { null } ?: stringResource(R.string.recovery_unknown))
                                Text(stringResource(if (entry.backup) R.string.recovery_backup else R.string.recovery_draft))
                                Text(entry.draft?.displayPath ?: entry.draft?.originalDirectory() ?: stringResource(R.string.recovery_unknown_location))
                                val time = entry.draft?.updatedAt ?: 0
                                if (time > 0) Text(DateFormat.getDateTimeInstance().format(Date(time)))
                                if (entry.damaged) Text(stringResource(R.string.recovery_damaged), color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
    deleting?.let { id -> AlertDialog(onDismissRequest = { deleting = null },
        text = { Text(stringResource(R.string.recovery_delete_message)) },
        confirmButton = { TextButton(onClick = { deleting = null; vm.delete(id) }) { Text(stringResource(R.string.recovery_delete)) } },
        dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.editor_cancel)) } }) }
}
