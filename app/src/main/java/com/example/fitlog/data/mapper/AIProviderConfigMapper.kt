package com.example.fitlog.data.mapper

import com.example.fitlog.data.local.entity.AIProviderConfigEntity
import com.example.fitlog.model.ai.AIProviderConfig
import com.example.fitlog.model.ai.ProviderType
import com.example.fitlog.util.security.KeystoreManager

/**
 * entity → domain model
 *
 * 关键点：[AIProviderConfigEntity.encryptedApiKey] 是密文，
 * 必须通过 [KeystoreManager.decrypt] 还原为明文 [AIProviderConfig.apiKey]。
 *
 * 这样外部调用方拿到的永远是可直接使用的明文 key，
 * 不需要知道加密细节。
 */
fun AIProviderConfigEntity.toModel(): AIProviderConfig {
    return AIProviderConfig(
        id = id,
        name = name,
        type = ProviderType.valueOf(type),
        baseUrl = baseUrl,
        apiKey = KeystoreManager.decrypt(encryptedApiKey),
        model = model,
        customEndpoint = customEndpoint,
        apiVersion = apiVersion,
        isPreset = isPreset,
        cachedModels = cachedModels?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
    )
}

/**
 * domain model → entity
 *
 * 关键点：[AIProviderConfig.apiKey] 是明文，
 * 必须通过 [KeystoreManager.encrypt] 加密后存入 [AIProviderConfigEntity.encryptedApiKey]。
 *
 * 将枚举 [ProviderType] 序列化为字符串存储。
 */
fun AIProviderConfig.toEntity(): AIProviderConfigEntity {
    return AIProviderConfigEntity(
        id = id,
        name = name,
        type = type.name,
        baseUrl = baseUrl,
        encryptedApiKey = KeystoreManager.encrypt(apiKey),
        model = model,
        customEndpoint = customEndpoint,
        apiVersion = apiVersion,
        isPreset = isPreset,
        cachedModels = cachedModels.joinToString(",").ifBlank { null },
    )
}
