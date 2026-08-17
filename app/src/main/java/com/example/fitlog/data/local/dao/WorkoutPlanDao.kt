package com.example.fitlog.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.fitlog.data.local.entity.plan.PlannedSessionEntity
import com.example.fitlog.data.local.entity.plan.WorkoutPlanEntity
import com.example.fitlog.data.local.relation.WorkoutPlanWithSessions
import kotlinx.coroutines.flow.Flow

/**
 * 训练计划 DAO，支持级联查询与操作。
 *
 * 动作清单内嵌在 [PlannedSessionEntity.exercises] JSON 列中，
 * 无需独立的动作级联操作。
 */
@Dao
interface WorkoutPlanDao {

    /**
     * 插入训练计划，主键冲突时忽略。
     *
     * 不使用 REPLACE：SQLite 的 REPLACE = DELETE + INSERT，父行被删除会
     * 触发 planned_sessions.planId 的 CASCADE，静默清空该计划全部训练日
     * （含用户积累的完成记录 completedWorkoutId）。
     *
     * @return 新行 rowId；冲突被忽略时返回 -1
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPlanIgnore(plan: WorkoutPlanEntity): Long

    /**
     * 更新已有训练计划。
     *
     * @return 受影响行数；0 表示该计划不存在
     */
    @Update
    suspend fun updatePlan(plan: WorkoutPlanEntity): Int

    /**
     * 插入或替换训练日。
     *
     * planned_sessions 没有子表，REPLACE 不会误伤级联数据；
     * 行内的 completedWorkoutId 由 [savePlanWithSessions] 在写入前合并保留。
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
     * 查询训练计划总数，用于判断预置计划是否已导入
     * （WorkoutPlanSeeder 的"版本最新且表非空"短路条件）。
     */
    @Query("SELECT COUNT(*) FROM workout_plans")
    suspend fun getPlanCount(): Int

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

    /**
     * 按 ID 删除单个训练日（编辑计划时清理被移除的训练日）。
     */
    @Query("DELETE FROM planned_sessions WHERE id = :sessionId")
    suspend fun deleteSessionById(sessionId: String)

    @Transaction
    @Query("SELECT * FROM workout_plans ORDER BY createdAt DESC")
    suspend fun getAllPlansWithDetails(): List<WorkoutPlanWithSessions>

    @Transaction
    @Query("SELECT * FROM workout_plans WHERE id = :id")
    suspend fun getPlanByIdWithDetails(id: String): WorkoutPlanWithSessions?

    /**
     * 观察所有训练计划（含级联训练日），按创建日期降序（Plan 页计划库）。
     */
    @Transaction
    @Query("SELECT * FROM workout_plans ORDER BY createdAt DESC")
    fun getAllPlansWithDetailsFlow(): Flow<List<WorkoutPlanWithSessions>>

    /**
     * 观察单个训练计划（含级联训练日）。
     * 训练日完成标记变化时 Flow 重新发射（进度展示）。
     */
    @Transaction
    @Query("SELECT * FROM workout_plans WHERE id = :id")
    fun getPlanByIdWithDetailsFlow(id: String): Flow<WorkoutPlanWithSessions?>

    /**
     * 观察某计划下一个未完成的训练日（Today 待练卡 / Plan 页进度）。
     */
    @Query(
        """
        SELECT * FROM planned_sessions
        WHERE planId = :planId AND completedWorkoutId IS NULL
        ORDER BY weekNumber, dayNumber LIMIT 1
        """,
    )
    fun getNextIncompleteSession(planId: String): Flow<PlannedSessionEntity?>

    /**
     * 事务级保存完整计划。
     *
     * 语义：
     * 1. plan 主表按"存在则 UPDATE、不存在则 INSERT"写入，绝不走 REPLACE，
     *    避免父行 DELETE 触发 CASCADE 清空 planned_sessions；
     * 2. sessions 与库中现有数据做 diff：本次未包含的训练日被删除（编辑计划
     *    删减训练日时不留孤儿数据）；
     * 3. 已存在的训练日保留库中的 completedWorkoutId（完成标记的唯一事实源
     *    是 markSessionCompleted/unmarkSessionCompleted），调用方传入的对象
     *    不携带、也不允许覆盖该字段。
     */
    @Transaction
    suspend fun savePlanWithSessions(
        plan: WorkoutPlanEntity,
        sessions: List<PlannedSessionEntity>,
    ) {
        if (updatePlan(plan) == 0) {
            insertPlanIgnore(plan)
        }

        val existing = getSessionsByPlanId(plan.id)
        val incomingIds = sessions.map { it.id }.toSet()
        existing.filter { it.id !in incomingIds }.forEach { deleteSessionById(it.id) }

        val completionById = existing.associate { it.id to it.completedWorkoutId }
        val merged = sessions.map { s ->
            s.copy(completedWorkoutId = completionById[s.id])
        }
        if (merged.isNotEmpty()) {
            insertSessions(merged)
        }
    }
}
