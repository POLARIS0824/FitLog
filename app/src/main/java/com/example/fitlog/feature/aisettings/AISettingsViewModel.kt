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
import kotlin.coroutines.cancellation.CancellationException

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
    private val endpointState = MutableStateFlow(EndpointState())
    private val testState = MutableStateFlow(TestState())
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
            endpoint = EndpointState(),
            test = TestState(), // TODO: 连通性测试
            ui = UiState(),
        )
    }.combine(endpointState) { state, endpoint ->
        state.copy(endpoint = endpoint)
    }.combine(testState) { state, test ->
        state.copy(test = test)
    }.combine(uiFlow) { state, ui ->
        state.copy(ui = ui)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AISettingsUiState(
            provider = ProviderState(),
            apiKey = ApiKeyState(),
            model = ModelState(selectedModel = ""),
            endpoint = EndpointState(),
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
     * 用该类型的已保存配置回填表单；没有保存过则清空 apiKey、回填默认模型，
     * 同时清空已拉取的模型列表。
     *
     * 注意：必须挂起查询仓库 [AIProviderConfigRepository.getById]，而不是读
     * `uiState.value.provider.providers`——combine Flow 首次发射要等 Room 查询返回，
     * init 阶段读到的还是 initialValue（空列表），会导致重启后回填失败（时序竞态）。
     */
    fun onProviderSelected(type: ProviderType) {
        viewModelScope.launch {
            selectedTypeState.update { type }
            val spec = ProviderSpecs.of(type)
            val saved = aiProviderConfigRepository.getById(type.name)
            apiKeyState.update { ApiKeyState(apiKey = saved?.apiKey.orEmpty()) }
            modelState.update {
                ModelState(
                    selectedModel = saved?.model ?: spec.defaultModel,
                    availableModels = saved?.cachedModels ?: emptyList(),
                )
            }
            endpointState.update {
                EndpointState(
                    baseUrl = saved?.baseUrl ?: spec.defaultBaseUrl,
                    customEndpoint = saved?.customEndpoint.orEmpty(),
                    apiVersion = saved?.apiVersion.orEmpty(),
                )
            }
            // 切换 provider 后旧的测试结果不再有意义
            testState.update { TestState() }
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

    /** Base URL 输入框内容变化。 */
    fun onBaseUrlChange(value: String) = endpointState.update { it.copy(baseUrl = value) }

    /** 自定义 Endpoint 输入框内容变化。 */
    fun onCustomEndpointChange(value: String) = endpointState.update { it.copy(customEndpoint = value) }

    /** API Version 输入框内容变化。 */
    fun onApiVersionChange(value: String) = endpointState.update { it.copy(apiVersion = value) }

    // ──────────────────────────────────────
    // 拉取模型列表
    // ──────────────────────────────────────

    /**
     * 用当前表单里的凭据拉取可用模型列表。
     *
     * [baseUrl] / [customEndpoint] 由 Screen 传入（它们是 Screen 本地表单状态）；
     * 失败后模型列表保持推荐值，用户仍可手动输入，不被阻塞。
     * 成功拉取后会将模型列表持久化保存到 Room 数据库中。
     */
    fun onFetchModels(baseUrl: String, customEndpoint: String?) {
        val type = selectedTypeState.value
        val apiKey = apiKeyState.value.apiKey
        val spec = ProviderSpecs.of(type)
        if (apiKey.isBlank() || baseUrl.isBlank()) return

        viewModelScope.launch {
            modelState.update { it.copy(isLoading = true, fetchResult = "") }
            val tempConfig = AIProviderConfig(
                id = type.name,
                name = spec.displayName,
                type = type,
                baseUrl = baseUrl.trim(),
                apiKey = apiKey.trim(),
                model = modelState.value.selectedModel.ifBlank { spec.defaultModel },
                customEndpoint = customEndpoint,
                isPreset = true,
            )
            aiChatRepository.fetchModels(tempConfig)
                .onSuccess { models ->
                    modelState.update {
                        it.copy(
                            availableModels = models,
                            isLoading = false,
                            fetchResult = "✅ 成功拉取 ${models.size} 个模型",
                        )
                    }
                    val existing = aiProviderConfigRepository.getById(type.name)
                    if (existing != null) {
                        aiProviderConfigRepository.updateCachedModels(type.name, models)
                    } else {
                        aiProviderConfigRepository.insert(
                            tempConfig.copy(cachedModels = models)
                        )
                    }
                }
                .onFailure { e ->
                    modelState.update {
                        it.copy(
                            isLoading = false,
                            fetchResult = "❌ 拉取模型失败：${e.message ?: "未知错误"}",
                        )
                    }
                }
        }
    }

    /** 模型列表拉取结果提示已展示，清除结果文本。 */
    fun onFetchResultShown() = modelState.update { it.copy(fetchResult = "") }

    // ──────────────────────────────────────
    // 连通性测试
    // ──────────────────────────────────────

    /**
     * 用当前表单里的配置（无需已保存）发一条最小消息，验证全链路可用。
     *
     * 结果写入 [TestState]：测试中 → 成功（附带 AI 回复摘要）/ 失败（错误描述）。
     */
    fun onTestConnection() {
        val type = selectedTypeState.value
        val apiKey = apiKeyState.value.apiKey
        val model = modelState.value.selectedModel
        val endpoint = endpointState.value
        if (apiKey.isBlank() || model.isBlank() || endpoint.baseUrl.isBlank()) return

        viewModelScope.launch {
            testState.update { TestState(isTesting = true) }
            val tempConfig = AIProviderConfig(
                id = type.name,
                name = "",
                type = type,
                baseUrl = endpoint.baseUrl.trim(),
                apiKey = apiKey.trim(),
                model = model.trim(),
                customEndpoint = endpoint.customEndpoint.trim().ifBlank { null },
                apiVersion = endpoint.apiVersion.trim().ifBlank { null },
                isPreset = true,
            )
            aiChatRepository.testConnection(tempConfig)
                .onSuccess {
                    testState.update {
                        TestState(isTesting = false, lastResult = "✅ 连接成功")
                    }
                }
                .onFailure { e ->
                    testState.update {
                        TestState(isTesting = false, lastResult = "❌ 连接失败：${e.message}")
                    }
                }
        }
    }

    /** 测试结果提示已展示，清除结果文本。 */
    fun onTestResultShown() = testState.update { it.copy(lastResult = "") }

    // ──────────────────────────────────────
    // 保存
    // ──────────────────────────────────────

    /**
     * 保存当前服务商配置。
     *
     * [config] 由 Screen 组装（id = type.name，REPLACE 即 upsert）。
     * 保存成功后自动设为当前激活的服务商——保存即启用，
     * 并写入一次性 [UiState.successMessage] 供 Screen 弹出 Snackbar。
     */
    fun onSave(config: AIProviderConfig) {
        viewModelScope.launch {
            try {
                aiProviderConfigRepository.insert(config)
                aiProviderConfigRepository.setActiveProviderId(config.id)
                uiFlow.update { it.copy(successMessage = "已保存并启用 ${config.name}") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                uiFlow.update { it.copy(errorMessage = e.message ?: "保存失败") }
            }
        }
    }

    /** 错误提示已展示，清除错误信息。 */
    fun onErrorShown() = uiFlow.update { it.copy(errorMessage = null) }

    /** 保存成功提示已展示，清除成功信息。 */
    fun onSuccessShown() = uiFlow.update { it.copy(successMessage = null) }
}
