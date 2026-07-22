package com.example.fitlog.model.ai

import kotlinx.serialization.json.JsonObject

/**
 * Agent 可调用的工具抽象。
 *
 * M1 全部为只读工具；M2 的写工具实现同一接口。
 * [execute] 返回 JSON 字符串作为 role=tool 消息的 content——
 * 结构化返回比自然语言更省 token 且不易被模型误读。
 */
interface AgentTool {

    /** 工具名（snake_case），如 "list_recent_workouts" */
    val name: String

    /** 给 LLM 看的功能描述，直接影响触发率 */
    val description: String

    /** 参数的 JSON Schema 对象 */
    val parametersSchema: JsonObject

    /**
     * 执行工具。
     *
     * @param arguments 模型给出的参数（由 [ToolCall.argumentsJson] parse 而来）
     * @return JSON 字符串结果，作为 role=tool 消息的 content 喂回模型
     */
    suspend fun execute(arguments: JsonObject): String
}

/**
 * 将 [AgentTool] 转为发给 LLM 的 [ToolDefinition]。
 */
fun AgentTool.toDefinition(): ToolDefinition = ToolDefinition(
    name = name,
    description = description,
    parametersSchema = parametersSchema,
)
