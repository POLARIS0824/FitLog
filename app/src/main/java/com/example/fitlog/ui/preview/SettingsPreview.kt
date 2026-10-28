package com.example.fitlog.ui.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.example.fitlog.R
import com.example.fitlog.data.ai.AiModelSelection
import com.example.fitlog.data.ai.AiProviderConnection
import com.example.fitlog.data.settings.AppearancePreferences
import com.example.fitlog.data.vault.VaultConfigState
import com.example.fitlog.feature.settings.AiSettingsActions
import com.example.fitlog.feature.settings.AiSettingsContent
import com.example.fitlog.feature.settings.AiSettingsUiState
import com.example.fitlog.feature.settings.AiSettingsViewModel
import com.example.fitlog.feature.settings.AppearanceContent
import com.example.fitlog.feature.settings.SettingsScreen

@Preview(locale = "en", widthDp = 412, heightDp = 892, showBackground = true)
@Composable
private fun SettingsPreview() {
    FitLogPreview { SettingsScreen(AppearancePreferences(), true, VaultConfigState.NotConfigured,
        {}, {}, {}, {}, {}) }
}

@Preview(locale = "en", widthDp = 412, heightDp = 892, showBackground = true)
@Composable
private fun AppearancePreview() {
    FitLogPreview { AppearanceContent(AppearancePreferences(), true, false, null, {}, {}, {}, {}) }
}

@Preview(locale = "en", widthDp = 412, heightDp = 892, showBackground = true)
@Composable
private fun AiSettingsPreview() {
    val provider = AiProviderConnection("preview-provider", stringResource(R.string.ai_deepseek),
        AiSettingsViewModel.DEEPSEEK_URL, listOf("deepseek-chat", "deepseek-reasoner"))
    FitLogPreview {
        AiSettingsContent(AiSettingsUiState(providers = listOf(provider), providerId = provider.id,
            name = provider.name, apiKey = "preview-key", modelIds = provider.modelIds, selectedModel = "deepseek-chat",
            currentSelection = AiModelSelection(provider.id, "deepseek-chat"), canSave = true), AiSettingsActions(), {})
    }
}
