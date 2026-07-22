package com.example.fitlog.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * OpenAI 兼容 Chat Completions API 的消息对象。
 *
 * @param role 消息角色，如 "system"、"user"、"assistant"、"tool"
 * @param content 消息内容；assistant 发起 tool_calls 时可能为 null，故可空
 * @param toolCalls role == "assistant" 且模型请求调用工具时非空
 * @param toolCallId role == "tool" 时必填，回指 [ToolCallDto.id]
 * @param name role == "tool" 时的工具名（部分 provider 要求携带）
 */
@Serializable
data class MessageDto(
    val role: String,
    val content: String? = null,
    @SerialName("tool_calls")
    val toolCalls: List<ToolCallDto>? = null,
    @SerialName("tool_call_id")
    val toolCallId: String? = null,
    val name: String? = null,
)

/**
 * 模型发起的单次工具调用。
 *
 * @param id 调用唯一标识，tool 结果消息通过 tool_call_id 回指
 * @param type 调用类型，固定为 "function"
 * @param function 函数调用详情
 */
@Serializable
data class ToolCallDto(
    val id: String,
    val type: String = "function",
    val function: FunctionCallDto,
)

/**
 * 函数调用详情。
 *
 * @param name 函数名，与 tools 数组中声明的一致
 * @param arguments 参数的 JSON【字符串】（非对象），使用时需二次 parse
 */
@Serializable
data class FunctionCallDto(
    val name: String,
    val arguments: String,
)
