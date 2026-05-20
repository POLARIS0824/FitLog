package com.example.fitlog.data.mapper

import com.example.fitlog.data.local.entity.plan.WorkoutPlanEntity
import com.example.fitlog.model.Difficulty
import com.example.fitlog.model.WorkoutPlan
import com.example.fitlog.model.user.TrainingGoal

fun WorkoutPlanEntity.toModel() : WorkoutPlan {
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
        // TODO: sessions 计划中的所有训练日
        sessions = emptyList(),
    )
}

fun WorkoutPlan.toEntity() : WorkoutPlanEntity {
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