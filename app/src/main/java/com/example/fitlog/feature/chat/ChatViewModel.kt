package com.example.fitlog.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fitlog.feature.agent.engine.AgentEngine
import com.example.fitlog.model.ai.ChatMessage
import com.google.adk.kt.events.Event
import com.google.adk.kt.types.FunctionCall
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/**
 * AI 教练对话页的 ViewModel，驱动 ADK Agent（[AgentEngine]）。
 *
 * ## 与旧版（纯文本聊天）的区别
 *
 * 消息不再直接发给 AI 服务商，而是进入 ADK agent 管线：模型可以调用
 * [com.example.fitlog.feature.agent.tools.FitnessTools] 查询用户真实数据再作答；
 * 涉及写操作的工具（记体重 / 切计划）会先暂停等待用户确认（见 [PendingConfirmation]）。
 *
 * ## 会话模型
 *
 * 单页面模式：固定 sessionId，历史由 ADK 的 RoomSessionService 持久化，
 * 进程重启后回到同一会话；AgentEngine 会在配置变化时自动重建 runner，历史不受影响。
 *
 * ## 事件流消费
 *
 * [collectAgentEvents] 逐条消费 ADK [Event]：
 * 1. 含 `adk_request_confirmation` 调用的事件 → 提取原始工具信息，置 [PendingConfirmation] 弹框；
 * 2. `errorMessage` → 统一错误提示；
 * 3. 最终回复（[Event.isFinalResponse] 且非 partial）→ 文本上屏。
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val agentEngine: AgentEngine,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    /** 消息展示用自增 id（LazyColumn key 的唯一事实源；进程内单调递增）。 */
    private var nextMessageId = 1L

    /** 单页面模式：固定会话 id，历史经 ADK RoomSessionService 持久化，重启后延续。 */
    private val sessionId = "main_chat"

    init {
        // 进程重启后回放持久化历史：模型上下文在会话库里延续，UI 也必须恢复一致视图，
        // 否则用户看到"对话凭空消失"而模型仍记得旧对话
        viewModelScope.launch {
            val history = agentEngine.replayHistory(sessionId)
            _uiState.update { state ->
                if (state.messages.isNotEmpty()) return@update state
                val messages = history.map { it.copy(id = nextMessageId++) }
                state.copy(messages = messages)
            }
        }
    }

    /**
     * 输入框文本变化事件
     */
    fun onInputChange(text: String) {
        _uiState.update { it.copy(input = text) }
    }

    /**
     * 发送按钮点击事件：进入 ADK agent 管线。
     */
    fun send() {
        // 空白或者正在发送中不可发送
        val input = _uiState.value.input.trim()
        if (input.isEmpty()) return
        if (_uiState.value.isSending) return

        // 消息立刻上屏，同时清除上一次的错误与残留确认框
        val userMessage = ChatMessage(role = "user", content = input, id = nextMessageId++)
        _uiState.update {
            it.copy(
                messages = it.messages + userMessage,
                input = "",
                isSending = true,
                errorMessage = null,
                pendingConfirmation = null,
            )
        }

        viewModelScope.launch {
            agentEngine.sendMessage(sessionId, input)
                .onSuccess { collectAgentEvents(it) }
                .onFailure { onEngineError(it) }
        }
    }

    /**
     * 用户对确认框的选择（同意/拒绝）。
     *
     * @param confirmed true=允许执行工具；false=拒绝（模型会向用户解释）
     */
    fun respondToConfirmation(confirmed: Boolean) {
        val pending = _uiState.value.pendingConfirmation ?: return
        _uiState.update { it.copy(pendingConfirmation = null, isSending = true) }

        viewModelScope.launch {
            agentEngine.respondToConfirmation(sessionId, pending.callId, confirmed)
                .onSuccess { collectAgentEvents(it) }
                .onFailure { error ->
                    onEngineError(error)
                    // 引擎失败时确认响应未写入会话，必须恢复确认框让用户重试——
                    // 若就此丢弃，原始 tool_call 将悬空（其后每轮请求 400，
                    // 会话毒化且只能手动清空）
                    _uiState.update { it.copy(pendingConfirmation = pending) }
                }
        }
    }

    /**
     * 消费一轮 ADK 事件流：确认请求 → 弹框暂停；最终文本 → 上屏；错误 → 提示。
     *
     * ADK 的事件流本身可能抛异常（工具执行失败、服务商中途断流、会话库 IO 错误等），
     * 因此整个收集过程包在 try/catch 中：异常转为 UI 错误提示而非未捕获协程异常
     * （后者会直接闪退）。[CancellationException] 按协程取消语义原样上抛。
     */
    private suspend fun collectAgentEvents(events: Flow<Event>) {
        var sawConfirmation = false
        var sawAssistantOutput = false // 见流结束时兜底判断
        try {
            events.collect { event ->
                // 1) 确认请求：ADK 已暂停本轮，UI 弹确认框
                val confirmationCall = event.functionCalls()
                    .firstOrNull { it.name == FunctionCall.REQUEST_CONFIRMATION_FUNCTION_CALL_NAME }
                if (confirmationCall != null) {
                    sawConfirmation = true
                    sawAssistantOutput = true
                    val original = confirmationCall.args[FunctionCall.ORIGINAL_FUNCTION_CALL_KEY]
                        as? Map<*, *>
                    val toolName = original?.get(FunctionCall.NAME_KEY) as? String
                        ?: confirmationCall.name
                    val toolArgs = original?.get(FunctionCall.ARGS_KEY) as? Map<String, Any?>
                        ?: emptyMap()
                    _uiState.update {
                        it.copy(
                            isSending = false,
                            pendingConfirmation = PendingConfirmation(
                                callId = confirmationCall.id ?: "",
                                toolName = toolName,
                                args = toolArgs,
                            ),
                        )
                    }
                    return@collect
                }

                // 2) 引擎/服务商错误
                event.errorMessage?.let { msg ->
                    sawAssistantOutput = true
                    _uiState.update { it.copy(errorMessage = msg, isSending = false) }
                    return@collect
                }

                // 3) 模型输出上屏
                val text = event.content?.parts?.mapNotNull { it.text }
                    ?.joinToString("") ?: ""
                val toolCalls = event.functionCalls()
                if (text.isNotBlank()) {
                    sawAssistantOutput = true
                    // 非最终轮的文本（如"我帮你查一下记录"+工具调用）也上屏：
                    // 只展示 isFinalResponse 会把中间说明整轮吞掉
                    _uiState.update {
                        it.copy(
                            messages = it.messages + ChatMessage(
                                role = "assistant",
                                content = text,
                                id = nextMessageId++,
                            ),
                        )
                    }
                }
                if (text.isBlank() && toolCalls.isNotEmpty() && !event.partial) {
                    // 纯工具调用轮：折叠为一条"调用了什么"的提示，让 agent 行为可感知
                    sawAssistantOutput = true
                    _uiState.update {
                        it.copy(
                            messages = it.messages + ChatMessage(
                                role = "assistant",
                                content = "（调用工具：" + toolCalls.joinToString("、") { it.name } + "）",
                                id = nextMessageId++,
                            ),
                        )
                    }
                }
            }

            // 流结束：确认中 → 等用户决定；无任何输出 → maxSteps 耗尽等静默场景，兜底提示
            if (!sawConfirmation && !sawAssistantOutput) {
                _uiState.update {
                    it.copy(errorMessage = "本轮对话未产生回复（可能已达到工具调用步数上限），请重试或换个问法")
                }
            }
            if (!sawConfirmation) {
                _uiState.update { it.copy(isSending = false) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onEngineError(e)
        }
    }

    /** 引擎初始化/发送失败（未配置服务商等）。 */
    private fun onEngineError(error: Throwable) {
        _uiState.update {
            it.copy(isSending = false, errorMessage = error.message ?: "Agent 请求失败")
        }
    }

    /**
     * 清空对话：删除会话库中的持久化历史并重置 UI。
     *
     * 也是协议坏历史（悬空 tool_call 等）的唯一自愈入口——用户遇到持续报错时
     * 可借此恢复可用状态。
     *
     * 删除失败时回滚 UI：否则界面已清空而模型上下文仍在，下一条消息
     * AI 仍"记得"被"清空"的对话，界面与模型状态分叉。
     */
    fun onClearChat() {
        if (_uiState.value.isSending) return
        val previous = _uiState.value
        _uiState.update {
            it.copy(
                messages = emptyList(),
                errorMessage = null,
                pendingConfirmation = null,
                input = "",
            )
        }
        viewModelScope.launch {
            agentEngine.clearSession(sessionId)
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            messages = previous.messages,
                            pendingConfirmation = previous.pendingConfirmation,
                            input = previous.input,
                            errorMessage = error.message ?: "清空会话失败，请重试",
                        )
                    }
                }
        }
    }

    /**
     * 错误提示展示完毕后调用，清除一次性错误状态（UI 事件命名，供 Snackbar 接线）。
     */
    fun onErrorShown() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
