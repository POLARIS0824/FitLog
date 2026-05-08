package com.example.myfitness.domain.usecase

import com.example.myfitness.domain.model.ChatMessage
import com.example.myfitness.domain.repository.AIChatRepository
import javax.inject.Inject

/**
 * 发送对话消息并获取 AI 回复的业务用例。
 */
class SendChatMessageUseCase @Inject constructor(
    private val chatRepository: AIChatRepository,
) {

    /**
     * 发送消息并获取 AI 回复。
     *
     * @param messages 对话上下文消息列表
     * @return AI 生成的回复文本
     * @throws IllegalStateException 当没有激活配置或请求失败时抛出
     */
    suspend operator fun invoke(messages: List<ChatMessage>): String {
        return chatRepository.sendChat(messages)
    }
}
