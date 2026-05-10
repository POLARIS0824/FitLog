package com.example.fitlog.domain.repository

import com.example.fitlog.domain.model.PlannedSession
import com.example.fitlog.domain.model.WorkoutPlan

/**
 * 训练计划仓库接口，负责 [WorkoutPlan] 的持久化、查询及活跃计划管理。
 */
interface WorkoutPlanRepository {

    /**
     * 获取所有训练计划（不含级联 session 详情）。
     */
    suspend fun getAllPlans(): List<WorkoutPlan>

    /**
     * 根据 ID 获取完整训练计划（含级联 session 与 exercise 详情）。
     */
    suspend fun getPlanById(id: String): WorkoutPlan?

    /**
     * 保存训练计划（级联保存 session 与 exercise）。
     *
     * 若计划已存在则覆盖更新。
     */
    suspend fun savePlan(plan: WorkoutPlan)

    /**
     * 删除训练计划（级联删除关联的 session 与 exercise）。
     */
    suspend fun deletePlan(id: String)

    /**
     * 获取当前活跃计划。
     *
     * 活跃计划 ID 通过 DataStore 管理。
     */
    suspend fun getActivePlan(): WorkoutPlan?

    /**
     * 设置活跃计划。
     *
     * @param id 计划 ID，设为 null 表示取消活跃计划。
     */
    suspend fun setActivePlan(id: String?)

    /**
     * 根据计划 ID 获取其下的所有训练日。
     */
    suspend fun getSessionsByPlanId(planId: String): List<PlannedSession>

    /**
     * 标记训练日已完成，关联到实际训练记录。
     *
     * @param sessionId 计划中的训练日 ID
     * @param workoutId 实际完成的 workouts 表记录 ID
     */
    suspend fun markSessionCompleted(sessionId: String, workoutId: Long)
}
