package com.example.fitlog.data.repository

import com.example.fitlog.data.local.dao.WorkoutPlanDao
import com.example.fitlog.data.mapper.toEntity
import com.example.fitlog.data.mapper.toModel
import com.example.fitlog.model.WorkoutPlan
import javax.inject.Inject

/**
 * 训练计划仓库。
 *
 * 管理计划的完整读写：计划 + 训练日（动作清单内嵌于训练日的 JSON 列）。
 */
class WorkoutPlanRepository @Inject constructor(
    private val workoutPlanDao: WorkoutPlanDao
) {
    /**
     * 事务级保存完整计划（计划 + 所有训练日）。
     */
    suspend fun save(workoutPlan: WorkoutPlan) = workoutPlanDao.savePlanWithSessions(
        plan = workoutPlan.toEntity(),
        sessions = workoutPlan.sessions.map { it.toEntity(workoutPlan.id) },
    )

    suspend fun delete(id: String) = workoutPlanDao.deletePlan(id)

    suspend fun getAllPlans(): List<WorkoutPlan> {
        return workoutPlanDao.getAllPlansWithDetails().map { it.toModel() }
    }

    suspend fun getPlanById(id: String): WorkoutPlan? {
        return workoutPlanDao.getPlanByIdWithDetails(id)?.toModel()
    }

    suspend fun markSessionCompleted(sessionId: String, workoutId: Long) =
        workoutPlanDao.markSessionCompleted(sessionId, workoutId)

    suspend fun unmarkSessionCompleted(sessionId: String) =
        workoutPlanDao.unmarkSessionCompleted(sessionId)
}
