package com.example.myfitness.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.myfitness.core.security.KeystoreManager
import com.example.myfitness.data.local.dao.AIProviderConfigDao
import com.example.myfitness.data.local.entity.AIProviderConfigEntity
import com.example.myfitness.domain.model.AIProviderConfig
import com.example.myfitness.domain.model.ProviderType
import com.example.myfitness.domain.repository.AIProviderConfigRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * [AIProviderConfigRepository] 的 Room + DataStore 实现。
 *
 * 配置数据持久化到 Room，[apiKey] 经 [KeystoreManager] 加密后存储。
 * 当前激活的配置 ID 保存到 DataStore。
 */
class AIProviderConfigRepositoryImpl @Inject constructor(
    private val dao: AIProviderConfigDao,
    private val dataStore: DataStore<Preferences>,
) : AIProviderConfigRepository {

    private companion object {
        val ACTIVE_AI_PROVIDER_ID = stringPreferencesKey("active_ai_provider_id")
    }

    override suspend fun getAll(): List<AIProviderConfig> {
        return dao.getAll().map { it.toDomain() }
    }

    override suspend fun getById(id: String): AIProviderConfig? {
        return dao.getById(id)?.toDomain()
    }

    override suspend fun save(config: AIProviderConfig) {
        dao.insert(config.toEntity())
    }

    override suspend fun delete(id: String) {
        dao.getById(id)?.let { dao.delete(it) }
    }

    override suspend fun getActiveId(): String? {
        return dataStore.data.map { it[ACTIVE_AI_PROVIDER_ID] }.first()
    }

    override suspend fun setActiveId(id: String) {
        dataStore.edit { it[ACTIVE_AI_PROVIDER_ID] = id }
    }

    /**
     * 将数据库实体转换为 domain 模型，解密 [encryptedApiKey]。
     */
    private fun AIProviderConfigEntity.toDomain(): AIProviderConfig {
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
        )
    }

    /**
     * 将 domain 模型转换为数据库实体，加密 [apiKey]。
     */
    private fun AIProviderConfig.toEntity(): AIProviderConfigEntity {
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
        )
    }
}
