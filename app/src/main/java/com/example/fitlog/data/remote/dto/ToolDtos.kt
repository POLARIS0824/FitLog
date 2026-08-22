package com.example.fitlog.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * OpenAI 兼容 Chat Completions API 的工具（function calling）相关 DTO。
 *
 * 与 [ChatCompletionRequestDto] / [MessageDto] 中既有字段一样，全部可选字段
 * 默认 null 且 `encodeDefaults = false`，不支持工具调用的服务商收到的请求体
 * 与之前完全一致（向后兼容）。
 */

/**
 * 请求侧：一次可用的工具定义列表项。
 *
 * ⚠️ `type` 故意**不带默认值**：全局 Json 配置（`Json { ignoreUnknownKeys = true }`，
 * 见 `AIModule`）的 `encodeDefaults = false`，若带默认值 `"function"`，序列化时该字段
 * 会因等于默认值被跳过，请求体 tools 数组项缺 `"type":"function"`，OpenAI 兼容服务商
 * 会直接返回 400。调用方必须显式传 `type = "function"`。
 *
 * @param type 固定为 "function"
 * @param function 函数定义
 */
@Serializable
data class ToolDefinitionDto(
    val type: String,
    val function: FunctionDefinitionDto,
)

/**
 * 函数定义：名称 + 描述 + JSON Schema 参数。
 *
 * @param name 函数名（模型据此在 tool_calls 中引用）
 * @param description 函数功能描述（模型决策是否调用的主要依据）
 * @param parameters 参数的 JSON Schema 对象（`{"type":"object","properties":{...}}`）
 */
@Serializable
data class FunctionDefinitionDto(
    val name: String,
    val description: String,
    val parameters: JsonObject,
)

/**
 * 响应侧：assistant 消息中模型请求的一次工具调用。
 *
 * ⚠️ `type` 故意**不带默认值**：同 [ToolDefinitionDto]，序列化回传历史消息时若带默认值
 * 会被 `encodeDefaults = false` 跳过，导致 assistant 消息的 `tool_calls` 数组项缺
 * `"type"`，服务商 400。调用方必须显式传 `type = "function"`。
 *
 * @param id 本次调用的唯一标识（回传 tool 结果时以 tool_call_id 引用）
 * @param type 固定为 "function"
 * @param function 调用的函数名与参数（arguments 为 JSON 字符串，需自行解析）
 */
@Serializable
data class ToolCallDto(
    val id: String,
    val type: String,
    val function: FunctionCallDto,
)

/**
 * 响应侧：工具调用的函数负载。
 *
 * @param name 函数名
 * @param arguments 参数的 JSON 字符串（如 `{"count":5}`），可能因模型输出截断而不合法
 */
@Serializable
data class FunctionCallDto(
    val name: String,
    val arguments: String,
)
