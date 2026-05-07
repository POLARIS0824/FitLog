package com.example.myfitness.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 训练日的数据库实体。
 *
 * @property id 主键，自动递增
 * @property date 训练日期，ISO-8601 格式（如 "2026-05-07"）
 * @property sourceFileName 来源 file 文件名，如 "2026-05-07.md"
 * @property rawContent 原始 file 全文，便于 AI 解析出错时对照排查
 */
@Entity(tableName = "workouts")
data class WorkoutEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val date: String,
    val sourceFileName: String?,
    val rawContent: String,
)
