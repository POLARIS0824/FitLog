package com.example.fitlog.feature.vault

import android.content.res.Configuration
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.fitlog.data.vault.VaultAccessStatus
import com.example.fitlog.data.vault.VaultFolderInfo
import com.example.fitlog.ui.preview.FitLogPreview
import com.example.fitlog.ui.preview.FitLogPreviews

@FitLogPreviews
@Preview(name = "English · 320dp · 200%", locale = "en", widthDp = 320, heightDp = 740, fontScale = 2f,
    showBackground = true)
@Preview(name = "中文 · 320dp · 200% · 深色", locale = "zh", widthDp = 320, heightDp = 740, fontScale = 2f,
    uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
internal fun SettingsUiReviewVaultCandidate() {
    FitLogPreview {
        VaultSetupScreen(
            uiState = VaultSetupUiState(
                configState = VaultConfigUiState.Configured(
                    VaultFolderInfo(Uri.EMPTY, "Workout archive 2025", VaultAccessStatus.Readable),
                ),
                candidateVault = VaultFolderInfo(Uri.EMPTY,
                    "Training journal 2026 · Strength and mobility", VaultAccessStatus.Readable),
            ),
            onChooseFolderClick = {}, onConfirmClick = {}, onRetryClick = {},
        )
    }
}

@FitLogPreviews
@Preview(name = "English · 320dp · 200%", locale = "en", widthDp = 320, heightDp = 740, fontScale = 2f,
    showBackground = true)
@Preview(name = "中文 · 320dp · 200% · 深色", locale = "zh", widthDp = 320, heightDp = 740, fontScale = 2f,
    uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
internal fun SettingsUiReviewVaultReauthorization() {
    FitLogPreview {
        VaultSetupScreen(
            uiState = VaultSetupUiState(
                configState = VaultConfigUiState.Configured(
                    VaultFolderInfo(Uri.EMPTY, "Training journal 2026", VaultAccessStatus.NeedsReauthorization),
                ),
            ),
            onChooseFolderClick = {}, onConfirmClick = {}, onRetryClick = {},
        )
    }
}

@Preview(name = "Vault candidate · English · 320dp · 200%", locale = "en",
    widthDp = 320, heightDp = 740, fontScale = 2f, showBackground = true)
@Composable
internal fun SettingsUiReviewVaultCandidateLargeEnglish() {
    SettingsUiReviewVaultCandidate()
}

@Preview(name = "Vault reauthorization · English · 320dp · 200%", locale = "en",
    widthDp = 320, heightDp = 740, fontScale = 2f, showBackground = true)
@Composable
internal fun SettingsUiReviewVaultReauthorizationLargeEnglish() {
    SettingsUiReviewVaultReauthorization()
}

@Preview(name = "Vault candidate · 中文 · 320dp · 200% · 深色", locale = "zh",
    widthDp = 320, heightDp = 740, fontScale = 2f,
    uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
internal fun SettingsUiReviewVaultCandidateLargeDark() {
    SettingsUiReviewVaultCandidate()
}
