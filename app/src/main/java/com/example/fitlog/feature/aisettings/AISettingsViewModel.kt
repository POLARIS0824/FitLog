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

    /** 用户是否已手动交互（输入表单/切换 provider）；true 时 init 不再回填，避免清空用户输入或强切回激活 provider。 */
    private var userInteracted = false
    private val apiKeyState = MutableStateFlow(ApiKeyState())
    private val modelState = MutableStateFlow(ModelState(selectedModel = ""))
    private val endpointState = MutableStateFlow(EndpointState())
    private val testState = MutableStateFlow(TestState())
    private val uiFlow = MutableStateFlow(UiState())

    init {
        // 首帧定位：表单落在当前激活的 provider 上；没有激活项则保持默认。
        // 竞态守卫：DataStore+Room 首读期间用户可能已手动交互（输入/切换 provider），
        // 此时放弃回填，避免清空用户输入或强切回 init 读到的 provider（见 userInteracted）。
        viewModelScope.launch {
            if (userInteracted) return@launch
            val active = aiProviderConfigRepository.activeProvider.first()
            if (userInteracted) return@launch
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
        userInteracted = true
        viewModelScope.launch {
            selectedTypeState.update { type }
            val spec = ProviderSpecs.of(type)
            val saved = aiProviderConfigRepository.getById(type.name)
            // 竞态守卫：挂起查询期间用户又切换了 provider，丢弃本次回填结果，
            // 避免快速连点 A→B 时旧协程（A）覆写已选中 B 的表单。
            if (selectedTypeState.value != type) return@launch
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
    fun onApiKeyChange(value: String) {
        userInteracted = true
        apiKeyState.update { it.copy(apiKey = value) }
    }

    /** 切换 API Key 明文/密文显示。 */
    fun onToggleApiKeyVisibility() {
        userInteracted = true
        apiKeyState.update { it.copy(showApiKey = !it.showApiKey) }
    }

    /** 模型输入框内容变化 / 点击推荐 chip。 */
    fun onModelChange(value: String) {
        userInteracted = true
        modelState.update { it.copy(selectedModel = value) }
    }

    /** Base URL 输入框内容变化。 */
    fun onBaseUrlChange(value: String) {
        userInteracted = true
        endpointState.update { it.copy(baseUrl = value) }
    }

    /** 自定义 Endpoint 输入框内容变化。 */
    fun onCustomEndpointChange(value: String) {
        userInteracted = true
        endpointState.update { it.copy(customEndpoint = value) }
    }

    /** API Version 输入框内容变化。 */
    fun onApiVersionChange(value: String) {
        userInteracted = true
        endpointState.update { it.copy(apiVersion = value) }
    }

    // ──────────────────────────────────────
    // 拉取模型列表
    // ──────────────────────────────────────

    /**
     * 表单状态 → [AIProviderConfig] 的唯一组装点。
     *
     * trim / 空串归一为 null / 默认值规则此前散落在 fetch/test/save 三处且
     * 互不一致（如 fetch 未 trim customEndpoint），本函数是消除漂移的唯一事实源；
     * 各调用方按自身语义做必填校验（apiKey/baseUrl 或 model）与字段补充。
     *
     * @return apiKey 或 baseUrl 为空（未填写）时返回 null
     */
    private fun buildConfigFromForm(): AIProviderConfig? {
        val type = selectedTypeState.value
        val spec = ProviderSpecs.of(type)
        val apiKey = apiKeyState.value.apiKey.trim()
        val baseUrl = endpointState.value.baseUrl.trim()
        if (apiKey.isBlank() || baseUrl.isBlank()) return null
        return AIProviderConfig(
            id = type.name,
            name = spec.displayName,
            type = type,
            baseUrl = baseUrl,
            apiKey = apiKey,
            model = modelState.value.selectedModel.trim(),
            customEndpoint = endpointState.value.customEndpoint.trim().ifBlank { null },
            apiVersion = endpointState.value.apiVersion.trim().ifBlank { null },
            isPreset = true, // 每类型一条的内置槽位配置
        )
    }

    /**
     * 用当前表单里的凭据拉取可用模型列表。
     *
     * 模型名留空时回落到该服务商的推荐默认模型；失败后模型列表保持推荐值，
     * 用户仍可手动输入，不被阻塞。成功拉取且该配置已有保存记录时，将模型列表
     * 并入其 cachedModels；配置从未保存时不落库（表单中的 apiKey 只用于本次请求）。
     */
    fun onFetchModels() {
        val type = selectedTypeState.value
        val spec = ProviderSpecs.of(type)
        val tempConfig = buildConfigFromForm()
            ?.let { if (it.model.isBlank()) it.copy(model = spec.defaultModel) else it }
            ?: return

        viewModelScope.launch {
            modelState.update { it.copy(isLoading = true, fetchResult = "") }
            val result = aiChatRepository.fetchModels(tempConfig)
            // 串台守卫：请求在途期间用户切换了 provider，丢弃过期结果，
            // 避免 A 的模型列表渲染进 B 的表单（用户误把 A 的模型保存到 B）。
            if (selectedTypeState.value != type) return@launch
            result
                .onSuccess { models ->
                    modelState.update {
                        it.copy(
                            availableModels = models,
                            isLoading = false,
                            fetchResult = "✅ 成功拉取 ${models.size} 个模型",
                        )
                    }
                    // 仅当该配置已保存过（存在行）才把拉取结果并入其 cachedModels；
                    // 配置从未保存时绝不 insert——否则表单里尚未确认的 apiKey
                    // 会随 tempConfig 被静默落库，与"保存"按钮语义冲突
                    if (aiProviderConfigRepository.getById(type.name) != null) {
                        aiProviderConfigRepository.updateCachedModels(type.name, models)
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
        val model = modelState.value.selectedModel
        val tempConfig = buildConfigFromForm()?.takeIf { model.isNotBlank() } ?: return

        viewModelScope.launch {
            testState.update { TestState(isTesting = true) }
            val type = tempConfig.type
            val result = aiChatRepository.testConnection(tempConfig)
            // 串台守卫：请求在途期间用户切换了 provider，丢弃过期结果。
            if (selectedTypeState.value != type) return@launch
            result
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
     * 保存按钮点击：从表单状态组装配置并保存。
     *
     * 领域对象组装（trim/判空/默认值）统一走 [buildConfigFromForm]——
     * Screen 只上报点击事件，不感知 [AIProviderConfig] 的构造细节。
     */
    fun onSaveClick() {
        val config = buildConfigFromForm()?.takeIf { it.model.isNotBlank() } ?: return

        onSave(
            config.copy(cachedModels = modelState.value.availableModels),
        )
    }

    /**
     * 保存当前服务商配置。
     *
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
