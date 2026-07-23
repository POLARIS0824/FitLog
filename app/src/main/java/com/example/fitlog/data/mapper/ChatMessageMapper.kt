package com.example.fitlog.data.mapper

import com.example.fitlog.data.remote.dto.MessageDto
import com.example.fitlog.model.ai.ChatMessage

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
        content = content ?: "",
    )
}
