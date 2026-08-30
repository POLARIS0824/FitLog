package com.example.fitlog.data.local.entity.chat

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * AI 教练对话消息的数据库实体。
 *
 * 聊天 UI 的持久化事实源（ADK 会话库只服务模型上下文，不服务 UI 回放）。
 * 仅存用户消息与 assistant 最终回答——工具调用轮的中间过程落 [AgentStepEntity]，
 * 经 [runId] 与产生它的那轮 assistant 回答关联。
 *
 * @property id 自增主键（LazyColumn 稳定 key）
 * @property role 消息角色："user" / "assistant"
 * @property content 消息文本内容
 * @property runId 关联的 Agent 运行 id（assistant 消息非空；用户消息为 null）
 * @property durationMs 产生该回答的 Agent 运行活跃耗时（毫秒，确认等待不计入）；历史 seed 数据为 null
 * @property createdAt 落库时间（epoch 毫秒，排序用）
 */
@Entity(
    tableName = "chat_messages",
    indices = [Index(value = ["runId"])],
)
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val role: String,
    val content: String,
    val runId: String?,
    val durationMs: Long?,
    val createdAt: Long,
)
