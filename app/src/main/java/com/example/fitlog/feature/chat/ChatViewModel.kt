package com.example.fitlog.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fitlog.data.agent.AgentEvent
import com.example.fitlog.data.agent.AgentOrchestrator
import com.example.fitlog.data.repository.AIProviderConfigRepository
import com.example.fitlog.model.ai.ChatMessage
import com.example.fitlog.model.ai.ChatRole
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * AI 对话界面的 ViewModel。
 *
 * 对话数据没有上游 Flow（M1 不存 Room），由用户事件驱动，
 * 因此自行持有 [MutableStateFlow] 作为唯一状态源，
 * 每个公开函数对应一种 UI 事件，负责推进状态。
 *
 * 持有两条列表：
 * - [conversation]：发给 LLM 的完整上下文（含 tool 角色消息）
 * - UiState.items：UI 展示模型（role=tool 的消息不进列表，过程以指示条呈现）
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val orchestrator: AgentOrchestrator,
    private val providerConfigRepo: AIProviderConfigRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    /** LLM 对话历史（不含 system prompt，由 orchestrator 每轮构建）。 */
    private val conversation = mutableListOf<ChatMessage>()

    init {
        // 观察激活的 AI 服务商，驱动"去配置"引导条
        viewModelScope.launch {
            providerConfigRepo.activeProvider.collect { config ->
                _uiState.update { it.copy(hasActiveProvider = config != null) }
            }
        }
    }

    /**
     * 输入框文本变化事件。
     */
    fun onInputChange(text: String) {
        _uiState.update { it.copy(input = text) }
    }

    /**
     * 发送按钮点击事件：追加用户消息并驱动一轮完整的 agent 对话。
     */
    fun send() {
        val input = _uiState.value.input.trim()
        // 空白或对话进行中不可发送
        if (input.isEmpty()) return
        if (_uiState.value.phase != ChatPhase.IDLE) return
        if (_uiState.value.hasActiveProvider == false) {
            _uiState.update { it.copy(errorMessage = "请先在设置中配置 AI 服务商") }
            return
        }

        // 用户消息立刻上屏并进入 LLM 上下文
        conversation += ChatMessage(role = ChatRole.USER, content = input)
        _uiState.update {
            it.copy(
                items = it.items + ChatListItem.UserMessage(text = input),
                input = "",
                phase = ChatPhase.THINKING,
            )
        }

        viewModelScope.launch {
            orchestrator.run(conversation.toList(), ::onAgentEvent)
                .onSuccess { turn ->
                    // 本轮全部新增消息（含 tool 消息）入上下文，保证多轮对话不丢 tool 信息
                    conversation.addAll(turn.newMessages)
                    _uiState.update {
                        it.copy(
                            items = it.items + ChatListItem.AssistantMessage(
                                text = turn.finalReply.content.orEmpty(),
                            ),
                            phase = ChatPhase.IDLE,
                            activeToolName = null,
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            phase = ChatPhase.IDLE,
                            activeToolName = null,
                            errorMessage = error.message ?: "发送失败",
                        )
                    }
                }
        }
    }

    /**
     * orchestrator 中间事件 → UI 状态（tool 指示条的出现与状态翻转）。
     */
    private suspend fun onAgentEvent(event: AgentEvent) {
        when (event) {
            is AgentEvent.ToolCallStarted -> _uiState.update {
                it.copy(
                    phase = ChatPhase.EXECUTING_TOOL,
                    activeToolName = event.call.name,
                    items = it.items + ChatListItem.ToolCallItem(
                        id = event.call.id,
                        toolName = event.call.name,
                        status = ChatListItem.ToolCallItem.Status.RUNNING,
                    ),
                )
            }

            is AgentEvent.ToolCallFinished -> _uiState.update { state ->
                state.copy(
                    items = state.items.map { item ->
                        if (item is ChatListItem.ToolCallItem && item.id == event.call.id) {
                            item.copy(
                                status = if (event.success) {
                                    ChatListItem.ToolCallItem.Status.SUCCESS
                                } else {
                                    ChatListItem.ToolCallItem.Status.FAILED
                                },
                            )
                        } else {
                            item
                        }
                    },
                    // tool 执行完会继续请求 LLM，回到等待状态
                    phase = ChatPhase.THINKING,
                    activeToolName = null,
                )
            }

            AgentEvent.MaxRoundsReached -> _uiState.update {
                it.copy(errorMessage = "查询轮次过多，已自动停止")
            }
        }
    }

    /**
     * 错误提示展示完毕后调用，清除一次性错误状态。
     */
    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
