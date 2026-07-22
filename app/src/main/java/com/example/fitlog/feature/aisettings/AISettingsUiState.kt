package com.example.fitlog.feature.aisettings

import com.example.fitlog.model.ai.AIProviderConfig
import com.example.fitlog.model.ai.ProviderType

data class AISettingsUiState(
    val provider: ProviderState,
    val apiKey: ApiKeyState,
    val model: ModelState,
    val endpoint: EndpointState,
    val test: TestState,
    val ui: UiState,
)

data class ProviderState(
    val providers: List<AIProviderConfig> = emptyList(),
    val activeProviderId: String? = null,
    /** 当前表单正在配置的服务商类型（区别于 activeProviderId：选中 ≠ 已激活，保存时才激活） */
    val selectedType: ProviderType = ProviderType.DEEPSEEK,
)

data class ApiKeyState(
    val apiKey: String = "",
    val showApiKey: Boolean = false,
)

data class ModelState(
    val availableModels: List<String> = emptyList(),
    val selectedModel: String,
    val isLoading: Boolean = false,
    /** 拉取模型列表结果提示（Snackbar 一次性展示，展示后由 [AISettingsViewModel.onFetchResultShown] 清除） */
    val fetchResult: String = "",
)

/** 端点相关表单字段（baseUrl / customEndpoint / apiVersion），随选中 provider 回填。 */
data class EndpointState(
    val baseUrl: String = "",
    val customEndpoint: String = "",
    val apiVersion: String = "",
)

// TODO: TestState
data class TestState(
    val isTesting: Boolean = false,
    val lastResult: String = "",
)

data class UiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    /** 保存成功提示（Snackbar 一次性展示，展示后由 [AISettingsViewModel.onSuccessShown] 清除） */
    val successMessage: String? = null,
)
