package com.example.fitlog.data.mapper

import com.example.fitlog.data.remote.dto.FunctionCallDto
import com.example.fitlog.data.remote.dto.FunctionSpecDto
import com.example.fitlog.data.remote.dto.MessageDto
import com.example.fitlog.data.remote.dto.ToolCallDto
import com.example.fitlog.data.remote.dto.ToolDto
import com.example.fitlog.model.ai.ChatMessage
import com.example.fitlog.model.ai.ToolCall
import com.example.fitlog.model.ai.ToolDefinition

/**
 * 领域模型 → 网络 DTO。
 *
 * [ChatMessage] 是纯 Kotlin 数据类，不依赖任何框架；
 * [MessageDto] 带 [kotlinx.serialization.Serializable] 注解，用于 JSON 序列化。
 *
 * 将这两者分离是"关注点分离"的体现：
 * - 领域模型不依赖序列化库 → 更换网络库或序列化方案时不需要改模型
 * - DTO 可以自由调整字段名（如 [kotlinx.serialization.SerialName]）而不影响业务代码
 */
fun ChatMessage.toDto(): MessageDto {
    return MessageDto(
        role = role,
        content = content,
        // 空列表归一化为 null，避免序列化出 "tool_calls": []（配合 explicitNulls=false 不输出该字段）
        toolCalls = toolCalls.map { it.toDto() }.ifEmpty { null },
        toolCallId = toolCallId,
        name = name,
    )
}

/**
 * 工具调用领域模型 → DTO。type 固定为 "function"（DTO 默认值）。
 */
fun ToolCall.toDto(): ToolCallDto {
    return ToolCallDto(
        id = id,
        function = FunctionCallDto(
            name = name,
            arguments = argumentsJson,
        ),
    )
}

/**
 * 网络 DTO → 领域模型。
 *
 * 反向转换，将 AI 返回的消息从传输格式转为业务代码可直接使用的模型。
 */
fun MessageDto.toModel(): ChatMessage {
    return ChatMessage(
        role = role,
        content = content,
        toolCalls = toolCalls?.map { it.toModel() } ?: emptyList(),
        toolCallId = toolCallId,
        name = name,
    )
}

/**
 * 工具调用 DTO → 领域模型。arguments 保持原始 JSON 字符串，执行前再 parse。
 */
fun ToolCallDto.toModel(): ToolCall {
    return ToolCall(
        id = id,
        name = function.name,
        argumentsJson = function.arguments,
    )
}

/**
 * 工具声明领域模型 → 网络 DTO。type 固定为 "function"（DTO 默认值）。
 */
fun ToolDefinition.toDto(): ToolDto {
    return ToolDto(
        function = FunctionSpecDto(
            name = name,
            description = description,
            parameters = parametersSchema,
        ),
    )
}
