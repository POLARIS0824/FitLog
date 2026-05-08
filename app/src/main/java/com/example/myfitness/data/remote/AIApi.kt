package com.example.myfitness.data.remote

import com.example.myfitness.data.remote.dto.ChatCompletionRequest
import com.example.myfitness.data.remote.dto.ChatCompletionResponse
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url

/**
 * OpenAI 兼容的 Chat Completions API 接口。
 *
 * 使用 [@Url] 与 [@Header] 支持动态 endpoint 和 API Key，
 * 以便用户切换不同的 AI 提供商。
 */
interface AIApi {

    /**
     * 发送对话补全请求。
     *
     * @param url 完整的请求地址（覆盖 Retrofit 的 baseUrl）
     * @param authorization Authorization 请求头，格式为 "Bearer <apiKey>"
     * @param request 请求体
     * @return 响应体
     */
    @POST
    suspend fun chatCompletions(
        @Url url: String,
        @Header("Authorization") authorization: String,
        @Body request: ChatCompletionRequest,
    ): ChatCompletionResponse
}
