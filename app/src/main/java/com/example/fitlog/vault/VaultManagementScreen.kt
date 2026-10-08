package com.example.fitlog.vault

import com.example.fitlog.ui.preview.FitLogPreviews
import com.example.fitlog.ui.preview.FitLogPreview
import com.example.fitlog.data.vault.VaultFolderInfo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.fitlog.R
import com.example.fitlog.ui.components.FitLogNotice
import com.example.fitlog.ui.components.FitLogPageHeader

@Composable
fun VaultManagementScreen(vm: VaultManagementViewModel, onConnect: () -> Unit, onBack: () -> Unit) {
    BackHandler(enabled = vm.busy) { }
    VaultManagementContent(
        state = VaultManagementUiState(
            busy = vm.busy,
            loading = vm.loading,
            folder = vm.folder,
            error = vm.error,
            confirmDisconnect = vm.confirmDisconnect,
        ),
        actions = VaultManagementActions(
            requestDisconnect = { vm.requestDisconnect() },
            cancelDisconnect = { vm.cancelDisconnect() },
            disconnect = { vm.disconnect() },
        ),
        onConnect = onConnect, onBack = onBack,
    )
}

@Composable
private fun VaultManagementContent(state: VaultManagementUiState, actions: VaultManagementActions, onConnect: () -> Unit, onBack: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 640.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            FitLogPageHeader(stringResource(R.string.vault_management_title),
                stringResource(R.string.vault_management_description), onBack, backEnabled = !state.busy)
            if (state.loading || state.busy) LinearWavyProgressIndicator(Modifier.fillMaxWidth())
            val folder = state.folder
            if (!state.loading) {
                if (folder != null) VaultCard(folder, pending = false, createAfterSetup = false)
                else FitLogNotice(stringResource(R.string.log_no_vault))
            }
            state.error?.let { FitLogNotice(stringResource(it), error = true) }
            Button(
                onClick = onConnect, enabled = !state.busy && !state.loading,
                shapes = ButtonDefaults.shapesFor(ButtonDefaults.MediumContainerHeight),
                contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
                modifier = Modifier.fillMaxWidth().heightIn(min = ButtonDefaults.MediumContainerHeight),
            ) {
                Text(stringResource(R.string.vault_switch_reauthorize), style = MaterialTheme.typography.titleMediumEmphasized)
            }
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.vault_disconnect_message), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = actions.requestDisconnect, enabled = !state.busy && folder != null) {
                        Text(stringResource(R.string.vault_disconnect), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
    if (state.confirmDisconnect) AlertDialog(onDismissRequest = actions.cancelDisconnect,
        title = { Text(stringResource(R.string.vault_disconnect)) },
        text = { Text(stringResource(R.string.vault_disconnect_message)) },
        confirmButton = { TextButton(onClick = { actions.disconnect() }, enabled = !state.busy) { Text(stringResource(R.string.vault_disconnect)) } },
        dismissButton = { TextButton(onClick = actions.cancelDisconnect, enabled = !state.busy) { Text(stringResource(R.string.editor_cancel)) } })
}

private data class VaultManagementUiState(
    val busy: Boolean = false,
    val loading: Boolean = false,
    val folder: VaultFolderInfo? = null,
    val error: Int? = null,
    val confirmDisconnect: Boolean = false,
)

private data class VaultManagementActions(
    val requestDisconnect: () -> Unit = {},
    val cancelDisconnect: () -> Unit = {},
    val disconnect: () -> Unit = {},
)

@FitLogPreviews
@Composable
private fun VaultManagementPreview() {
    val folder = VaultFolderInfo(android.net.Uri.EMPTY, stringResource(R.string.preview_vault_name),
        com.example.fitlog.data.vault.VaultAccessStatus.CanCreateFiles)
    FitLogPreview { VaultManagementContent(VaultManagementUiState(folder = folder), VaultManagementActions(), {}, {}) }
}
