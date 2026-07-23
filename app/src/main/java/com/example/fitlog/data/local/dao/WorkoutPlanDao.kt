package com.example.fitlog.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.fitlog.data.local.entity.plan.PlannedSessionEntity
import com.example.fitlog.data.local.entity.plan.WorkoutPlanEntity
import com.example.fitlog.data.local.relation.WorkoutPlanWithSessions

/**
 * 训练计划 DAO，支持级联查询与操作。
 *
 * 动作清单内嵌在 [PlannedSessionEntity.exercises] JSON 列中，
 * 无需独立的动作级联操作。
 */
@Dao
interface WorkoutPlanDao {

    /**
     * 插入或替换训练计划。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlan(plan: WorkoutPlanEntity)

    /**
     * 插入或替换训练日。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessions(sessions: List<PlannedSessionEntity>)

    /**
     * 获取所有训练计划（不含级联详情）。
     */
    @Query("SELECT * FROM workout_plans ORDER BY createdAt DESC")
    suspend fun getAllPlans(): List<WorkoutPlanEntity>

    /**
     * 根据 ID 获取单个训练计划。
     */
    @Query("SELECT * FROM workout_plans WHERE id = :id")
    suspend fun getPlanById(id: String): WorkoutPlanEntity?

    /**
     * 获取某计划下的所有训练日。
     */
    @Query("SELECT * FROM planned_sessions WHERE planId = :planId ORDER BY weekNumber, dayNumber")
    suspend fun getSessionsByPlanId(planId: String): List<PlannedSessionEntity>

    /**
     * 删除训练计划（级联删除由外键约束处理）。
     */
    @Query("DELETE FROM workout_plans WHERE id = :id")
    suspend fun deletePlan(id: String)

    /**
     * 标记训练日已完成。
     */
    @Query("UPDATE planned_sessions SET completedWorkoutId = :workoutId WHERE id = :sessionId")
    suspend fun markSessionCompleted(sessionId: String, workoutId: Long)

    /**
     * 取消训练日完成标记。
     */
    @Query("UPDATE planned_sessions SET completedWorkoutId = NULL WHERE id = :sessionId")
    suspend fun unmarkSessionCompleted(sessionId: String)

    /**
     * 删除某计划下的所有训练日。
     */
    @Query("DELETE FROM planned_sessions WHERE planId = :planId")
    suspend fun deleteSessionsByPlanId(planId: String)

    @Transaction
    @Query("SELECT * FROM workout_plans ORDER BY createdAt DESC")
    suspend fun getAllPlansWithDetails(): List<WorkoutPlanWithSessions>

    @Transaction
    @Query("SELECT * FROM workout_plans WHERE id = :id")
    suspend fun getPlanByIdWithDetails(id: String): WorkoutPlanWithSessions?

    /**
     * 事务级保存完整计划。
     *
     * 先保存 plan，再保存 sessions（动作清单内嵌于 session）。
     */
    @Transaction
    suspend fun savePlanWithSessions(
        plan: WorkoutPlanEntity,
        sessions: List<PlannedSessionEntity>,
    ) {
        insertPlan(plan)
        if (sessions.isNotEmpty()) {
            insertSessions(sessions)
        }
    }
}
