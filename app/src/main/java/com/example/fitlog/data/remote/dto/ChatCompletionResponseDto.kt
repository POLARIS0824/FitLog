package com.example.fitlog.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * OpenAI 兼容 Chat Completions API 的响应体。
 *
 * @param choices 模型生成的回复选项列表
 * @param usage Token 使用量统计
 */
@Serializable
data class ChatCompletionResponseDto(
    val choices: List<ChoiceDto>,
    val usage: UsageDto? = null,
)

/**
 * 单个回复选项。
 *
 * @param message 回复消息
 * @param finishReason 生成结束原因，如 "stop"、"length"
 */
@Serializable
data class ChoiceDto(
    val message: MessageDto,
    @SerialName("finish_reason")
    val finishReason: String? = null,
)

/**
 * Token 使用量统计。
 *
 * @param promptTokens 输入 token 数
 * @param completionTokens 输出 token 数
 * @param totalTokens 总 token 数
 */
@Serializable
data class UsageDto(
    @SerialName("prompt_tokens")
    val promptTokens: Int,
    @SerialName("completion_tokens")
    val completionTokens: Int,
    @SerialName("total_tokens")
    val totalTokens: Int,
)
