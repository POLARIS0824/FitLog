package com.example.fitlog.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.fitlog.data.local.dao.WorkoutPlanDao
import com.example.fitlog.data.local.entity.plan.PlannedExerciseEntity
import com.example.fitlog.data.local.entity.plan.PlannedSessionEntity
import com.example.fitlog.data.local.entity.plan.WorkoutPlanEntity
import com.example.fitlog.domain.model.Difficulty
import com.example.fitlog.domain.model.PlannedExercise
import com.example.fitlog.domain.model.PlannedSession
import com.example.fitlog.domain.model.user.TrainingGoal
import com.example.fitlog.domain.model.WorkoutPlan
import com.example.fitlog.domain.repository.WorkoutPlanRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject

/**
 * [WorkoutPlanRepository] 的 Room + DataStore 实现。
 *
 * 计划数据持久化到 Room（级联保存 sessions 与 exercises）。
 * 当前活跃计划 ID 保存到 DataStore。
 */
class WorkoutPlanRepositoryImpl @Inject constructor(
    private val dao: WorkoutPlanDao,
    private val dataStore: DataStore<Preferences>,
) : WorkoutPlanRepository {

    private companion object {
        val ACTIVE_WORKOUT_PLAN_ID = stringPreferencesKey("active_workout_plan_id")
    }

    override suspend fun getAllPlans(): List<WorkoutPlan> {
        return dao.getAllPlans().map { it.toDomain(sessions = emptyList()) }
    }

    override suspend fun getPlanById(id: String): WorkoutPlan? {
        val planEntity = dao.getPlanById(id) ?: return null
        val sessions = buildSessions(id)
        return planEntity.toDomain(sessions)
    }

    override suspend fun savePlan(plan: WorkoutPlan) {
        val planEntity = plan.toEntity()
        val sessionEntities = plan.sessions.map { it.toEntity(planId = plan.id) }
        val exerciseEntities = plan.sessions.flatMap { session ->
            session.exercises.map { it.toEntity(sessionId = session.id) }
        }
        dao.savePlanWithSessions(planEntity, sessionEntities, exerciseEntities)
    }

    override suspend fun deletePlan(id: String) {
        dao.deletePlan(id)
    }

    override suspend fun getActivePlan(): WorkoutPlan? {
        val activeId = dataStore.data.map { it[ACTIVE_WORKOUT_PLAN_ID] }.first()
        return activeId?.let { getPlanById(it) }
    }

    override suspend fun setActivePlan(id: String?) {
        dataStore.edit { prefs ->
            if (id != null) {
                prefs[ACTIVE_WORKOUT_PLAN_ID] = id
            } else {
                prefs.remove(ACTIVE_WORKOUT_PLAN_ID)
            }
        }
    }

    override suspend fun getSessionsByPlanId(planId: String): List<PlannedSession> {
        return buildSessions(planId)
    }

    override suspend fun markSessionCompleted(sessionId: String, workoutId: Long) {
        dao.markSessionCompleted(sessionId, workoutId)
    }

    /**
     * 根据计划 ID 级联构建 [PlannedSession] 列表（含 exercises）。
     */
    private suspend fun buildSessions(planId: String): List<PlannedSession> {
        return dao.getSessionsByPlanId(planId).map { sessionEntity ->
            val exercises = dao.getExercisesBySessionId(sessionEntity.id)
                .map { it.toDomain() }
            sessionEntity.toDomain(exercises)
        }
    }

    // ==================== Entity → Domain ====================

    private fun WorkoutPlanEntity.toDomain(sessions: List<PlannedSession>): WorkoutPlan {
        return WorkoutPlan(
            id = id,
            name = name,
            description = description,
            goal = goal?.let { TrainingGoal.valueOf(it) },
            difficulty = difficulty?.let { Difficulty.valueOf(it) },
            durationWeeks = durationWeeks,
            sessionsPerWeek = sessionsPerWeek,
            isCustom = isCustom,
            createdAt = LocalDate.parse(createdAt),
            sessions = sessions,
        )
    }

    private fun PlannedSessionEntity.toDomain(exercises: List<PlannedExercise>): PlannedSession {
        return PlannedSession(
            id = id,
            name = name,
            description = description,
            dayNumber = dayNumber,
            weekNumber = weekNumber,
            targetDurationMinutes = targetDurationMinutes,
            exercises = exercises,
            completedWorkoutId = completedWorkoutId,
        )
    }

    private fun PlannedExerciseEntity.toDomain(): PlannedExercise {
        return PlannedExercise(
            id = id,
            exerciseKey = exerciseKey,
            exerciseName = exerciseName,
            targetSets = targetSets,
            targetRepsMin = targetRepsMin,
            targetRepsMax = targetRepsMax,
            targetWeightKg = targetWeightKg,
            targetRpe = targetRpe,
            restSeconds = restSeconds,
            notes = notes,
            order = order,
        )
    }

    // ==================== Domain → Entity ====================

    private fun WorkoutPlan.toEntity(): WorkoutPlanEntity {
        return WorkoutPlanEntity(
            id = id,
            name = name,
            description = description,
            goal = goal?.name,
            difficulty = difficulty?.name,
            durationWeeks = durationWeeks,
            sessionsPerWeek = sessionsPerWeek,
            isCustom = isCustom,
            createdAt = createdAt.toString(),
        )
    }

    private fun PlannedSession.toEntity(planId: String): PlannedSessionEntity {
        return PlannedSessionEntity(
            id = id,
            planId = planId,
            name = name,
            description = description,
            dayNumber = dayNumber,
            weekNumber = weekNumber,
            targetDurationMinutes = targetDurationMinutes,
            completedWorkoutId = completedWorkoutId,
        )
    }

    private fun PlannedExercise.toEntity(sessionId: String): PlannedExerciseEntity {
        return PlannedExerciseEntity(
            id = id,
            sessionId = sessionId,
            exerciseKey = exerciseKey,
            exerciseName = exerciseName,
            targetSets = targetSets,
            targetRepsMin = targetRepsMin,
            targetRepsMax = targetRepsMax,
            targetWeightKg = targetWeightKg,
            targetRpe = targetRpe,
            restSeconds = restSeconds,
            notes = notes,
            order = order,
        )
    }
}
