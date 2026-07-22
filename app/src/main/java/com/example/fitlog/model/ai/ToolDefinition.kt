package com.example.fitlog.model.ai

import kotlinx.serialization.json.JsonObject

/**
 * 发给 LLM 的工具描述（领域侧，DTO 转换在 mapper 层）。
 *
 * @param name 工具名（snake_case），与 [AgentTool.name] 一致
 * @param description 功能描述，直接影响模型的触发率
 * @param parametersSchema 参数的 JSON Schema 对象
 */
data class ToolDefinition(
    val name: String,
    val description: String,
    val parametersSchema: JsonObject,
)
