package com.example.fitlog.feature.vault

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fitlog.R
import com.example.fitlog.app.navigation.FitLogBackGuard
import com.example.fitlog.data.vault.VaultAccessStatus
import com.example.fitlog.data.vault.VaultFolderInfo
import com.example.fitlog.ui.components.FitLogDimens
import com.example.fitlog.ui.components.FitLogLoadingIndicator
import com.example.fitlog.ui.components.FitLogNotice
import com.example.fitlog.ui.components.FitLogTopAppBar
import com.example.fitlog.ui.components.fitLogPagePadding
import com.example.fitlog.ui.components.rememberFitLogTopAppBarBehavior
import com.example.fitlog.ui.preview.FitLogPreview
import com.example.fitlog.ui.preview.FitLogPreviews

@Composable
internal fun VaultManagementScreen(vm: VaultManagementViewModel, onConnect: () -> Unit, onBack: () -> Unit) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    FitLogBackGuard(enabled = state.busy) { }
    VaultManagementContent(
        state = state,
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
    val behavior = rememberFitLogTopAppBarBehavior()
    val pagePadding = fitLogPagePadding()
    Column(Modifier.fillMaxSize().nestedScroll(behavior.nestedScrollConnection)) {
      FitLogTopAppBar(stringResource(R.string.vault_management_title), behavior,
          maxContentWidth = FitLogDimens.ContentWidth, onBack = onBack, backEnabled = !state.busy)
      Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = FitLogDimens.ContentWidth).fillMaxSize().verticalScroll(rememberScrollState()).padding(pagePadding),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Text(stringResource(R.string.vault_management_description), style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            val folder = state.folder
            if (!state.loading) {
                if (folder != null) VaultCard(folder, pending = false)
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
                    OutlinedButton(onClick = actions.requestDisconnect, enabled = !state.busy && folder != null, shapes = ButtonDefaults.shapes()) {
                        Text(stringResource(R.string.vault_disconnect), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
        FitLogLoadingIndicator(state.loading || state.busy,
            Modifier.align(Alignment.TopCenter).widthIn(max = FitLogDimens.ContentWidth).fillMaxWidth().padding(horizontal = pagePadding))
      }
    }
    if (state.confirmDisconnect) AlertDialog(onDismissRequest = actions.cancelDisconnect,
        title = { Text(stringResource(R.string.vault_disconnect)) },
        text = { Text(stringResource(R.string.vault_disconnect_message)) },
        confirmButton = { TextButton(onClick = { actions.disconnect() }, enabled = !state.busy, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.vault_disconnect)) } },
        dismissButton = { TextButton(onClick = actions.cancelDisconnect, enabled = !state.busy, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.record_cancel)) } })
}



private data class VaultManagementActions(
    val requestDisconnect: () -> Unit = {},
    val cancelDisconnect: () -> Unit = {},
    val disconnect: () -> Unit = {},
)

@FitLogPreviews
@Composable
private fun VaultManagementPreview() {
    val folder = VaultFolderInfo(android.net.Uri.EMPTY, stringResource(R.string.preview_vault_name),
        com.example.fitlog.data.vault.VaultAccessStatus.Readable)
    FitLogPreview { VaultManagementContent(VaultManagementUiState(folder = folder), VaultManagementActions(), {}, {}) }
}
