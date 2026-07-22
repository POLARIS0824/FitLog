package com.example.fitlog.data.local.entity.workout

import androidx.room.Entity
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
 * @property sourceFileName 来源 file 文件名，如 "2026-05-07.md"
 * @property rawContent 原始 file 全文，便于 AI 解析出错时对照排查
 */
@Entity(tableName = "workouts")
data class WorkoutEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val userId: Long = 0L,
    val date: LocalDate,
    val feelings: String? = null,
    val sourceFileName: String?,
    val rawContent: String?,
)