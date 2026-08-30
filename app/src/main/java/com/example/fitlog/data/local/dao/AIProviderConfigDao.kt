package com.example.fitlog.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.fitlog.data.local.entity.AIProviderConfigEntity
import kotlinx.coroutines.flow.Flow

/**
 * AI 提供商配置（[AIProviderConfigEntity]）的数据访问对象。
 */
@Dao
interface AIProviderConfigDao {

    /**
     * 插入或替换配置。
     *
     * @param entity 待插入的配置实体
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: AIProviderConfigEntity)

    /**
     * 更新已有配置。
     *
     * @param entity 待更新的配置实体
     */
    @Update
    suspend fun update(entity: AIProviderConfigEntity)

    /**
     * 删除配置。
     *
     * @param entity 待删除的配置实体
     */
    @Delete
    suspend fun delete(entity: AIProviderConfigEntity)

    /**
     * 按 ID 删除配置（仅需主键，无需构造实体）。
     *
     * @param id 配置唯一标识
     * @return 受影响行数（0 = 该 id 不存在）
     */
    @Query("DELETE FROM ai_provider_configs WHERE id = :id")
    suspend fun deleteById(id: String): Int

    /**
     * 根据 ID 查询配置。
     *
     * @param id 配置唯一标识
     * @return 匹配的配置，若不存在则返回 null
     */
    @Query("SELECT * FROM ai_provider_configs WHERE id = :id")
    suspend fun getById(id: String): AIProviderConfigEntity?

    /**
     * 按 ID 观察配置行（响应式：配置字段被修改时重发）。
     *
     * [getById] 是一次性查询，activeProvider 只有挂到本 Flow 上才能感知
     * baseUrl/apiKey 等字段的更新（引擎重建依赖此重发）。
     *
     * @param id 配置唯一标识
     */
    @Query("SELECT * FROM ai_provider_configs WHERE id = :id")
    fun getByIdFlow(id: String): Flow<AIProviderConfigEntity?>

    /**
     * 查询所有配置。
     *
     * @return 配置列表
     */
    @Query("SELECT * FROM ai_provider_configs")
    fun getAll(): Flow<List<AIProviderConfigEntity>>

    /**
     * 更新指定配置的缓存模型列表。
     *
     * @param id 配置唯一标识
     * @param cachedModels 以逗号分隔的模型名称列表
     */
    @Query("UPDATE ai_provider_configs SET cachedModels = :cachedModels WHERE id = :id")
    suspend fun updateCachedModels(id: String, cachedModels: String?)
}
