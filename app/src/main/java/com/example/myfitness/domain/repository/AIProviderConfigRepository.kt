package com.example.myfitness.domain.repository

import com.example.myfitness.domain.model.AIProviderConfig

/**
 * AI 提供商配置的领域层仓库接口。
 */
interface AIProviderConfigRepository {

    /**
     * 获取所有已保存的配置。
     *
     * @return 配置列表
     */
    suspend fun getAll(): List<AIProviderConfig>

    /**
     * 根据 ID 查询配置。
     *
     * @param id 配置唯一标识
     * @return 匹配的配置，若不存在则返回 null
     */
    suspend fun getById(id: String): AIProviderConfig?

    /**
     * 保存配置。新增或更新均走此方法。
     *
     * @param config 待保存的配置
     */
    suspend fun save(config: AIProviderConfig)

    /**
     * 删除指定配置。
     *
     * @param id 配置唯一标识
     */
    suspend fun delete(id: String)

    /**
     * 获取当前激活的配置 ID。
     *
     * @return 当前激活配置 ID，若未设置则返回 null
     */
    suspend fun getActiveId(): String?

    /**
     * 设置当前激活的配置 ID。
     *
     * @param id 配置唯一标识
     */
    suspend fun setActiveId(id: String)
}
