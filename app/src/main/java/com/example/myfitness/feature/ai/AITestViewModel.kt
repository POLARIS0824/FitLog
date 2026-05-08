package com.example.myfitness.feature.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myfitness.domain.model.AIProviderConfig
import com.example.myfitness.domain.model.ChatMessage
import com.example.myfitness.domain.model.ProviderType
import com.example.myfitness.domain.repository.AIProviderConfigRepository
import com.example.myfitness.domain.usecase.SendChatMessageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * AI 调用链条测试页面的 ViewModel。
 */
@HiltViewModel
class AITestViewModel @Inject constructor(
    private val configRepository: AIProviderConfigRepository,
    private val sendChatMessageUseCase: SendChatMessageUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AITestUiState())
    val uiState: StateFlow<AITestUiState> = _uiState

    init {
        loadConfigs()
    }

    /**
     * 加载所有已保存的配置和当前激活项。
     */
    private fun loadConfigs() {
        viewModelScope.launch {
            val configs = configRepository.getAll()
            val activeId = configRepository.getActiveId()
            _uiState.value = _uiState.value.copy(
                configs = configs,
                activeId = activeId,
            )
        }
    }

    /**
     * 保存一条新配置并刷新列表。
     *
     * @param name 配置名称（用于区分不同 provider）
     * @param type AI 提供商类型
     * @param baseUrl AI 提供商的 base URL
     * @param apiKey API Key
     * @param model 模型标识
     * @param customEndpoint 自定义 endpoint 路径（仅 [ProviderType.CUSTOM] 使用）
     * @param apiVersion API 版本（仅 [ProviderType.AZURE] 使用）
     */
    fun addConfig(
        name: String,
        type: ProviderType,
        baseUrl: String,
        apiKey: String,
        model: String,
        customEndpoint: String? = null,
        apiVersion: String? = null,
    ) {
        viewModelScope.launch {
            val id = "config_${System.currentTimeMillis()}"
            val config = AIProviderConfig(
                id = id,
                name = name.trim(),
                type = type,
                baseUrl = baseUrl.trim(),
                apiKey = apiKey.trim(),
                model = model.trim(),
                customEndpoint = customEndpoint?.trim()?.takeIf { it.isNotBlank() },
                apiVersion = apiVersion?.trim()?.takeIf { it.isNotBlank() },
                isPreset = false,
            )
            configRepository.save(config)
            loadConfigs()
        }
    }

    /**
     * 一键添加常用预设配置，方便快速测试不同平台。
     * 预设的 baseUrl 和 model 使用占位符，用户填入 API Key 即可使用。
     */
    fun addPresetConfigs() {
        viewModelScope.launch {
            val presets = listOf(
                AIProviderConfig(
                    id = "preset_openai",
                    name = "OpenAI",
                    type = ProviderType.OPENAI,
                    baseUrl = "https://api.openai.com",
                    apiKey = "",
                    model = "gpt-4o",
                    isPreset = true,
                ),
                AIProviderConfig(
                    id = "preset_moonshot",
                    name = "Moonshot",
                    type = ProviderType.MOONSHOT,
                    baseUrl = "https://api.moonshot.cn",
                    apiKey = "",
                    model = "moonshot-v1-8k",
                    isPreset = true,
                ),
                AIProviderConfig(
                    id = "preset_deepseek",
                    name = "DeepSeek",
                    type = ProviderType.DEEPSEEK,
                    baseUrl = "https://api.deepseek.com",
                    apiKey = "",
                    model = "deepseek-chat",
                    isPreset = true,
                ),
                AIProviderConfig(
                    id = "preset_siliconflow",
                    name = "SiliconFlow",
                    type = ProviderType.SILICONFLOW,
                    baseUrl = "https://api.siliconflow.cn",
                    apiKey = "",
                    model = "deepseek-ai/DeepSeek-V3",
                    isPreset = true,
                ),
                AIProviderConfig(
                    id = "preset_azure",
                    name = "Azure OpenAI",
                    type = ProviderType.AZURE,
                    baseUrl = "https://your-resource.openai.azure.com",
                    apiKey = "",
                    model = "gpt-4",
                    apiVersion = "2024-02-01",
                    isPreset = true,
                ),
            )
            presets.forEach { configRepository.save(it) }
            loadConfigs()
        }
    }

    /**
     * 将指定配置设为当前激活项。
     *
     * @param id 配置 ID
     */
    fun setActiveConfig(id: String) {
        viewModelScope.launch {
            configRepository.setActiveId(id)
            _uiState.value = _uiState.value.copy(activeId = id)
        }
    }

    /**
     * 使用当前激活的配置发送测试消息。
     */
    fun testActiveConfig() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                result = null,
                error = null,
            )

            try {
                val messages = listOf(
                    ChatMessage(role = "system", content = "You are a helpful assistant."),
                    ChatMessage(role = "user", content = "Hello, please reply with a short greeting."),
                )
                val response = sendChatMessageUseCase(messages)

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    result = response,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Unknown error",
                )
            }
        }
    }

    /**
     * 删除指定配置。
     *
     * @param id 配置 ID
     */
    fun deleteConfig(id: String) {
        viewModelScope.launch {
            configRepository.delete(id)
            loadConfigs()
        }
    }
}

/**
 * AI 测试页面的 UI 状态。
 *
 * @param configs 已保存的配置列表
 * @param activeId 当前激活的配置 ID
 * @param isLoading 是否正在请求中
 * @param result 请求成功后的 AI 回复
 * @param error 请求失败后的错误信息
 */
data class AITestUiState(
    val configs: List<AIProviderConfig> = emptyList(),
    val activeId: String? = null,
    val isLoading: Boolean = false,
    val result: String? = null,
    val error: String? = null,
)
