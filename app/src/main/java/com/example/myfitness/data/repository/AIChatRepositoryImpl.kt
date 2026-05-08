package com.example.myfitness.data.repository

import com.example.myfitness.data.remote.AIApi
import com.example.myfitness.data.remote.dto.ChatCompletionRequestDto
import com.example.myfitness.data.remote.dto.MessageDto
import com.example.myfitness.domain.model.ChatMessage
import com.example.myfitness.domain.repository.AIChatRepository
import com.example.myfitness.domain.repository.AIProviderConfigRepository
import javax.inject.Inject

/**
 * [AIChatRepository] 的 Retrofit 实现。
 *
 * 内部自行读取当前激活的 AI 提供商配置，
 * 通过 [ProviderType] 构造请求 URL 与 Headers，负责 DTO 转换。
 */
class AIChatRepositoryImpl @Inject constructor(
    private val api: AIApi,
    private val configRepository: AIProviderConfigRepository,
) : AIChatRepository {

    override suspend fun sendChat(
        messages: List<ChatMessage>,
    ): String {
        val activeId = configRepository.getActiveId()
            ?: throw IllegalStateException("No active AI provider configured")
        val config = configRepository.getById(activeId)
            ?: throw IllegalStateException("Active AI provider not found")

        val url = config.type.buildUrl(config)
        val headers = config.type.buildHeaders(config.apiKey)
        val dtos = messages.map { MessageDto(role = it.role, content = it.content) }
        val request = ChatCompletionRequestDto(
            model = config.model,
            messages = dtos,
        )

        val response = api.chatCompletions(
            url = url,
            headers = headers,
            request = request,
        )
        return response.choices.firstOrNull()?.message?.content
            ?: throw IllegalStateException("Empty AI response")
    }
}
