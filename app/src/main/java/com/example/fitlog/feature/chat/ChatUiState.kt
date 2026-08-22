package com.example.fitlog.feature.chat

import com.example.fitlog.model.ai.ChatMessage

/**
 * 待用户确认的工具调用（ADK 确认协议）。
 *
 * 当 agent 调用 [com.google.adk.kt.annotations.Tool] 且标了
 * `requireConfirmation = true` 时，ADK 会在事件流中暂停并发出一个合成
 * `adk_request_confirmation` 调用；UI 据此弹确认框，用户决定后经
 * [ChatViewModel.respondToConfirmation] 回传。
 *
 * @param callId 合成确认调用的 id（回传确认时必须原样带回）
 * @param toolName 原始工具名（如 logBodyWeight / setActivePlan）
 * @param args 原始工具参数（Map<String, Any?>，供 UI 展示摘要）
 */
data class PendingConfirmation(
    val callId: String,
    val toolName: String,
    val args: Map<String, Any?>,
)

/**
 * AI 教练界面的 UI 状态。
 */
data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val input: String = "",
    val isSending: Boolean = false,
    val errorMessage: String? = null,
    /** 非空时展示确认对话框；确认/拒绝后由 ViewModel 清除。 */
    val pendingConfirmation: PendingConfirmation? = null,
)
