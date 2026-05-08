package com.example.myfitness.feature.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myfitness.domain.model.AIProviderConfig
import com.example.myfitness.domain.model.ChatMessage
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
     * @param baseUrl AI 提供商的 base URL
     * @param apiKey API Key
     * @param model 模型标识
     */
    fun addConfig(name: String, baseUrl: String, apiKey: String, model: String) {
        viewModelScope.launch {
            val id = "config_${System.currentTimeMillis()}"
            val config = AIProviderConfig(
                id = id,
                name = name.trim(),
                baseUrl = baseUrl.trim(),
                apiKey = apiKey.trim(),
                model = model.trim(),
                isPreset = false,
            )
            configRepository.save(config)
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
