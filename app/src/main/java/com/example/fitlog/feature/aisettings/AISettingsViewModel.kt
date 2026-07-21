package com.example.fitlog.feature.aisettings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fitlog.data.repository.AIProviderConfigRepository
import com.example.fitlog.model.ai.AIProviderConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * AI 服务商设置页 ViewModel。
 *
 * ## 状态来源
 *
 * - 配置列表、当前激活 ID → [AIProviderConfigRepository]（Room + DataStore，响应式自动刷新）
 * - 编辑器可见性 / 编辑目标、API Key 输入、模型选择 → 本地 [MutableStateFlow]（纯 UI 表单状态）
 *
 * 所有源通过 [combine] 汇聚为单个 [uiState]，任何一路变化都会触发重算，UI 无需手动刷新。
 */
@HiltViewModel
class AISettingsViewModel @Inject constructor(
    private val aiProviderConfigRepository: AIProviderConfigRepository
) : ViewModel() {

    /**
     * 编辑器状态。
     *
     * @property visible 编辑器对话框是否可见
     * @property editing 正在编辑的配置；`null` 表示新建
     */
    data class EditorState(
        val visible: Boolean = false,
        val editing: AIProviderConfig? = null,
    )

    private val editorState = MutableStateFlow(EditorState())
    private val apiKeyState = MutableStateFlow(ApiKeyState())
    private val modelState = MutableStateFlow(ModelState(selectedModel = ""))
    private val uiFlow = MutableStateFlow(UiState())

    /** 设置页 UI 状态流，由数据层 Flow 与本地表单 Flow 组合而成。 */
    val uiState: StateFlow<AISettingsUiState> = combine(
        aiProviderConfigRepository.getAIProviders(),
        aiProviderConfigRepository.activeProviderId,
        editorState,
        apiKeyState,
        modelState,
    ) { providers, activeId, editor, apiKey, model ->
        AISettingsUiState(
            provider = ProviderState(
                providers = providers,
                activeProviderId = activeId,
                showEditor = editor.visible,
                editing = editor.editing,
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
    // 编辑器开关
    // ──────────────────────────────────────

    /** 打开编辑器（新建模式），并清空表单。 */
    fun onAddNew() {
        editorState.update { EditorState(visible = true, editing = null) }
        apiKeyState.update { ApiKeyState() }
        modelState.update { ModelState(selectedModel = "") }
    }

    /** 打开编辑器（编辑模式），用现有配置回填表单。 */
    fun onEdit(config: AIProviderConfig) {
        editorState.update { EditorState(visible = true, editing = config) }
        apiKeyState.update { it.copy(apiKey = config.apiKey) }
        modelState.update { it.copy(selectedModel = config.model) }
    }

    /** 关闭编辑器。 */
    fun onEditorDismiss() = editorState.update { EditorState() }

    // ──────────────────────────────────────
    // 表单输入
    // ──────────────────────────────────────

    /** API Key 输入框内容变化。 */
    fun onApiKeyChange(value: String) = apiKeyState.update { it.copy(apiKey = value) }

    /** 切换 API Key 明文/密文显示。 */
    fun onToggleApiKeyVisibility() = apiKeyState.update { it.copy(showApiKey = !it.showApiKey) }

    /** 模型输入框内容变化。 */
    fun onModelChange(value: String) = modelState.update { it.copy(selectedModel = value) }

    // ──────────────────────────────────────
    // 持久化操作
    // ──────────────────────────────────────

    /**
     * 保存配置。
     *
     * [config] 由 Screen 组装（name / baseUrl / type 等纯表单字段由对话框本地维护），
     * 此处仅按 [EditorState.editing] 区分新增或更新并落库。
     *
     * 注意：保存成功后无需手动刷新列表，Room Flow 检测到变更会自动重新发射。
     */
    fun onSave(config: AIProviderConfig) {
        viewModelScope.launch {
            try {
                if (editorState.value.editing == null) {
                    aiProviderConfigRepository.insert(config)
                } else {
                    aiProviderConfigRepository.update(config)
                }
                onEditorDismiss()
            } catch (e: Exception) {
                uiFlow.update { it.copy(errorMessage = e.message ?: "保存失败") }
            }
        }
    }

    /** 删除配置；若删除的是当前激活配置，仓库层会自动清除激活 ID。 */
    fun onDelete(config: AIProviderConfig) {
        viewModelScope.launch {
            try {
                aiProviderConfigRepository.delete(config)
            } catch (e: Exception) {
                uiFlow.update { it.copy(errorMessage = e.message ?: "删除失败") }
            }
        }
    }

    /** 将指定配置设为当前激活的服务商。 */
    fun onSetActive(id: String) {
        viewModelScope.launch {
            try {
                aiProviderConfigRepository.setActiveProviderId(id)
            } catch (e: Exception) {
                uiFlow.update { it.copy(errorMessage = e.message ?: "切换失败") }
            }
        }
    }

    /** 错误提示已展示，清除错误信息。 */
    fun onErrorShown() = uiFlow.update { it.copy(errorMessage = null) }
}
