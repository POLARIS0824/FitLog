package com.example.fitlog.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * AI 提供商配置的数据库实体。
 *
 * [encryptedApiKey] 为 AES-GCM 密文，由 [com.example.fitlog.util.security.KeystoreManager]
 * 通过 Android Keystore 加密后存储。
 *
 * @property id 配置唯一标识，如 "openai"、用户自定义 UUID
 * @property name 展示名称，如 "OpenAI"
 * @property type 平台类型字符串，如 "OPENAI"、"CUSTOM"
 * @property baseUrl API 基础地址
 * @property encryptedApiKey 加密后的 API 密钥密文
 * @property model 默认模型名称
 * @property customEndpoint 自定义 endpoint 路径
 * @property apiVersion API 版本号
 * @property isPreset 是否为内置预设配置（用户不可删除）
 * @property cachedModels 缓存的可用模型列表（以逗号分隔的字符串存储）
 */
@Entity(tableName = "ai_provider_configs")
data class AIProviderConfigEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    @ColumnInfo(defaultValue = "CUSTOM")
    val type: String,
    val baseUrl: String,
    val encryptedApiKey: String,
    val model: String,
    val customEndpoint: String?,
    val apiVersion: String?,
    val isPreset: Boolean,
    val cachedModels: String? = null,
)
