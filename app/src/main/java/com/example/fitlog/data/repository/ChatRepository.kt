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

    /** 消息总数（判断是否需要从 ADK 历史做一次性 seed）。 */
    suspend fun count(): Long

    /** 清空消息与步骤两张表（清空对话时与 ADK 会话删除同批执行）。 */
    suspend fun clearAll()
}
