package com.example.fitlog.model

import com.example.fitlog.model.user.TrainingGoal
import java.time.LocalDate

/**
 * 训练计划，描述一个完整的计划周期（如"4周增肌计划"）。
 *
 * 采用扁平化 session 设计：周期性计划直接展开为多个 [PlannedSession]，
 * 不引入"模板"抽象，降低初期复杂度。
 *
 * @property id 业务标识，如 "plan-hypertrophy-4wk-001"
 * @property name 计划展示名称
 * @property description 计划说明，可选
 * @property goal 训练目标，复用 [TrainingGoal]
 * @property difficulty 建议难度等级，复用 [Difficulty]
 * @property durationWeeks 计划持续周数
 * @property sessionsPerWeek 每周训练次数
 * @property isCustom false = AI/系统预设，true = 用户自定义
 * @property createdAt 计划创建日期
 * @property sessions 计划中的所有训练日
 */
data class WorkoutPlan(
    val id: String,
    val name: String,
    val description: String?,
    val goal: TrainingGoal?,
    val difficulty: Difficulty?,
    val durationWeeks: Int,
    val sessionsPerWeek: Int,
    val isCustom: Boolean,
    val createdAt: LocalDate,
    val sessions: List<PlannedSession>,
)

/**
 * 计划中的单次训练（Planned Session）。
 *
 * @property id 业务标识
 * @property name 训练日名称，如 "Day 1 - 推日"
 * @property description 训练日说明，可选
 * @property dayNumber 计划中的第几天（1-based）
 * @property weekNumber 第几周（1-based）
 * @property targetDurationMinutes 目标训练时长（分钟），可选
 * @property exercises 该训练日包含的动作列表
 * @property completedWorkoutId 关联实际完成的 [Workout] 记录 ID，未完成为 null
 */
data class PlannedSession(
    val id: String,
    val name: String,
    val description: String?,
    val dayNumber: Int,
    val weekNumber: Int,
    val targetDurationMinutes: Int?,
    val exercises: List<PlannedExercise>,
    val completedWorkoutId: Long? = null,
)

/**
 * 计划中的单个动作配置。
 *
 * @property id 业务标识
 * @property exerciseKey 关联 [Exercise.id]（kebab-case），如 "barbell-bench-press"
 * @property exerciseName 动作名称缓存，避免 Exercise 目录未加载时无法显示
 * @property targetSets 目标组数
 * @property targetRepsMin 目标次数下限，可选（留空时由 AI/系统根据历史推算）
 * @property targetRepsMax 目标次数上限，可选
 * @property targetWeightKg 目标重量（公斤），可选
 * @property targetRpe 目标 RPE（1-10），可选
 * @property restSeconds 组间休息秒数，可选
 * @property notes AI 指导备注，如"注意肩胛骨下沉"，可选
 * @property order 在训练日中的执行顺序
 */
data class PlannedExercise(
    val id: String,
    val exerciseKey: String,
    val exerciseName: String?,
    val targetSets: Int,
    val targetRepsMin: Int?,
    val targetRepsMax: Int?,
    val targetWeightKg: Float?,
    val targetRpe: Int?,
    val restSeconds: Int?,
    val notes: String?,
    val order: Int,
)
