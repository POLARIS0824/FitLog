package com.example.fitlog.data.mapper

import com.example.fitlog.data.local.entity.plan.WorkoutPlanEntity
import com.example.fitlog.data.local.relation.PlannedSessionWithExercises
import com.example.fitlog.data.local.relation.WorkoutPlanWithSessions
import com.example.fitlog.model.Difficulty
import com.example.fitlog.model.PlannedExercise
import com.example.fitlog.model.PlannedSession
import com.example.fitlog.model.WorkoutPlan
import com.example.fitlog.model.user.TrainingGoal

fun WorkoutPlanEntity.toModel(): WorkoutPlan {
    return WorkoutPlan(
        id = id,
        name = name,
        description = description,
        goal = goal?.let { TrainingGoal.valueOf(it) },
        difficulty = difficulty?.let { Difficulty.valueOf(it) },
        durationWeeks = durationWeeks,
        sessionsPerWeek = sessionsPerWeek,
        isCustom = isCustom,
        createdAt = createdAt,
        sessions = emptyList(),
    )
}

fun WorkoutPlanWithSessions.toModel(): WorkoutPlan {
    return WorkoutPlan(
        id = plan.id,
        name = plan.name,
        description = plan.description,
        goal = plan.goal?.let { TrainingGoal.valueOf(it) },
        difficulty = plan.difficulty?.let { Difficulty.valueOf(it) },
        durationWeeks = plan.durationWeeks,
        sessionsPerWeek = plan.sessionsPerWeek,
        isCustom = plan.isCustom,
        createdAt = plan.createdAt,
        sessions = sessions.map { it.toModel() },
    )
}

fun PlannedSessionWithExercises.toModel(): PlannedSession {
    return PlannedSession(
        id = session.id,
        name = session.name,
        description = session.description,
        dayNumber = session.dayNumber,
        weekNumber = session.weekNumber,
        targetDurationMinutes = session.targetDurationMinutes,
        exercises = exercises.map { it.toModel() },
        completedWorkoutId = session.completedWorkoutId,
    )
}

fun com.example.fitlog.data.local.entity.plan.PlannedExerciseEntity.toModel(): PlannedExercise {
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

fun WorkoutPlan.toEntity(): WorkoutPlanEntity {
    return WorkoutPlanEntity(
        id = id,
        name = name,
        description = description,
        goal = goal?.name,
        difficulty = difficulty?.name,
        durationWeeks = durationWeeks,
        sessionsPerWeek = sessionsPerWeek,
        isCustom = isCustom,
        createdAt = createdAt,
    )
}