package com.example.myfitness.domain.model

/**
 * 用户的 AI 提供商配置。
 *
 * @property id 配置唯一标识
 * @property name 展示名称
 * @property type 平台类型，决定 URL 构造和 Header 格式
 * @property baseUrl API 基础地址
 * @property apiKey API 密钥（明文，存储时经 Keystore 加密）
 * @property model 模型名称
 * @property customEndpoint 自定义 endpoint 路径（仅 [ProviderType.CUSTOM] 使用）
 * @property apiVersion API 版本（仅 [ProviderType.AZURE] 使用）
 * @property isPreset 是否为系统内置预设配置
 */
data class AIProviderConfig(
    val id: String,
    val name: String,
    val type: ProviderType = ProviderType.CUSTOM,
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val customEndpoint: String? = null,
    val apiVersion: String? = null,
    val isPreset: Boolean,
)
