package com.example.fitlog.data.repository

import androidx.room.withTransaction
import com.example.fitlog.data.local.AppDatabase
import com.example.fitlog.data.local.dao.AgentStepDao
import com.example.fitlog.data.local.dao.ChatMessageDao
import com.example.fitlog.data.local.entity.chat.AgentStepEntity
import com.example.fitlog.data.local.entity.chat.ChatMessageEntity
import com.example.fitlog.model.ai.AgentStep
import com.example.fitlog.model.ai.AgentStepType
import com.example.fitlog.model.ai.ChatThreadMessage
import javax.inject.Inject

/**
 * [ChatRepository] 的 Room 实现：消息与步骤两表的读写、自增 id 分配、
 * runId 挂载规则全部收口于此。
 */
class RoomChatRepository @Inject constructor(
    private val chatMessageDao: ChatMessageDao,
    private val agentStepDao: AgentStepDao,
    private val db: AppDatabase,
) : ChatRepository {

    /** {@inheritDoc} */
    override suspend fun insertMessage(
        role: String,
        content: String,
        runId: String?,
        durationMs: Long?,
        createdAt: Long,
    ): Long = chatMessageDao.insert(
        ChatMessageEntity(
            role = role,
            content = content,
            runId = runId,
            durationMs = durationMs,
            createdAt = createdAt,
        ),
    )

    /** {@inheritDoc} */
    override suspend fun insertStep(
        runId: String,
        order: Int,
        type: AgentStepType,
        toolKey: String?,
        label: String,
        detail: String?,
        elapsedMs: Long,
        createdAt: Long,
    ): Long = agentStepDao.insert(
        AgentStepEntity(
            runId = runId,
            stepOrder = order,
            type = type.storageValue,
            toolKey = toolKey,
            label = label,
            detail = detail,
            elapsedMs = elapsedMs,
            createdAt = createdAt,
        ),
    )

    /** {@inheritDoc} */
    override suspend fun loadThread(): List<ChatThreadMessage> {
        val messages = chatMessageDao.getAll()
        val stepsByRun = agentStepDao.getAll().groupBy { it.runId }
        return messages.map { entity ->
            ChatThreadMessage(
                id = entity.id,
                role = entity.role,
                content = entity.content,
                durationMs = entity.durationMs,
                steps = entity.runId
                    ?.let { runId ->
                        stepsByRun[runId].orEmpty().map { step ->
                            AgentStep(
                                id = step.id,
                                type = AgentStepType.fromStorageValue(step.type),
                                toolKey = step.toolKey,
                                label = step.label,
                                detail = step.detail,
                                elapsedMs = step.elapsedMs,
                            )
                        }
                    }
                    ?: emptyList(),
            )
        }
    }

    /** {@inheritDoc} */
    override suspend fun count(): Long = chatMessageDao.count()

    /** {@inheritDoc} */
    override suspend fun insertMessages(messages: List<ChatRepository.SeedMessage>): List<Long> =
        db.withTransaction {
            chatMessageDao.insertAll(
                messages.map { seed ->
                    ChatMessageEntity(
                        role = seed.role,
                        content = seed.content,
                        runId = null,
                        durationMs = null,
                        createdAt = seed.createdAt,
                    )
                },
            )
        }

    /** {@inheritDoc} */
    override suspend fun deleteStepsByRun(runId: String) = agentStepDao.deleteByRun(runId)

    /** {@inheritDoc} */
    override suspend fun clearAll() = db.withTransaction {
        // 两表"同批"清空须在一个事务内：步骤行只经消息行的 runId 挂载，
        // 消息已删而步骤残留时孤儿数据永久无法恢复（全库唯一未入事务的
        // 多表写，与 WorkoutRepository 的级联写同一约束）
        chatMessageDao.clearAll()
        agentStepDao.clearAll()
    }
}
