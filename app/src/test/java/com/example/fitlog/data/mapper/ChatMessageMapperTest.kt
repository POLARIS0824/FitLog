package com.example.fitlog.data.mapper

import com.example.fitlog.data.remote.dto.MessageDto
import com.example.fitlog.model.ai.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [ChatMessageMapper] 的单元测试。
 * 验证对话消息在领域模型与网络 DTO 之间的双向映射。
 */
class ChatMessageMapperTest {

    /**
     * 测试领域模型转 DTO：role 与 content 透传。
     */
    @Test
    fun testModelToDto() {
        val model = ChatMessage(role = "user", content = "你好")
        val dto = model.toDto()

        assertEquals("user", dto.role)
        assertEquals("你好", dto.content)
    }

    /**
     * 测试 DTO 转领域模型：role 与 content 透传。
     */
    @Test
    fun testDtoToModel() {
        val dto = MessageDto(role = "assistant", content = "加油！")
        val model = dto.toModel()

        assertEquals("assistant", model.role)
        assertEquals("加油！", model.content)
    }

    /**
     * 测试双向转换的对称性。
     */
    @Test
    fun testRoundTripSymmetry() {
        val original = ChatMessage(role = "system", content = "You are a professional fitness coach")
        assertEquals(original, original.toDto().toModel())
    }
}
