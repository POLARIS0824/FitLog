package com.example.fitlog.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * OpenAI 兼容 Chat Completions API 的请求体。
 *
 * @param model 模型标识，如 "deepseek-v4"
 * @param messages 对话上下文消息列表
 * @param tools 可供模型调用的工具声明列表；null 表示本次请求不启用 function calling
 * @param toolChoice 工具选择策略，"auto" 表示由模型自行决定是否调用
 */
@Serializable
data class ChatCompletionRequestDto(
    val model: String,
    val messages: List<MessageDto>,
    val tools: List<ToolDto>? = null,
    @SerialName("tool_choice")
    val toolChoice: String? = null,
)
