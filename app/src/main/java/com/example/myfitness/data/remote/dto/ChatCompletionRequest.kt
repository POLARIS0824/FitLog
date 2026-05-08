package com.example.myfitness.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * OpenAI 兼容 Chat Completions API 的请求体。
 *
 * @param model 模型标识，如 "gpt-4o-mini"
 * @param messages 对话上下文消息列表
 */
@Serializable
data class ChatCompletionRequest(
    val model: String,
    val messages: List<MessageDto>,
)
