package com.example.fitlog.editor

import com.example.fitlog.ui.components.FitLogLoadingIndicator

import com.example.fitlog.ui.preview.FitLogPreviews
import com.example.fitlog.ui.preview.FitLogPreview
import com.example.fitlog.ui.preview.PreviewDiary
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.fitlog.R
import com.example.fitlog.ui.components.FitLogPageHeader
import com.example.fitlog.ui.components.FitLogNotice
import com.example.fitlog.navigation.FitLogRoute
import java.text.DateFormat
import java.util.Date

@Composable
fun RecoveryScreen(vm: RecoveryViewModel, currentVaultId: String?, onOpen: (FitLogRoute.Editor) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val exporter = remember(context) { RecoveryExporter(context.contentResolver) }
    val fallbackName = stringResource(R.string.recovery_export_name)
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        if (uri == null) vm.cancelExport() else vm.export { exporter.export(uri, it) }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }
    BackHandler { if (vm.selectedId != null) vm.select(null) else onBack() }
    RecoveryContent(
        state = RecoveryUiState(
            selectedId = vm.selectedId,
            busy = vm.busy,
            notice = vm.notice,
            selected = vm.selected,
            entries = vm.entries,
        ),
        actions = RecoveryActions(
            refresh = { vm.refresh() },
            select = { vm.select(it) },
            restore = { vm.restore(it) },
            prepareExport = { vm.prepareExport({ export.launch(it) }, fallbackName) },
            delete = { vm.delete(it) },
        ),
        currentVaultId = currentVaultId, onOpen = onOpen, onBack = onBack,
    )
}

@Composable
private fun RecoveryContent(state: RecoveryUiState, actions: RecoveryActions, currentVaultId: String?, onOpen: (FitLogRoute.Editor) -> Unit, onBack: () -> Unit) {
    var deleting by remember { mutableStateOf<String?>(null) }
    val back = { if (state.selectedId != null) actions.select(null) else onBack() }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 840.dp).fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FitLogPageHeader(stringResource(R.string.recovery_title), onBack = back)
            state.notice?.let { FitLogNotice(stringResource(it)) }
            val selected = state.selected
            if (selected != null) {
                val draft = selected.draft
                Text(draft?.name?.ifBlank { null } ?: stringResource(R.string.recovery_unknown), style = MaterialTheme.typography.titleLargeEmphasized)
                Text(stringResource(if (selected.backup) R.string.recovery_backup else R.string.recovery_draft))
                Text(draft?.displayPath ?: draft?.directory ?: stringResource(R.string.recovery_unknown_location))
                if (selected.pending) Text(stringResource(R.string.recovery_pending_backup))
                if (selected.damaged) Text(stringResource(R.string.recovery_damaged), color = MaterialTheme.colorScheme.error)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { actions.restore(onOpen) }, enabled = !state.busy && draft?.directory != null && draft.vaultUri.isNotBlank()) {
                        Text(stringResource(R.string.recovery_continue))
                    }
                    OutlinedButton(onClick = { actions.prepareExport() }, enabled = !state.busy && draft != null) {
                        Text(stringResource(R.string.recovery_export))
                    }
                    TextButton(onClick = { deleting = selected.id }, enabled = !state.busy) { Text(stringResource(R.string.recovery_delete)) }
                }
                LazyColumn(Modifier.weight(1f)) { item { SelectionContainer { Text(draft?.text.orEmpty()) } } }
            } else {
                FilledTonalButton(onClick = { actions.refresh() }, enabled = !state.busy) { Text(stringResource(R.string.log_refresh)) }
                if (!state.busy && state.entries.isEmpty()) FitLogNotice(stringResource(R.string.recovery_empty))
                val groups = state.entries.groupBy { it.draft?.vaultId.orEmpty() }.entries.sortedWith(
                    compareByDescending<Map.Entry<String, List<RecoveryEntry>>> { it.key == currentVaultId }
                        .thenByDescending { it.value.maxOfOrNull { entry -> entry.draft?.updatedAt ?: 0 } ?: 0 })
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    groups.forEach { group ->
                        item(key = "vault:${group.key}") {
                            Text(group.value.firstNotNullOfOrNull { it.draft?.vaultUri } ?: stringResource(R.string.recovery_unknown), style = MaterialTheme.typography.titleSmall)
                        }
                        items(group.value.sortedByDescending { it.draft?.updatedAt ?: 0 }, key = { it.id }) { entry ->
                            Card(onClick = { actions.select(entry.id) }, modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.large,
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(entry.draft?.name?.ifBlank { null } ?: stringResource(R.string.recovery_unknown),
                                        style = MaterialTheme.typography.titleMediumEmphasized)
                                    Text(stringResource(if (entry.backup) R.string.recovery_backup else R.string.recovery_draft))
                                    Text(entry.draft?.displayPath ?: entry.draft?.directory ?: stringResource(R.string.recovery_unknown_location))
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
        FitLogLoadingIndicator(state.busy,
            Modifier.align(Alignment.TopCenter).widthIn(max = 840.dp).fillMaxWidth().padding(horizontal = 24.dp))
    }
    deleting?.let { id -> AlertDialog(onDismissRequest = { deleting = null },
        text = { Text(stringResource(R.string.recovery_delete_message)) },
        confirmButton = { TextButton(onClick = { deleting = null; actions.delete(id) }) { Text(stringResource(R.string.recovery_delete)) } },
        dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.editor_cancel)) } }) }
}

private data class RecoveryUiState(
    val selectedId: String? = null,
    val busy: Boolean = false,
    val notice: Int? = null,
    val selected: RecoveryEntry? = null,
    val entries: List<RecoveryEntry> = emptyList(),
)

private data class RecoveryActions(
    val refresh: () -> Unit = {},
    val select: (String?) -> Unit = {},
    val restore: ((FitLogRoute.Editor) -> Unit) -> Unit = {},
    val prepareExport: () -> Unit = {},
    val delete: (String) -> Unit = {},
)

@FitLogPreviews
@Composable
private fun RecoveryPreview() {
    val entry = PreviewDiary.recoveryEntry()
    FitLogPreview {
        RecoveryContent(RecoveryUiState(entries = listOf(entry)), RecoveryActions(),
            PreviewDiary.VAULT_ID, {}, {})
    }
}

@FitLogPreviews
@Composable
private fun RecoveryDetailPreview() {
    val entry = PreviewDiary.recoveryEntry()
    FitLogPreview {
        RecoveryContent(RecoveryUiState(entries = listOf(entry), selectedId = entry.id, selected = entry), RecoveryActions(),
            PreviewDiary.VAULT_ID, {}, {})
    }
}
