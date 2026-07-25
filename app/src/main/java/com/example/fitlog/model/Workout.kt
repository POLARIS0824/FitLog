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
    val startedAt: Long? = null,
    val endedAt: Long? = null,
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
    val setType: SetType = SetType.WORKING,
)

/**
 * 组类型。
 *
 * 容量类统计只累加 [WORKING] 正式组，热身组 [WARMUP] 仅作展示与 AI 上下文。
 */
enum class SetType {
    /** 热身组（不计入容量统计） */
    WARMUP,

    /** 正式组（默认） */
    WORKING,
}
