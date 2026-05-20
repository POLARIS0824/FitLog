package com.example.fitlog.data.repository

import com.example.fitlog.data.local.dao.AIProviderConfigDao
import javax.inject.Inject

/**
 * AI 服务商配置仓库
 * 使用 AIProviderConfigDao 与 DataStore。
 * 读取/写入 AI 服务商配置，并在保存时调用 KeystoreManager 加密 API Key，读取时自动解密；
 * 管理当前激活的配置 ID
 */
class AIProviderConfigRepository @Inject constructor(
    private val aiProviderConfigDao: AIProviderConfigDao,
    // TODO: 加密 Datastore
)