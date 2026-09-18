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
     * 删除某轮运行的全部步骤（运行作废/中断时防孤儿步骤无限累积：
     * 无最终回答挂载的步骤行永远无法再被读出，只能徒增表体积）。
     */
    @Query("DELETE FROM agent_steps WHERE runId = :runId")
    suspend fun deleteByRun(runId: String)

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
