package com.example.fitlog.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * OpenAI 兼容 Chat Completions API 的消息对象。
 *
 * 工具调用协议：
 * - assistant 侧：[toolCalls] 非空时 [content] 可为 null（模型仅请求调工具不说话）
 * - tool 侧：role = "tool"，[toolCallId] 引用对应的 [ToolCallDto.id]，[content] 为工具结果
 *
 * 新增字段全部可空默认 null，`encodeDefaults = false` 下不编码，
 * 不影响不支持 function calling 的服务商（向后兼容）。
 *
 * @param role 消息角色，如 "system"、"user"、"assistant"、"tool"
 * @param content 消息内容
 * @param toolCalls assistant 消息中模型请求的工具调用列表（仅 assistant 角色）
 * @param toolCallId 本条 tool 结果对应的工具调用 id（仅 role = "tool"）
 */
@Serializable
data class MessageDto(
    val role: String,
    val content: String?,
    @SerialName("tool_calls") val toolCalls: List<ToolCallDto>? = null,
    @SerialName("tool_call_id") val toolCallId: String? = null,
)
