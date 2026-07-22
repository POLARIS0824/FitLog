package com.example.fitlog.model.ai

/**
 * 模型发起的单次工具调用（领域侧）。
 *
 * @param id 调用唯一标识，tool 结果消息通过 toolCallId 回指
 * @param name 工具名，与 [AgentTool.name] 一致
 * @param argumentsJson 调用参数的原始 JSON 字符串，执行前需 parse 为 JsonObject
 */
data class ToolCall(
    val id: String,
    val name: String,
    val argumentsJson: String,
)
