package com.example.fitlog.feature.aisettings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fitlog.data.repository.AIChatRepository
import com.example.fitlog.data.repository.AIProviderConfigRepository
import com.example.fitlog.model.ai.AIProviderConfig
import com.example.fitlog.model.ai.ProviderType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * AI 服务商设置页 ViewModel（单配置页范式）。
 *
 * ## 语义模型
 *
 * 每种 [ProviderType] 对应数据库中**一条**配置，id 固定为 `type.name`。
 * 保存走 DAO 的 REPLACE 冲突策略，"新增"与"编辑"合并为同一个 upsert 操作。
 *
 * ## 状态来源
 *
 * - 已保存配置列表、激活 ID → [AIProviderConfigRepository]（Room + DataStore，响应式）
 * - 当前选中的类型、API Key 输入、模型选择 → 本地 [MutableStateFlow]（表单状态）
 */
@HiltViewModel
class AISettingsViewModel @Inject constructor(
    private val aiProviderConfigRepository: AIProviderConfigRepository,
    private val aiChatRepository: AIChatRepository,
) : ViewModel() {

    private val selectedTypeState = MutableStateFlow(ProviderType.DEEPSEEK)
    private val apiKeyState = MutableStateFlow(ApiKeyState())
    private val modelState = MutableStateFlow(ModelState(selectedModel = ""))
    private val uiFlow = MutableStateFlow(UiState())

    init {
        // 首帧定位：表单落在当前激活的 provider 上；没有激活项则保持默认
        viewModelScope.launch {
            val active = aiProviderConfigRepository.activeProvider.first()
            active?.let { onProviderSelected(it.type) }
        }
    }

    /** 设置页 UI 状态流，由数据层 Flow 与本地表单 Flow 组合而成。 */
    val uiState: StateFlow<AISettingsUiState> = combine(
        aiProviderConfigRepository.getAIProviders(),
        aiProviderConfigRepository.activeProviderId,
        selectedTypeState,
        apiKeyState,
        modelState,
    ) { providers, activeId, selectedType, apiKey, model ->
        AISettingsUiState(
            provider = ProviderState(
                providers = providers,
                activeProviderId = activeId,
                selectedType = selectedType,
            ),
            apiKey = apiKey,
            model = model,
            test = TestState(), // TODO: 连通性测试
            ui = UiState(),
        )
    }.combine(uiFlow) { state, ui ->
        state.copy(ui = ui)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AISettingsUiState(
            provider = ProviderState(),
            apiKey = ApiKeyState(),
            model = ModelState(selectedModel = ""),
            test = TestState(),
            ui = UiState(isLoading = true),
        ),
    )

    // ──────────────────────────────────────
    // Provider 选择
    // ──────────────────────────────────────

    /**
     * 在底部弹层中选中某个服务商。
     *
     * 用该类型的已保存配置回填表单（apiKey / model 走 VM 状态）；
     * 没有保存过则清空 apiKey、回填默认模型，同时清空已拉取的模型列表。
     */
    fun onProviderSelected(type: ProviderType) {
        selectedTypeState.update { type }
        val saved = uiState.value.provider.providers.firstOrNull { it.id == type.name }
        apiKeyState.update { ApiKeyState(apiKey = saved?.apiKey.orEmpty()) }
        modelState.update {
            ModelState(selectedModel = saved?.model ?: ProviderSpecs.of(type).defaultModel)
        }
    }

    // ──────────────────────────────────────
    // 表单输入
    // ──────────────────────────────────────

    /** API Key 输入框内容变化。 */
    fun onApiKeyChange(value: String) = apiKeyState.update { it.copy(apiKey = value) }

    /** 切换 API Key 明文/密文显示。 */
    fun onToggleApiKeyVisibility() = apiKeyState.update { it.copy(showApiKey = !it.showApiKey) }

    /** 模型输入框内容变化 / 点击推荐 chip。 */
    fun onModelChange(value: String) = modelState.update { it.copy(selectedModel = value) }

    // ──────────────────────────────────────
    // 拉取模型列表
    // ──────────────────────────────────────

    /**
     * 用当前表单里的凭据拉取可用模型列表。
     *
     * [baseUrl] / [customEndpoint] 由 Screen 传入（它们是 Screen 本地表单状态）；
     * 失败后模型列表保持推荐值，用户仍可手动输入，不被阻塞。
     */
    fun onFetchModels(baseUrl: String, customEndpoint: String?) {
        val type = selectedTypeState.value
        val apiKey = apiKeyState.value.apiKey
        if (apiKey.isBlank() || baseUrl.isBlank()) return

        viewModelScope.launch {
            modelState.update { it.copy(isLoading = true) }
            val tempConfig = AIProviderConfig(
                id = type.name,
                name = "",
                type = type,
                baseUrl = baseUrl.trim(),
                apiKey = apiKey.trim(),
                model = "",
                customEndpoint = customEndpoint,
                isPreset = true,
            )
            aiChatRepository.fetchModels(tempConfig)
                .onSuccess { models ->
                    modelState.update { it.copy(availableModels = models, isLoading = false) }
                }
                .onFailure { e ->
                    modelState.update { it.copy(isLoading = false) }
                    uiFlow.update { it.copy(errorMessage = "拉取模型失败：${e.message}") }
                }
        }
    }

    // ──────────────────────────────────────
    // 保存
    // ──────────────────────────────────────

    /**
     * 保存当前服务商配置。
     *
     * [config] 由 Screen 组装（id = type.name，REPLACE 即 upsert）。
     * 保存成功后自动设为当前激活的服务商——保存即启用。
     */
    fun onSave(config: AIProviderConfig) {
        viewModelScope.launch {
            try {
                aiProviderConfigRepository.insert(config)
                aiProviderConfigRepository.setActiveProviderId(config.id)
            } catch (e: Exception) {
                uiFlow.update { it.copy(errorMessage = e.message ?: "保存失败") }
            }
        }
    }

    /** 错误提示已展示，清除错误信息。 */
    fun onErrorShown() = uiFlow.update { it.copy(errorMessage = null) }
}
