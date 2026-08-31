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
    val planSessionId: String? = null,
    val sourceFileName: String? = null,
    val rawContent: String? = null,
) {
    /**
     * 是否为可计数的结构化训练（含动作明细且已结束）。
     *
     * 两类记录必须排除：
     * 1. 导入 Markdown 时仅存档表头（日期/感受）而无动作明细的记录，是
     *    "那天练过"的存档证明，不是一次可计数的训练；
     * 2. 训练执行流的进行中会话（startedAt 已写、endedAt 为空）——组数还在
     *    增长，计入"完成次数"会让 Today/Stats 的数字随录入过程虚高。
     *
     * 完成次数等统计必须统一走本口径，否则卡片显示次数与
     * Coach 观察/AI 指纹会互相矛盾。
     */
    val isCountable: Boolean
        get() = exercises.isNotEmpty() && endedAt != null
}

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
