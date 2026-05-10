package com.example.fitlog.domain.model

import java.time.LocalDate

/**
 * WorkOut -> ExerciseLog -> SetLog
 */

data class WorkOut(
    val id: Long,
    val userId: Long,
    val date: LocalDate,
    val exercises: List<ExerciseLog>,
    val feelings: String?,
    val sourceFileName: String? = null,
)

/**
 * 动作名称以及组列表
 */
data class ExerciseLog(
    val name: String,
    val sets: List<SetLog>,
)

/**
 * 每组重量以及次数
 */
data class SetLog(
    val weightKg: Float,
    val reps: Int,
)
