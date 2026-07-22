package com.example.fitlog.model

import java.time.LocalDate

/**
 * Workout -> ExerciseLog -> SetLog
 */

data class Workout(
    val id: Long,
    val userId: Long,
    val date: LocalDate,
    val exercises: List<ExerciseLog>,
    val feelings: String?,
    val sourceFileName: String? = null,
    val rawContent: String? = null,
)

/**
 * 动作名称以及组列表
 */
data class ExerciseLog(
    val name: String,
    val exerciseKey: String? = null,
    val sets: List<SetLog>,
)

/**
 * 每组重量以及次数
 */
data class SetLog(
    val weightKg: Float,
    val reps: Int,
)
