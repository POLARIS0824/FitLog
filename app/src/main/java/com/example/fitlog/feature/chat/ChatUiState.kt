package com.example.fitlog.feature.chat

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
 *
 * @property messages 消息列表（assistant 消息自带过程时间线步骤，按时间升序）
 * @property input 输入框文本
 * @property isSending 是否正在等待 Agent 产出（控制发送按钮与滚动）
 * @property activeRun 进行中/待确认的 Agent 运行；非空时列表尾部渲染展开的时间线卡片
 * @property errorMessage 一次性错误提示（Snackbar 展示后清除）
 * @property pendingConfirmation 非空时展示确认对话框；确认/拒绝后由 ViewModel 清除
 */
data class ChatUiState(
    val messages: List<ChatUiMessage> = emptyList(),
    val input: String = "",
    val isSending: Boolean = false,
    val activeRun: ActiveRun? = null,
    val errorMessage: String? = null,
    val pendingConfirmation: PendingConfirmation? = null,
)
