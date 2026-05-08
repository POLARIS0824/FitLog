package com.example.myfitness.domain.usecase

import com.example.myfitness.data.remote.dto.MessageDto
import com.example.myfitness.domain.model.ChatMessage
import com.example.myfitness.domain.repository.AIChatRepository
import com.example.myfitness.domain.repository.AIProviderConfigRepository
import javax.inject.Inject

/**
 * 发送对话消息并获取 AI 回复的业务用例。
 */
class SendChatMessageUseCase @Inject constructor(
    private val chatRepository: AIChatRepository,
    private val configRepository: AIProviderConfigRepository,
) {

    /**
     * 使用当前激活的 AI 提供商发送消息。
     *
     * @param messages 对话上下文消息列表
     * @return AI 生成的回复文本
     * @throws IllegalStateException 当没有激活配置时抛出
     */
    suspend operator fun invoke(messages: List<ChatMessage>): String {
        val activeId = configRepository.getActiveId()
            ?: throw IllegalStateException("No active AI provider configured")
        val config = configRepository.getById(activeId)
            ?: throw IllegalStateException("Active AI provider not found")

        val dtos = messages.map {
            MessageDto(role = it.role, content = it.content)
        }

        val url = config.baseUrl.removeSuffix("/") + "/v1/chat/completions"

        return chatRepository.sendMessage(
            url = url,
            apiKey = config.apiKey,
            model = config.model,
            messages = dtos,
        )
    }
}
