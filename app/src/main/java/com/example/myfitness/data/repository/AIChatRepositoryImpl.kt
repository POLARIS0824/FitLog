package com.example.myfitness.data.repository

import com.example.myfitness.data.remote.AIApi
import com.example.myfitness.data.remote.dto.ChatCompletionRequestDto
import com.example.myfitness.data.remote.dto.MessageDto
import com.example.myfitness.domain.repository.AIChatRepository
import javax.inject.Inject

/**
 * [AIChatRepository] 的 Retrofit 实现。
 */
class AIChatRepositoryImpl @Inject constructor(
    private val api: AIApi,
) : AIChatRepository {

    override suspend fun sendMessage(
        url: String,
        apiKey: String,
        model: String,
        messages: List<MessageDto>,
    ): String {
        val request = ChatCompletionRequestDto(
            model = model,
            messages = messages,
        )
        val response = api.chatCompletions(
            url = url,
            authorization = "Bearer $apiKey",
            request = request,
        )
        return response.choices.firstOrNull()?.message?.content
            ?: throw IllegalStateException("Empty AI response")
    }
}
