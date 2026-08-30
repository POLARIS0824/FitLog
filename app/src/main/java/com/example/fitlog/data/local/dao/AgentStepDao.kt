package com.example.fitlog.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.fitlog.data.local.entity.chat.AgentStepEntity

/**
 * Agent 过程步骤（[AgentStepEntity]）的数据访问对象。
 */
@Dao
interface AgentStepDao {

    /**
     * 全部步骤按 runId + 步骤序号升序（回放时按 runId 分组挂载到消息）。
     */
    @Query("SELECT * FROM agent_steps ORDER BY runId ASC, stepOrder ASC, id ASC")
    suspend fun getAll(): List<AgentStepEntity>

    /**
     * 某轮运行的步骤，按步骤序号升序。
     */
    @Query("SELECT * FROM agent_steps WHERE runId = :runId ORDER BY stepOrder ASC, id ASC")
    suspend fun getByRun(runId: String): List<AgentStepEntity>

    /**
     * 写入一条步骤，返回自增 id。
     */
    @Insert
    suspend fun insert(entity: AgentStepEntity): Long

    /**
     * 清空全部步骤（清空对话时与消息表同批执行）。
     */
    @Query("DELETE FROM agent_steps")
    suspend fun clearAll()
}
