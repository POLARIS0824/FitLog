package com.example.fitlog.data.mapper

import com.example.fitlog.data.local.entity.plan.PlannedSessionEntity
import com.example.fitlog.data.local.entity.plan.WorkoutPlanEntity
import com.example.fitlog.data.local.relation.WorkoutPlanWithSessions
import com.example.fitlog.model.PlannedSession
import com.example.fitlog.model.WorkoutPlan
import com.example.fitlog.model.user.TrainingGoal

fun WorkoutPlanEntity.toModel(): WorkoutPlan {
    return WorkoutPlan(
        id = id,
        name = name,
        description = description,
        goal = goal?.let { raw -> TrainingGoal.entries.firstOrNull { it.name == raw } },
        durationWeeks = durationWeeks,
        sessionsPerWeek = sessionsPerWeek,
        isCustom = isCustom,
        createdAt = createdAt,
        rawPlanText = rawPlanText,
        sessions = emptyList(),
    )
}

fun WorkoutPlanWithSessions.toModel(): WorkoutPlan {
    return WorkoutPlan(
        id = plan.id,
        name = plan.name,
        description = plan.description,
        goal = plan.goal?.let { raw -> TrainingGoal.entries.firstOrNull { it.name == raw } },
        durationWeeks = plan.durationWeeks,
        sessionsPerWeek = plan.sessionsPerWeek,
        isCustom = plan.isCustom,
        createdAt = plan.createdAt,
        rawPlanText = plan.rawPlanText,
        // @Relation 不支持 orderBy（Room 内部查询序非 API 契约），与 WorkoutMapper
        // 的显式排序契约对称；savePlanWithSessions 的 REPLACE 会把被编辑的 session
        // 挪到表尾，不排序则计划详情页的课次顺序随编辑漂移
        sessions = sessions.sortedWith(compareBy({ it.weekNumber }, { it.dayNumber }))
            .map { it.toModel() },
    )
}

fun PlannedSessionEntity.toModel(): PlannedSession {
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

fun WorkoutPlan.toEntity(): WorkoutPlanEntity {
    return WorkoutPlanEntity(
        id = id,
        name = name,
        description = description,
        goal = goal?.name,
        durationWeeks = durationWeeks,
        sessionsPerWeek = sessionsPerWeek,
        isCustom = isCustom,
        createdAt = createdAt,
        rawPlanText = rawPlanText,
    )
}

fun PlannedSession.toEntity(planId: String): PlannedSessionEntity {
    return PlannedSessionEntity(
        id = id,
        planId = planId,
        name = name,
        description = description,
        dayNumber = dayNumber,
        weekNumber = weekNumber,
        targetDurationMinutes = targetDurationMinutes,
        exercises = exercises,
        completedWorkoutId = completedWorkoutId,
    )
}
