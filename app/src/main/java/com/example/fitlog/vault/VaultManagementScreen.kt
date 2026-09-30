package com.example.fitlog.vault

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.fitlog.R
import com.example.fitlog.data.vault.VaultAccessStatus

@Composable
fun VaultManagementScreen(vm: VaultManagementViewModel, onConnect: () -> Unit, onBack: () -> Unit) {
    BackHandler(enabled = vm.busy) { }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = onBack, enabled = !vm.busy) { Text(stringResource(R.string.cd_back)) }
        Text(stringResource(R.string.vault_management_title), style = MaterialTheme.typography.headlineMedium)
        if (vm.loading || vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        val folder = vm.folder
        if (!vm.loading) {
            Text(folder?.displayName ?: stringResource(R.string.log_no_vault))
            folder?.let {
                Text(it.uri.toString())
                Text(stringResource(when (it.accessStatus) {
                    VaultAccessStatus.CanCreateFiles -> R.string.vault_access_writable
                    VaultAccessStatus.ReadOnly -> R.string.vault_error_read_only
                    VaultAccessStatus.NeedsReauthorization -> R.string.vault_error_needs_reauthorization
                    VaultAccessStatus.DirectoryUnavailable -> R.string.vault_error_directory_unavailable
                    else -> R.string.vault_setup_error_access_failed
                }))
            }
        }
        vm.error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
        TextButton(onClick = onConnect, enabled = !vm.busy && !vm.loading) { Text(stringResource(R.string.vault_switch_reauthorize)) }
        TextButton(onClick = vm::requestDisconnect, enabled = !vm.busy && folder != null) { Text(stringResource(R.string.vault_disconnect)) }
        Text(stringResource(R.string.vault_disconnect_message))
    }
    if (vm.confirmDisconnect) AlertDialog(onDismissRequest = vm::cancelDisconnect,
        text = { Text(stringResource(R.string.vault_disconnect_message)) },
        confirmButton = { TextButton(onClick = { vm.disconnect() }, enabled = !vm.busy) { Text(stringResource(R.string.vault_disconnect)) } },
        dismissButton = { TextButton(onClick = vm::cancelDisconnect, enabled = !vm.busy) { Text(stringResource(R.string.editor_cancel)) } })
}
