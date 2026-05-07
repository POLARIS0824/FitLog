package com.example.myfitness.ai.remote

import com.example.myfitness.ai.model.ChatCompletionRequest
import com.example.myfitness.ai.model.ChatCompletionResponse
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * OpenAI 兼容的 Chat Completions API 接口。
 */
interface AiApi {
    /**
     * 发起对话补全请求。
     *
     * @param request 请求体
     * @return 响应体
     */
    @POST("v1/chat/completions")
    suspend fun chatCompletions(
        @Body request: ChatCompletionRequest,
    ): ChatCompletionResponse
}
