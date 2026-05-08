package com.example.myfitness.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * AI 提供商配置的数据库实体。
 *
 * [encryptedApiKey] 为 AES-GCM 密文，由 [com.example.myfitness.core.security.KeystoreManager]
 * 通过 Android Keystore 加密后存储。
 *
 * @property id 配置唯一标识，如 "openai"、"moonshot"、用户自定义 UUID
 * @property name 展示名称，如 "OpenAI"
 * @property baseUrl API 基础地址
 * @property encryptedApiKey 加密后的 API 密钥密文
 * @property model 默认模型名称
 * @property isPreset 是否为内置预设配置（用户不可删除）
 */
@Entity(tableName = "ai_provider_configs")
data class AIProviderConfigEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val baseUrl: String,
    val encryptedApiKey: String,
    val model: String,
    val isPreset: Boolean,
)
