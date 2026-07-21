package com.example.fitlog.feature.aisettings

import com.example.fitlog.model.ai.AIProviderConfig

data class AISettingsUiState(
    val provider: ProviderState,
    val apiKey: ApiKeyState,
    val model: ModelState,
    val test: TestState,
    val ui: UiState,
)

data class ProviderState(
    val providers: List<AIProviderConfig> = emptyList(),
    val activeProviderId: String? = null,
    val showEditor: Boolean = false,
    val editing: AIProviderConfig? = null,  // null = 新建，非 null = 编辑已有
)

data class ApiKeyState(
    val apiKey: String = "",
    val showApiKey: Boolean = false,
)

data class ModelState(
    val availableModels: List<String> = emptyList(),
    val selectedModel: String,
    val isLoading: Boolean = false,
)

// TODO: TestState
data class TestState(
    val isTesting: Boolean = false,
    val lastResult: String = "",
)

data class UiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)