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
 * @param reasoningContent 思考/推理内容（DeepSeek / GLM / SiliconFlow 等推理模型返回）
 * @param toolCalls assistant 消息中模型请求的工具调用列表（仅 assistant 角色）
 * @param toolCallId 本条 tool 结果对应的工具调用 id（仅 role = "tool"）
 */
@Serializable
data class MessageDto(
    val role: String,
    // 默认 null 必须显式声明：OpenAI 兼容网关对仅请求调工具的 assistant 消息
    // 合法省略 content 键，无默认值的可空属性会因缺键反序列化失败，
    // 整轮请求被当成解析错误作废。
    // 请求侧影响（encodeDefaults=false 下 content=null 被省略而非显式 null）：
    // 与 OpenAI 官方客户端行为一致（tool-call-only assistant 消息即省略 content），
    // 属对齐规范的变更而非回归
    val content: String? = null,
    @SerialName("reasoning_content") val reasoningContent: String? = null,
    @SerialName("tool_calls") val toolCalls: List<ToolCallDto>? = null,
    @SerialName("tool_call_id") val toolCallId: String? = null,
)

