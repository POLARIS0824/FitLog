package com.example.myfitness.domain.model

/**
 * AI 对话中的单条消息。
 *
 * @param role 消息角色，如 "system"、"user"、"assistant"
 * @param content 消息文本内容
 */
data class ChatMessage(
    val role: String,
    val content: String,
)
