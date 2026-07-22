package com.example.fitlog.model.ai

/**
 * 消息角色常量，避免魔法字符串。
 */
object ChatRole {
    const val SYSTEM = "system"
    const val USER = "user"
    const val ASSISTANT = "assistant"
    const val TOOL = "tool"
}

/**
 * AI 对话中的单条消息。
 *
 * @param role 消息角色，见 [ChatRole]
 * @param content 消息文本内容；assistant 发起 tool calls 时可能为 null
 * @param toolCalls role == assistant 且模型请求调用工具时非空
 * @param toolCallId role == tool 时回指 [ToolCall.id]
 * @param name role == tool 时的工具名（冗余，便于展示与调试）
 */
data class ChatMessage(
    val role: String,
    val content: String? = null,
    val toolCalls: List<ToolCall> = emptyList(),
    val toolCallId: String? = null,
    val name: String? = null,
)
