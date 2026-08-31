package com.example.fitlog.data.local.entity.workout

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * Workout -> ExerciseLog -> SetLog
 */

/**
 * 训练日的数据库实体。
 *
 * @property id 主键，自动递增
 * @property userId 用户 ID，默认 0L
 * @property date 训练日期，使用 LocalDate 存储，便于按日期查询和排序
 * @property feelings 训练感受/备注，可选
 * @property startedAt 训练开始时间（epoch millis），可选；与 [endedAt] 的差值即训练时长
 * @property endedAt 训练结束时间（epoch millis），可选
 * @property planSessionId 来源计划课次 id（planned_sessions.id），训练执行流
 *   "从计划开始"的会话行携带，结束训练后据此回写课次完成标记；自由训练与
 *   导入记录为 null
 * @property sourceFileName 来源 file 文件名，如 "2026-05-07.md"；唯一（NULL 不受约束），
 *   导入幂等性由 schema 保证而非应用层 check-then-insert
 * @property rawContent 原始 file 全文，便于 AI 解析出错时对照排查
 */
@Entity(
    tableName = "workouts",
    indices = [
        Index(value = ["date"]),
        Index(value = ["sourceFileName"], unique = true),
    ],
)
data class WorkoutEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val userId: Long = 0L,
    val date: LocalDate,
    val feelings: String? = null,
    val startedAt: Long? = null,
    val endedAt: Long? = null,
    val planSessionId: String? = null,
    val sourceFileName: String?,
    val rawContent: String?,
)