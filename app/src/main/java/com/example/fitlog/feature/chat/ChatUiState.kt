package com.example.fitlog.feature.chat

/**
 * Chat 对话的阶段状态机。
 */
enum class ChatPhase {
    /** 空闲，可发送。 */
    IDLE,

    /** 等待 LLM 响应中。 */
    THINKING,

    /** 正在执行 tool。 */
    EXECUTING_TOOL,
}

/**
 * AI 教练界面的 UI 状态。
 *
 * @param items 消息列表展示模型（含用户/AI 消息、tool 指示条、错误条）
 * @param input 输入框文本
 * @param phase 当前对话阶段（驱动输入栏可用性与加载指示）
 * @param activeToolName 正在执行的 tool 名；null 表示无
 * @param errorMessage 一次性错误提示（snackbar 展示后由 dismissError 清除）
 * @param hasActiveProvider 是否已配置激活的 AI 服务商；null = 加载中
 */
data class ChatUiState(
    val items: List<ChatListItem> = emptyList(),
    val input: String = "",
    val phase: ChatPhase = ChatPhase.IDLE,
    val activeToolName: String? = null,
    val errorMessage: String? = null,
    val hasActiveProvider: Boolean? = null,
)
