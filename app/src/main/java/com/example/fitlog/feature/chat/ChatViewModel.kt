package com.example.fitlog.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fitlog.data.repository.AIChatRepository
import com.example.fitlog.model.ai.ChatMessage
import com.example.fitlog.model.ai.SystemPrompt.SYSTEM_PROMPT
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
 * 对话数据没有上游 Flow（不存 Room），由用户事件驱动，
 * 因此自行持有 [MutableStateFlow] 作为唯一状态源，
 * 每个公开函数对应一种 UI 事件，负责推进状态。
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val aiChatRepository: AIChatRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    /** 消息展示用自增 id（LazyColumn key 的唯一事实源；进程内单调递增）。 */
    private var nextMessageId = 1L

    /**
     * 输入框文本变化事件
     */
    fun onInputChange(text: String) {
        _uiState.update { it.copy(input = text) }
    }

    /**
     * 发送按钮点击事件
     */
    fun send() {
        // 空白或者正在发送中不可发送
        val input = _uiState.value.input.trim()
        if (input.isEmpty()) return
        if (_uiState.value.isSending) return

        // 消息立刻上屏，同时清除上一次的错误提示
        val userMessage = ChatMessage(role = "user", content = input, id = nextMessageId++)
        val messagesBeforeSend = _uiState.value.messages + userMessage
        _uiState.update {
            it.copy(
                messages = messagesBeforeSend,
                input = "",
                isSending = true,
                errorMessage = null,
            )
        }

        // 协程发送网络请求
        viewModelScope.launch {
            val apiMessages: List<ChatMessage> = listOf(SYSTEM_PROMPT) + messagesBeforeSend
            val result = aiChatRepository.chat(apiMessages)

            result
                .onSuccess { reply ->
                    _uiState.update {
                        it.copy(
                            messages = it.messages + reply.copy(id = nextMessageId++),
                            isSending = false,
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isSending = false,
                            errorMessage = error.message ?: "Send Failed",
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