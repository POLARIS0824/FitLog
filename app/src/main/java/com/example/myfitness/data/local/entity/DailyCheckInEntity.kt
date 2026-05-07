package com.example.myfitness.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 训练记录的数据库实体，以 Markdown 文本存储每日训练内容。
 *
 * @property id 主键，自动递增
 * @property date 训练日期，ISO-8601 格式（如 "2026-05-07"）
 * @property content Markdown 格式的训练日志
 */
@Entity(tableName = "daily_check_ins")
data class DailyCheckInEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val date: String,
    val content: String,
)
