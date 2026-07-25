package com.example.fitlog.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * 身体指标记录的数据库实体（当前仅体重）。
 *
 * 以 [date] 为业务主键：REPLACE 插入即完成"按天去重 upsert"，
 * 同一天重复记录时新值覆盖旧值，无需唯一索引与手写 upsert。
 * 未来扩展（体脂、围度等）直接加列即可，一行一天的设计不变。
 *
 * @property date 记录日期
 * @property weightKg 体重（kg）
 */
@Entity(tableName = "body_metrics")
data class BodyMetricEntity(
    @PrimaryKey
    val date: LocalDate,
    val weightKg: Float,
)
