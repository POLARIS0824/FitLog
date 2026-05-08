package com.example.myfitness.domain.model

/**
 * 用户的 AI 提供商配置
 */
data class AIProviderConfig(
    val id: String,
    val name: String,
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val isPreset: Boolean,
)
