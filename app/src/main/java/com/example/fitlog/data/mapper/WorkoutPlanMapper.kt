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
        goal = goal?.let { TrainingGoal.valueOf(it) },
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
        goal = plan.goal?.let { TrainingGoal.valueOf(it) },
        durationWeeks = plan.durationWeeks,
        sessionsPerWeek = plan.sessionsPerWeek,
        isCustom = plan.isCustom,
        createdAt = plan.createdAt,
        rawPlanText = plan.rawPlanText,
        sessions = sessions.map { it.toModel() },
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
