package com.example.fitlog.vault

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
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 640.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            FitLogPageHeader(stringResource(R.string.vault_management_title),
                stringResource(R.string.vault_management_description), onBack, backEnabled = !vm.busy)
            if (vm.loading || vm.busy) LinearWavyProgressIndicator(Modifier.fillMaxWidth())
            val folder = vm.folder
            if (!vm.loading) {
                if (folder != null) VaultCard(folder, pending = false, createAfterSetup = false)
                else FitLogNotice(stringResource(R.string.log_no_vault))
            }
            vm.error?.let { FitLogNotice(stringResource(it), error = true) }
            Button(
                onClick = onConnect, enabled = !vm.busy && !vm.loading,
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
                    OutlinedButton(onClick = vm::requestDisconnect, enabled = !vm.busy && folder != null) {
                        Text(stringResource(R.string.vault_disconnect), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
    if (vm.confirmDisconnect) AlertDialog(onDismissRequest = vm::cancelDisconnect,
        title = { Text(stringResource(R.string.vault_disconnect)) },
        text = { Text(stringResource(R.string.vault_disconnect_message)) },
        confirmButton = { TextButton(onClick = { vm.disconnect() }, enabled = !vm.busy) { Text(stringResource(R.string.vault_disconnect)) } },
        dismissButton = { TextButton(onClick = vm::cancelDisconnect, enabled = !vm.busy) { Text(stringResource(R.string.editor_cancel)) } })
}
