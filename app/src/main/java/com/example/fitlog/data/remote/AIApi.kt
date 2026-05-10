package com.example.fitlog.data.remote

import com.example.fitlog.data.remote.dto.ChatCompletionRequestDto
import com.example.fitlog.data.remote.dto.ChatCompletionResponse
import retrofit2.http.Body
import retrofit2.http.HeaderMap
import retrofit2.http.POST
import retrofit2.http.Url

/**
 * OpenAI 兼容的 Chat Completions API 接口。
 *
 * 使用 [@Url] 与 [@HeaderMap] 支持动态 endpoint 和自定义 Headers，
 * 以便适配不同 AI 提供商的路径与认证格式差异。
 */
interface AIApi {

    /**
     * 发送对话补全请求。
     *
     * @param url 完整的请求地址（覆盖 Retrofit 的 baseUrl）
     * @param headers 请求头键值对，由 [com.example.fitlog.domain.model.ai.ProviderType.buildHeaders] 构造
     * @param request 请求体
     * @return 响应体
     */
    @POST
    suspend fun chatCompletions(
        @Url url: String,
        @HeaderMap headers: Map<String, String>,
        @Body request: ChatCompletionRequestDto,
    ): ChatCompletionResponse
}
