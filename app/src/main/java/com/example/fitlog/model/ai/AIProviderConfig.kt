package com.example.fitlog.model.ai

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
 * @property cachedModels 上次在线成功拉取的可用模型列表
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
    val cachedModels: List<String> = emptyList(),
) {

    /**
     * 该配置是否应走 Gemini 原生端点（而非 OpenAI 兼容协议）。
     *
     * 单一判定口径：baseUrl 指向 Google 官方域名，且类型未显式声明
     * OpenAI 兼容协议（Azure / Custom 一律走兼容路径——CUSTOM 的 URL 由
     * customEndpoint 决定，缺失时按兼容路径报错）。Agent 模型工厂
     * （AgentModelFactory）与 CoachInsight 路径都必须经此属性判定，
     * 防止同一家配置出现"聊天能用、洞察卡静默失败"的两套行为。
     */
    val usesNativeGeminiEndpoint: Boolean
        get() {
            val base = baseUrl.lowercase()
            return (base.contains("googleapis.com") || base.contains("generativelanguage")) &&
                type != ProviderType.AZURE &&
                type != ProviderType.CUSTOM
        }
}