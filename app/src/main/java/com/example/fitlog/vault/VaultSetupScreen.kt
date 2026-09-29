@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.example.fitlog.vault

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.fitlog.R
import com.example.fitlog.data.vault.VaultAccessStatus
import com.example.fitlog.data.vault.VaultFolderInfo
import com.example.fitlog.data.vault.VaultPreferences
import com.example.fitlog.data.vault.VaultRepository
import com.example.fitlog.navigation.FitLogRoute
import com.example.fitlog.ui.theme.FitLogTheme

/**
 * 页面接入层：负责系统选择器、生命周期感知的状态收集与导航衔接。
 */
@Composable
fun VaultSetupRoute(
    route: FitLogRoute.VaultSetup,
    vaultPreferences: VaultPreferences,
    vaultRepository: VaultRepository,
    onSetupCompleted: (FitLogRoute.VaultSetup) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: VaultSetupViewModel = viewModel(
        factory = VaultSetupViewModel.Factory(
            createAfterSetup = route.createAfterSetup,
            requestId = route.requestId,
            vaultPreferences = vaultPreferences,
            vaultRepository = vaultRepository,
        )
    )

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val vaultPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        viewModel.onFolderPicked(uri)
    }

    LaunchedEffect(uiState.completedResult) {
        val result = uiState.completedResult ?: return@LaunchedEffect
        if (result.requestId == route.requestId) {
            viewModel.onCompletionConsumed()
            onSetupCompleted(route)
        }
    }

    BackHandler {
        if (viewModel.onBack()) {
            onBack()
        }
    }

    VaultSetupScreen(
        uiState = uiState,
        onChooseFolderClick = {
            viewModel.onSelectingFolder()
            vaultPicker.launch(null)
        },
        onConfirmClick = viewModel::confirm,
        onRetryClick = viewModel::retry,
        modifier = modifier,
    )
}

/**
 * 纯展示组件：根据 uiState 显示对应状态内容并分发操作回调。
 */
@Composable
fun VaultSetupScreen(
    uiState: VaultSetupUiState,
    onChooseFolderClick: () -> Unit,
    onConfirmClick: () -> Unit,
    onRetryClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.vault_setup_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )

        Text(
            text = stringResource(R.string.vault_setup_description),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )

        Spacer(modifier = Modifier.height(24.dp))

        when (val config = uiState.configState) {
            VaultConfigUiState.Loading -> {
                CircularProgressIndicator()
                Text(
                    text = stringResource(R.string.vault_setup_loading_config),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            is VaultConfigUiState.Failed -> {
                Text(
                    text = stringResource(R.string.vault_error_load_config_failed),
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(
                    onClick = onRetryClick,
                    modifier = Modifier.padding(top = 16.dp),
                ) {
                    Text(stringResource(R.string.vault_setup_btn_retry))
                }
            }
            VaultConfigUiState.NotConfigured, is VaultConfigUiState.Configured -> {
                if (uiState.hasCandidate) {
                    CandidateVaultCard(
                        candidate = uiState.candidateVault!!,
                        current = uiState.currentVault,
                        createAfterSetup = uiState.createAfterSetup,
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    val confirmLabel = stringResource(
                        if (uiState.isSaving) R.string.vault_setup_saving
                        else if (uiState.isChecking) R.string.vault_setup_checking
                        else R.string.vault_setup_btn_confirm
                    )
                    val reselectLabel = stringResource(R.string.vault_setup_btn_reselect)

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Button(
                            onClick = onConfirmClick,
                            enabled = uiState.canConfirm,
                            shape = ButtonGroupDefaults.connectedLeadingButtonShape,
                        ) {
                            Text(confirmLabel)
                        }

                        FilledTonalButton(
                            onClick = onChooseFolderClick,
                            enabled = !uiState.isSaving && !uiState.isChecking,
                            shape = ButtonGroupDefaults.connectedTrailingButtonShape,
                        ) {
                            Text(reselectLabel)
                        }
                    }
                } else if (uiState.currentVault != null) {
                    CurrentVaultCard(
                        vault = uiState.currentVault!!,
                        createAfterSetup = uiState.createAfterSetup,
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    val continueLabel = stringResource(
                        if (uiState.isChecking) R.string.vault_setup_checking
                        else R.string.vault_setup_btn_continue
                    )
                    val changeLabel = stringResource(
                        if (uiState.currentVault?.accessStatus == VaultAccessStatus.NeedsReauthorization) {
                            R.string.vault_setup_btn_reauthorize
                        } else {
                            R.string.vault_setup_btn_change
                        }
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Button(
                            onClick = onConfirmClick,
                            enabled = uiState.canConfirm,
                            shape = ButtonGroupDefaults.connectedLeadingButtonShape,
                        ) {
                            Text(continueLabel)
                        }

                        FilledTonalButton(
                            onClick = onChooseFolderClick,
                            enabled = !uiState.isSaving && !uiState.isChecking,
                            shape = ButtonGroupDefaults.connectedTrailingButtonShape,
                        ) {
                            Text(changeLabel)
                        }
                    }
                } else {
                    Button(
                        onClick = onChooseFolderClick,
                        enabled = !uiState.isSaving && !uiState.isChecking,
                        modifier = Modifier.padding(top = 16.dp),
                    ) {
                        Text(
                            stringResource(
                                if (uiState.isChecking) R.string.vault_setup_checking
                                else R.string.vault_setup_btn_choose
                            )
                        )
                    }
                }
            }
        }

        if (uiState.isChecking || uiState.isSaving) {
            Spacer(modifier = Modifier.height(16.dp))
            CircularProgressIndicator()
        }

        uiState.errorResId?.let { resId ->
            Text(
                text = stringResource(resId),
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 16.dp),
            )

            if (uiState.stage == VaultOperationStage.Idle && (uiState.hasCandidate || uiState.currentVault != null)) {
                Button(
                    onClick = onRetryClick,
                    modifier = Modifier.padding(top = 12.dp),
                ) {
                    Text(stringResource(R.string.vault_setup_btn_retry))
                }
            }
        }
    }
}

@Composable
private fun CandidateVaultCard(
    candidate: VaultFolderInfo,
    current: VaultFolderInfo?,
    createAfterSetup: Boolean,
    modifier: Modifier = Modifier,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.small,
            ) {
                Text(
                    text = stringResource(R.string.vault_label_pending),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = candidate.displayName?.ifBlank { null }
                    ?: stringResource(R.string.vault_folder_unnamed),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = stringResource(capabilityStringRes(candidate.accessStatus)),
                style = MaterialTheme.typography.bodySmall,
                color = capabilityColor(candidate.accessStatus),
                textAlign = TextAlign.Center,
            )

            if (createAfterSetup && candidate.accessStatus == VaultAccessStatus.ReadOnly) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.vault_setup_read_only_add_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }

            if (current != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.vault_label_current) + ": " +
                        (current.displayName?.ifBlank { null }
                            ?: stringResource(R.string.vault_folder_unnamed)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun CurrentVaultCard(
    vault: VaultFolderInfo,
    createAfterSetup: Boolean,
    modifier: Modifier = Modifier,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.small,
            ) {
                Text(
                    text = stringResource(R.string.vault_label_current),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = vault.displayName?.ifBlank { null }
                    ?: stringResource(R.string.vault_folder_unnamed),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = stringResource(capabilityStringRes(vault.accessStatus)),
                style = MaterialTheme.typography.bodySmall,
                color = capabilityColor(vault.accessStatus),
                textAlign = TextAlign.Center,
            )

            if (createAfterSetup && vault.accessStatus == VaultAccessStatus.ReadOnly) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.vault_setup_read_only_add_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private fun capabilityStringRes(status: VaultAccessStatus): Int = when (status) {
    VaultAccessStatus.CanCreateFiles -> R.string.vault_capability_can_create
    VaultAccessStatus.ReadOnly -> R.string.vault_capability_read_only
    VaultAccessStatus.NeedsReauthorization -> R.string.vault_capability_needs_reauthorization
    VaultAccessStatus.DirectoryUnavailable -> R.string.vault_capability_unavailable
    else -> R.string.vault_setup_error_access_failed
}

@Composable
private fun capabilityColor(status: VaultAccessStatus) = when (status) {
    VaultAccessStatus.CanCreateFiles -> MaterialTheme.colorScheme.primary
    VaultAccessStatus.ReadOnly -> MaterialTheme.colorScheme.outline
    else -> MaterialTheme.colorScheme.error
}

// ======================== Previews ========================

@Preview(name = "Not Configured", showBackground = true)
@Composable
private fun VaultSetupNotConfiguredPreview() {
    FitLogTheme {
        VaultSetupScreen(
            uiState = VaultSetupUiState(
                configState = VaultConfigUiState.NotConfigured,
            ),
            onChooseFolderClick = {},
            onConfirmClick = {},
            onRetryClick = {},
        )
    }
}

@Preview(name = "Configured - Can Create", showBackground = true)
@Composable
private fun VaultSetupConfiguredPreview() {
    FitLogTheme {
        VaultSetupScreen(
            uiState = VaultSetupUiState(
                configState = VaultConfigUiState.Configured(
                    VaultFolderInfo(
                        uri = Uri.EMPTY,
                        displayName = "My Workouts",
                        accessStatus = VaultAccessStatus.CanCreateFiles,
                    )
                ),
            ),
            onChooseFolderClick = {},
            onConfirmClick = {},
            onRetryClick = {},
        )
    }
}

@Preview(name = "Candidate Selected - Pending Connect", showBackground = true)
@Composable
private fun VaultSetupCandidatePreview() {
    FitLogTheme {
        VaultSetupScreen(
            uiState = VaultSetupUiState(
                configState = VaultConfigUiState.Configured(
                    VaultFolderInfo(
                        uri = Uri.EMPTY,
                        displayName = "Old Vault",
                        accessStatus = VaultAccessStatus.CanCreateFiles,
                    )
                ),
                candidateVault = VaultFolderInfo(
                    uri = Uri.EMPTY,
                    displayName = "New Obsidian Notes",
                    accessStatus = VaultAccessStatus.CanCreateFiles,
                ),
            ),
            onChooseFolderClick = {},
            onConfirmClick = {},
            onRetryClick = {},
        )
    }
}

@Preview(name = "Candidate ReadOnly for Add", showBackground = true)
@Composable
private fun VaultSetupReadOnlyAddPreview() {
    FitLogTheme {
        VaultSetupScreen(
            uiState = VaultSetupUiState(
                createAfterSetup = true,
                configState = VaultConfigUiState.NotConfigured,
                candidateVault = VaultFolderInfo(
                    uri = Uri.EMPTY,
                    displayName = "Read-Only Archive",
                    accessStatus = VaultAccessStatus.ReadOnly,
                ),
                errorResId = R.string.vault_error_read_only,
            ),
            onChooseFolderClick = {},
            onConfirmClick = {},
            onRetryClick = {},
        )
    }
}

@Preview(name = "Checking Folder", showBackground = true)
@Composable
private fun VaultSetupCheckingPreview() {
    FitLogTheme {
        VaultSetupScreen(
            uiState = VaultSetupUiState(
                stage = VaultOperationStage.Checking,
                candidateVault = VaultFolderInfo(
                    uri = Uri.EMPTY,
                    displayName = "Fitness Logs",
                    accessStatus = VaultAccessStatus.CanCreateFiles,
                ),
            ),
            onChooseFolderClick = {},
            onConfirmClick = {},
            onRetryClick = {},
        )
    }
}

@Preview(name = "Saving Folder", showBackground = true)
@Composable
private fun VaultSetupSavingPreview() {
    FitLogTheme {
        VaultSetupScreen(
            uiState = VaultSetupUiState(
                stage = VaultOperationStage.Saving,
                candidateVault = VaultFolderInfo(
                    uri = Uri.EMPTY,
                    displayName = "Fitness Logs",
                    accessStatus = VaultAccessStatus.CanCreateFiles,
                ),
            ),
            onChooseFolderClick = {},
            onConfirmClick = {},
            onRetryClick = {},
        )
    }
}

@Preview(name = "Save Error - Retry Available", showBackground = true)
@Composable
private fun VaultSetupErrorPreview() {
    FitLogTheme {
        VaultSetupScreen(
            uiState = VaultSetupUiState(
                candidateVault = VaultFolderInfo(
                    uri = Uri.EMPTY,
                    displayName = "Fitness Logs",
                    accessStatus = VaultAccessStatus.CanCreateFiles,
                ),
                errorResId = R.string.vault_error_save_config_failed,
            ),
            onChooseFolderClick = {},
            onConfirmClick = {},
            onRetryClick = {},
        )
    }
}
