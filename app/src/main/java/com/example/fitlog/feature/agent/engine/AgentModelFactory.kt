package com.example.fitlog.feature.agent.engine

import com.example.fitlog.data.remote.AIApi
import com.example.fitlog.model.ai.AIProviderConfig
import com.google.adk.kt.models.Gemini
import com.google.adk.kt.models.Model

/**
 * 根据用户激活的 AI 服务商配置创建 ADK [Model] 实例。
 *
 * ## 选择策略
 *
 * 单一判定口径来自 [AIProviderConfig.usesNativeGeminiEndpoint]（模型层属性，
 * 与 CoachInsight 路径共享，杜绝漂移）：
 *
 * - **Gemini 官方端点**：使用 ADK 内置 [Gemini]（原生 GenAI SDK，协议能力最全，
 *   支持流式/上下文缓存）
 * - **其余一切**（OpenAI / Moonshot / DeepSeek / SiliconFlow / Azure / 自定义网关）：
 *   使用 [OpenAiCompatibleModel] 复用现有 [AIApi] 链路与加密的 API Key
 *
 * 该工厂随激活配置变化调用，AgentEngine 据此在配置切换时重建 agent/runner。
 */
object AgentModelFactory {

    /**
     * 为指定配置创建模型实例。
     *
     * @param config 当前激活的配置（apiKey 已解密）
     * @param aiApi OpenAI 兼容网络层
     * @return 该配置对应的 [Model]
     */
    fun create(config: AIProviderConfig, aiApi: AIApi): Model =
        if (config.usesNativeGeminiEndpoint) {
            Gemini(name = config.model, apiKey = config.apiKey)
        } else {
            OpenAiCompatibleModel(config = config, aiApi = aiApi)
        }
}
