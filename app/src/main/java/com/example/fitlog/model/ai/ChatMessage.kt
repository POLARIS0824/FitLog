package com.example.fitlog.model.ai

/**
 * AI 对话中的单条消息。
 *
 * @param role 消息角色，如 "system"、"user"、"assistant"
 * @param content 消息文本内容
 * @param id 本地展示用唯一标识（作为 LazyColumn 的稳定 key，支撑删除/流式更新）；
 *     由调用方在创建/落屏时分配，网络往返（[com.example.fitlog.data.mapper.ChatMessageMapper]）保留默认值 0
 */
data class ChatMessage(
    val role: String,
    val content: String,
    val id: Long = 0L,
)

/**
 * 聊天线程中的一条消息（含挂载的过程时间线），聊天持久化的领域模型。
 *
 * 由 [com.example.fitlog.data.repository.ChatRepository.loadThread] 组装：
 * 消息本体与按 runId 挂载的步骤分别来自 chat_messages / agent_steps 两表。
 * UI 层直接渲染本模型（消息气泡 + 可展开时间线）。
 *
 * @property id 消息唯一 id（Room 自增，LazyColumn 稳定 key）
 * @property role 消息角色："user" / "assistant"
 * @property content 消息文本内容
 * @property steps 产生该回答的 Agent 运行步骤（按时间升序；用户消息恒为空）
 * @property durationMs 该轮 Agent 的活跃耗时（毫秒，确认等待不计入）；历史 seed 数据为 null
 */
data class ChatThreadMessage(
    val id: Long,
    val role: String,
    val content: String,
    val steps: List<AgentStep> = emptyList(),
    val durationMs: Long? = null,
)