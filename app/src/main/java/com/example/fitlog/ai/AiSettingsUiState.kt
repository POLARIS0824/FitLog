package com.example.fitlog.ai

import com.example.fitlog.data.ai.AiModelSelection
import com.example.fitlog.data.ai.AiProviderConnection

internal data class AiSettingsUiState(
    val saving: Boolean = false,
    val initialized: Boolean = true,
    val currentSelection: AiModelSelection? = null,
    val providers: List<AiProviderConnection> = emptyList(),
    val loading: Boolean = false,
    val requestRunning: Boolean = false,
    val message: Int? = null,
    val baseUrl: String = AiSettingsViewModel.DEEPSEEK_URL,
    val name: String = "",
    val canEdit: Boolean = true,
    val providerId: String = "",
    val apiKey: String = "",
    val showKey: Boolean = false,
    val modelIds: List<String> = emptyList(),
    val selectedModel: String = "",
    val canSave: Boolean = false,
)

internal fun AiSettingsUiState.withDerivedValues(): AiSettingsUiState {
    val editable = initialized && !loading && !saving
    val valid = try {
        com.example.fitlog.data.llm.openAiEndpoint(baseUrl, "models")
        apiKey.trim().let { it.isNotEmpty() && it.all { char -> char in '!'..'~' } }
    } catch (_: IllegalArgumentException) { false }
    return copy(canEdit = editable, canSave = editable && !requestRunning && name.isNotBlank() && valid && selectedModel.trim() in modelIds)
}
