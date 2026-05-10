package com.example.fitlog.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * OpenAI 兼容 Chat Completions API 的请求体。
 *
 * @param model 模型标识，如 "deepseek-v4"
 * @param messages 对话上下文消息列表
 */
@Serializable
data class ChatCompletionRequestDto(
    val model: String,
    val messages: List<MessageDto>,
)
