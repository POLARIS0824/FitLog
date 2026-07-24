package com.example.fitlog.model.ai

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * AI 提供商类型枚举。
 *
 * 每个类型负责构造自身的请求 URL 和 Headers。
 * 与 [AIProviderConfig.isPreset] 解耦：本枚举表示请求协议类型，
 * [isPreset] 表示配置记录是否为系统内置。
 */
enum class ProviderType {
    OPENAI,
    MOONSHOT,
    DEEPSEEK,
    SILICONFLOW,
    AZURE,
    CUSTOM;

    /**
     * 根据配置构建完整请求 URL。
     *
     * 使用 OkHttp [HttpUrl] builder 统一处理 path segment、query parameter 和编码，
     * 避免字符串拼接带来的斜杠、编码等问题。
     *
     * @param config 当前 AI 提供商配置
     * @return 完整的请求地址
     * @throws IllegalArgumentException 当 baseUrl 不合法或缺少必需参数时抛出
     */
    fun buildUrl(config: AIProviderConfig): String {
        val base = config.baseUrl.toHttpUrlOrNull()
            ?: throw IllegalArgumentException("Invalid baseUrl: ${config.baseUrl}")
        val builder = base.newBuilder()

        when (this) {
            OPENAI, MOONSHOT, SILICONFLOW -> {
                builder.addPathSegments("v1/chat/completions")
            }
            DEEPSEEK -> {
                builder.addPathSegments("chat/completions")
            }
            AZURE -> {
                val apiVersion = config.apiVersion
                    ?: throw IllegalArgumentException("Azure provider requires apiVersion")
                builder.addPathSegments("openai/deployments/${config.model}/chat/completions")
                builder.addQueryParameter("api-version", apiVersion)
            }
            CUSTOM -> {
                val custom = config.customEndpoint
                    ?: throw IllegalArgumentException("Custom provider requires customEndpoint")
                val customUrl = custom.toHttpUrlOrNull()
                if (customUrl != null) {
                    return customUrl.toString()
                }
                builder.addPathSegments(custom.removePrefix("/"))
            }
        }
        return builder.build().toString()
    }

    /**
     * 构建模型列表请求 URL（OpenAI 兼容的 GET /models）。
     *
     * 注意与 [buildUrl] 的策略差异：[AIProviderConfig.customEndpoint] 仅覆盖
     * 聊天补全路径（chat/completions），模型列表始终由 baseUrl + 各提供商
     * 约定路径构造（CUSTOM 与 OpenAI 一致，为 `v1/models`）。
     * 即 customEndpoint 是"仅聊天"的覆盖项，不影响模型列表。
     *
     * @param config 当前 AI 提供商配置
     * @return 模型列表请求地址
     * @throws UnsupportedOperationException Azure 无通用模型列表端点
     */
    fun buildModelsUrl(config: AIProviderConfig): String {
        val base = config.baseUrl.toHttpUrlOrNull()
            ?: throw IllegalArgumentException("Invalid baseUrl: ${config.baseUrl}")
        val builder = base.newBuilder()
        when (this) {
            OPENAI, MOONSHOT, SILICONFLOW, CUSTOM -> builder.addPathSegments("v1/models")
            DEEPSEEK -> builder.addPathSegments("models")
            AZURE -> throw UnsupportedOperationException("该类型不支持拉取模型列表")
        }
        return builder.build().toString()
    }

    /**
     * 构建请求 Headers。
     *
     * @param apiKey 当前配置的 API 密钥
     * @return Header 键值对 Map
     */
    fun buildHeaders(apiKey: String): Map<String, String> {
        return when (this) {
            OPENAI, MOONSHOT, DEEPSEEK, SILICONFLOW, CUSTOM -> {
                mapOf("Authorization" to "Bearer $apiKey")
            }
            AZURE -> {
                mapOf("api-key" to apiKey)
            }
        }
    }
}