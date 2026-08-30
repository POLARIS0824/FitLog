package com.example.fitlog.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * OpenAI 兼容 Chat Completions API 的响应体。
 *
 * @param choices 模型生成的回复选项列表；部分服务商在 HTTP 200 下返回错误体时缺失
 * @param usage Token 使用量统计
 * @param error 服务商随 HTTP 200 返回的错误体（配额耗尽/内容审查/网关异常等）。
 *   不容错会在反序列化时抛异常，用户只能看到笼统的"解析失败"而非真实原因
 */
@Serializable
data class ChatCompletionResponseDto(
    val choices: List<ChoiceDto>? = null,
    val usage: UsageDto? = null,
    val error: ApiErrorDto? = null,
)

/**
 * 服务商错误体（OpenAI 兼容格式：`{"error": {"message": ..., "type": ..., "code": ...}}`）。
 *
 * @param message 人类可读的错误描述
 * @param type 错误类别（如 invalid_request_error、insufficient_quota）
 */
@Serializable
data class ApiErrorDto(
    val message: String? = null,
    val type: String? = null,
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
