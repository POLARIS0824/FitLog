package com.example.fitlog.data.repository

import com.example.fitlog.model.ai.AgentStepType
import com.example.fitlog.model.ai.ChatThreadMessage

/**
 * 聊天持久化仓库：收口 chat_messages / agent_steps 两张表的读写与
 * 消息 ↔ 时间线的挂载规则（UI 面向 [ChatThreadMessage] 领域模型，
 * 不再感知 DAO/entity——此前这些规则散落在 ChatViewModel 内）。
 */
interface ChatRepository {

    /**
     * 批量 seed 的消息载荷（历史回放一次性落库用，无 runId/时长）。
     */
    data class SeedMessage(val role: String, val content: String, val createdAt: Long)

    /**
     * 写入一条消息（用户消息 / assistant 最终回答 / 历史回放 seed 共用）。
     *
     * @return 自增 id（LazyColumn 稳定 key 的事实源）
     */
    suspend fun insertMessage(
        role: String,
        content: String,
        runId: String? = null,
        durationMs: Long? = null,
        createdAt: Long,
    ): Long

    /**
     * 写入一条 Agent 过程步骤（即时落库，挂到 runId 对应的最终回答上）。
     *
     * @return 自增 id
     */
    suspend fun insertStep(
        runId: String,
        order: Int,
        type: AgentStepType,
        toolKey: String?,
        label: String,
        detail: String?,
        elapsedMs: Long,
        createdAt: Long,
    ): Long

    /**
     * 加载完整对话线程：消息按落库时间升序，assistant 消息挂载其
     * runId 对应的全部步骤（按步骤序号升序）。
     */
    suspend fun loadThread(): List<ChatThreadMessage>

    /**
     * 事务性批量写入 seed 消息（ADK 历史一次性回放）。必须整批原子：
     * 中途崩溃留下半截历史时，本地非空的判断会永久跳过补种，残缺被固化。
     *
     * @return 与入参顺序一致的自增 id 列表
     */
    suspend fun insertMessages(messages: List<SeedMessage>): List<Long>

    /**
     * 删除某轮运行的全部步骤行。运行作废（错误/停止/无最终回答）后，
     * 其步骤永远失去挂载点，保留只会无限累积成孤儿数据。
     */
    suspend fun deleteStepsByRun(runId: String)

    /** 消息总数（判断是否需要从 ADK 历史做一次性 seed）。 */
    suspend fun count(): Long

    /** 清空消息与步骤两张表（清空对话时与 ADK 会话删除同批执行）。 */
    suspend fun clearAll()
}
