package com.example.myfitness.domain.model

import java.time.LocalDate

data class DailyCheckIn(
    val id: Long,
    val date: LocalDate,
    val exercises: List<ExerciseEntry>,
    val sourceFileName: String? = null,
)

/**
 * 动作名称以及组列表
 */
data class ExerciseEntry(
    val name: String,
    val sets: List<WorkoutSet>,
)

/**
 * 每组重量以及次数
 */
data class WorkoutSet(
    val weightKg: Float,
    val reps: Int,
)
