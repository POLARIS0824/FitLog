package com.example.myfitness.ai.config

/**
 * AI 调用配置，支持 OpenAI 兼容的 API（如 OpenAI、Moonshot、DeepSeek、Gemini 等）。
 *
 * MVP 阶段在此手动填入 API key，后续可迁移到加密存储或用户设置页。
 *
 * @property baseUrl API 基础地址，如 "https://api.openai.com/"
 * @property apiKey Bearer Token 格式的 API 密钥
 * @property model 模型名称，如 "gpt-4o-mini"、"moonshot-v1-8k"
 */
data class AiConfig(
    val baseUrl: String = "https://api.openai.com/",
    val apiKey: String = "",
    val model: String = "gpt-4o-mini",
)
