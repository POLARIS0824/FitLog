package com.example.fitlog.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * OpenAI 兼容 Chat Completions API 的请求体。
 *
 * 可选采样/格式参数默认 null，配合 kotlinx.serialization
 * 默认的 `encodeDefaults = false`，为 null 时不编码进请求体，
 * 因此不影响不支持这些字段的服务商（向下兼容）。
 *
 * @param model 模型标识，如 "deepseek-v4"
 * @param messages 对话上下文消息列表
 * @param temperature 采样温度（0~2），越低越稳定；null 由服务商默认
 * @param maxTokens 最大输出 token 数，用于控制成本；null 不限制
 * @param responseFormat 结构化输出约束（JSON mode）；null 纯文本
 * @param tools 可用工具定义列表（function calling）；null 不提供工具
 */
@Serializable
data class ChatCompletionRequestDto(
    val model: String,
    val messages: List<MessageDto>,
    val temperature: Double? = null,
    @SerialName("max_tokens") val maxTokens: Int? = null,
    @SerialName("response_format") val responseFormat: ResponseFormatDto? = null,
    val tools: List<ToolDefinitionDto>? = null,
)

/**
 * 结构化输出约束（OpenAI 兼容 JSON mode）。
 *
 * 注意：仅部分服务商/模型支持 `json_object`（DeepSeek、Moonshot 等支持），
 * 不支持的服务商会报错或忽略——调用方需自行做解析容错兜底。
 *
 * @param type 固定为 "json_object"
 */
@Serializable
data class ResponseFormatDto(
    val type: String,
)
