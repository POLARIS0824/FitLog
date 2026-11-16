package com.example.fitlog.ui.preview

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.fitlog.data.vault.VaultAccessStatus
import com.example.fitlog.data.vault.VaultFolderInfo
import com.example.fitlog.feature.vault.VaultConfigUiState
import com.example.fitlog.feature.vault.VaultSetupScreen
import com.example.fitlog.feature.vault.VaultSetupUiState

@Preview(locale = "en", widthDp = 412, heightDp = 892, showBackground = true)
@Composable
private fun VaultSetupNotConfiguredPreview() {
    FitLogPreview {
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

@Preview(locale = "en", widthDp = 412, heightDp = 892, showBackground = true)
@Composable
private fun VaultSetupCandidatePreview() {
    FitLogPreview {
        VaultSetupScreen(
            uiState = VaultSetupUiState(
                configState = VaultConfigUiState.Configured(
                    VaultFolderInfo(
                        uri = Uri.EMPTY,
                        displayName = "Old Vault",
                        accessStatus = VaultAccessStatus.Readable,
                    )
                ),
                candidateVault = VaultFolderInfo(
                    uri = Uri.EMPTY,
                    displayName = "New Obsidian Notes",
                    accessStatus = VaultAccessStatus.Readable,
                ),
            ),
            onChooseFolderClick = {},
            onConfirmClick = {},
            onRetryClick = {},
        )
    }
}
