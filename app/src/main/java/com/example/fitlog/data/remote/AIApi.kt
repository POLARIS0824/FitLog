package com.example.fitlog.data.remote

import com.example.fitlog.data.remote.dto.ChatCompletionRequestDto
import com.example.fitlog.data.remote.dto.ChatCompletionResponseDto
import com.example.fitlog.data.remote.dto.ModelsResponseDto
import retrofit2.http.Body
import retrofit2.http.GET
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
     * @param url 完整的请求地址（覆盖 Retrofit 的 baseUrl）用于支持多个提供商
     * @param headers 请求头键值对，由 [com.example.fitlog.model.ai.ProviderType.buildHeaders] 构造。
     * 把整个 Map 展开为一组 HTTP Headers。不同服务商的认证方式不同
     * @param request 请求体
     * @return 响应体
     */
    @POST
    suspend fun chatCompletions(
        @Url url: String,
        @HeaderMap headers: Map<String, String>,
        @Body request: ChatCompletionRequestDto,
    ): ChatCompletionResponseDto

    /**
     * 拉取模型列表（OpenAI 兼容的 GET /models）。
     *
     * @param url 完整的请求地址（覆盖 Retrofit 的 baseUrl）
     * @param headers 请求头键值对，同 [chatCompletions]
     * @return 模型列表响应体
     */
    @GET
    suspend fun models(
        @Url url: String,
        @HeaderMap headers: Map<String, String>,
    ): ModelsResponseDto
}
