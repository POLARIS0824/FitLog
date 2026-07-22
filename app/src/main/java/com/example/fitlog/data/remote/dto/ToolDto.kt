package com.example.fitlog.data.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * OpenAI 兼容 tools 数组的元素：{"type": "function", "function": {...}}。
 *
 * @param type 工具类型，固定为 "function"
 * @param function 函数规格声明
 */
@Serializable
data class ToolDto(
    val type: String = "function",
    val function: FunctionSpecDto,
)

/**
 * 函数规格声明，供模型决定是否调用以及如何传参。
 *
 * @param name 函数名（snake_case）
 * @param description 功能描述，直接影响模型的触发率
 * @param parameters 参数的 JSON Schema 对象；用 [JsonObject] 透传，
 * 避免为每个工具声明嵌套 DTO 类
 */
@Serializable
data class FunctionSpecDto(
    val name: String,
    val description: String,
    val parameters: JsonObject,
)
