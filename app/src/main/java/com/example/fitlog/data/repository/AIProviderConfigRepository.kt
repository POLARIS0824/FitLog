package com.example.fitlog.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.fitlog.data.local.dao.AIProviderConfigDao
import com.example.fitlog.data.mapper.toEntity
import com.example.fitlog.data.mapper.toModel
import com.example.fitlog.model.ai.AIProviderConfig
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * AI 服务商配置仓库。
 *
 * ## 职责
 *
 * 本仓库是 AI 配置的"唯一真实来源"，同时管理三件事：
 *
 * 1. **配置的增删改查** → 通过 [AIProviderConfigDao] 操作 Room 数据库
 * 2. **当前激活配置的持久化** → 通过 [DataStore] 存储 `active_ai_provider_id`
 * 3. **API Key 的加解密桥接** → 由 [com.example.fitlog.data.mapper.AIProviderConfigMapper]
 *    在 Entity ↔ Model 转换时自动完成，本仓库无需直接操作 [KeystoreManager]
 *
 * ## 数据流
 *
 * ```
 * 保存: ViewModel → Config (明文 apiKey) → toEntity() 加密 → Room (密文 encryptedApiKey)
 * 读取: Room (密文) → toModel() 解密 → Config (明文 apiKey) → ViewModel
 * ```
 *
 * 外部调用方（ViewModel、AIChatRepository）拿到的永远是明文且可用的配置，
 * 完全不需要感知加密/解密的存在。
 *
 * ## 激活管理的设计
 *
 * "当前使用哪个 AI 服务商"是一个 UI 偏好，不属于领域数据，
 * 因此用 DataStore（轻量键值存储）而非 Room。
 *
 * [getActiveProviderId] 返回 [Flow] 是为了让 UI 能响应式观察切换变化。
 * [getActiveProvider] 是其增强版，自动将 ID 解析为完整配置。
 */
class AIProviderConfigRepository @Inject constructor(
    private val aiProviderConfigDao: AIProviderConfigDao,
    private val dataStore: DataStore<Preferences>,
) {

    /**
     * DataStore 中存储"当前激活的 AI 提供商 ID"的键。
     *
     * 使用 [stringPreferencesKey] 而非魔法字符串，享受编译期类型安全。
     */
    private companion object {
        val ACTIVE_PROVIDER_KEY = stringPreferencesKey("active_ai_provider_id")
    }

    // ──────────────────────────────────────
    // CRUD — 配置的管理
    // ──────────────────────────────────────

    /**
     * 新增 AI 服务商配置。
     *
     * [config.apiKey] 是明文，在 [toEntity] 转换时自动加密。
     * 若 ID 已存在则替换（由 DAO 的 [OnConflictStrategy.REPLACE] 保证）。
     */
    suspend fun insert(config: AIProviderConfig) {
        aiProviderConfigDao.insert(config.toEntity())
    }

    /**
     * 更新已有 AI 服务商配置。
     */
    suspend fun update(config: AIProviderConfig) {
        aiProviderConfigDao.update(config.toEntity())
    }

    /**
     * 清除当前激活的提供商 ID。
     *
     * 调用后 [activeProviderId] 与 [activeProvider] 发射 null，
     * 由 UI 引导用户重新选择。
     */
    suspend fun clearActiveProviderId() {
        dataStore.edit { prefs ->
            prefs.remove(ACTIVE_PROVIDER_KEY)
        }
    }

    /**
     * 删除 AI 服务商配置。
     *
     * 注意：不检查 [AIProviderConfig.isPreset]，
     * 是否允许删除预设配置由 UI 层控制（预设配置不显示删除按钮）。
     */
    suspend fun delete(config: AIProviderConfig) {
        aiProviderConfigDao.delete(config.toEntity())
        if (activeProviderId.first() == config.id) {
            clearActiveProviderId()
        }
    }

    /**
     * 获取所有已保存的 AI 服务商配置。
     *
     * 返回时 [AIProviderConfig.apiKey] 已解密为明文，可直接使用。
     */
    fun getAIProviders(): Flow<List<AIProviderConfig>> {
        return aiProviderConfigDao.getAll().map { list ->
            list.map { it.toModel() }
        }
    }

    /**
     * 根据 ID 获取单个配置。
     *
     * @return 配置（含已解密的 API Key），若不存在则返回 `null`
     */
    suspend fun getById(id: String): AIProviderConfig? {
        return aiProviderConfigDao.getById(id)?.toModel()
    }

    /**
     * 更新指定 AI 服务商已缓存的模型列表。
     *
     * @param id 配置唯一标识
     * @param cachedModels 缓存的模型名称列表
     */
    suspend fun updateCachedModels(id: String, cachedModels: List<String>) {
        val serialized = cachedModels.joinToString(",").ifBlank { null }
        aiProviderConfigDao.updateCachedModels(id, serialized)
    }

    // ──────────────────────────────────────
    // 激活管理 — "当前正在使用哪个服务商？"
    // ──────────────────────────────────────

    /**
     * 观察当前激活的提供商 ID。
     *
     * 返回 [Flow] 而非挂起函数，因为：
     * - ViewModel 通过 `stateIn()` 收集此 Flow，切换配置时 UI 自动刷新
     * - 可以 flatMapLatest 到 [getById] 构造实时的 [getActiveProvider]
     *
     * 若从未设置过激活 ID，返回 `null`。
     */
    val activeProviderId: Flow<String?> = dataStore.data.map { prefs ->
        prefs[ACTIVE_PROVIDER_KEY]
    }

    /**
     * 设置当前激活的提供商 ID。
     *
     * 只需调用一次，所有收集 [activeProviderId] 的 Flow 都会收到更新。
     */
    suspend fun setActiveProviderId(id: String) {
        dataStore.edit { prefs ->
            prefs[ACTIVE_PROVIDER_KEY] = id
        }
    }

    /**
     * 获取当前激活的完整配置。
     *
     * 这是最常用的方法——调用方不需要先拿 ID、再查配置。
     *
     * 内部逻辑:
     * ```
     * DataStore → 拿到 ID → Room 查询 → Entity → Model (自动解密 API Key)
     * ```
     *
     * 每个环节都可能产生 `null`：
     * - 未设置激活 ID
     * - ID 对应的配置已被删除
     * → 最终返回 `null`
     *
     * 对两个源头都响应式：DataStore 的激活 ID 切换、Room 侧配置行的字段修改
     * （baseUrl/apiKey 等）都会重发——引擎重建依赖后者。
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val activeProvider: Flow<AIProviderConfig?> = activeProviderId.flatMapLatest { id ->
        if (id == null) {
            flowOf(null)
        } else {
            // 挂到 Room 的响应式查询上：配置字段（baseUrl/apiKey 等）被修改时
            // 重发，下游（AgentEngine 重建）才能感知；getById 一次性查询做不到
            aiProviderConfigDao.getByIdFlow(id).map { it?.toModel() }
        }
    }
}