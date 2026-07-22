package com.example.fitlog.data.agent

import com.example.fitlog.model.ai.AgentTool
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Agent 工具注册表。
 *
 * 集中持有所有可供 LLM 调用的 [AgentTool]。
 * 工具通过 Hilt multibinding（@IntoSet）注册——新增工具时在
 * [com.example.fitlog.di.AgentModule] 中追加一个 @Binds 即可。
 */
@Singleton
class AgentToolRegistry @Inject constructor(
    agentTools: Set<@JvmSuppressWildcards AgentTool>,
) {

    private val tools: Map<String, AgentTool> = agentTools.associateBy { it.name }

    /**
     * 全部已注册工具（用于构造发给 LLM 的 tools 声明）。
     */
    fun all(): List<AgentTool> = tools.values.toList()

    /**
     * 按名字查找工具；模型可能虚构工具名，找不到时返回 null 由调用方兜底。
     */
    fun find(name: String): AgentTool? = tools[name]
}
