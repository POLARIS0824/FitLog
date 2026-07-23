package com.example.fitlog.data.local.entity.plan

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * 训练计划的数据库实体。
 *
 * @property id 业务标识主键
 * @property name 计划名称
 * @property description 计划说明
 * @property goal [com.example.fitlog.model.user.TrainingGoal.name]，可选
 * @property durationWeeks 计划持续周数
 * @property sessionsPerWeek 每周训练次数
 * @property isCustom 是否用户自定义
 * @property createdAt ISO-8601 日期字符串
 * @property rawPlanText AI 生成计划的原始文本，可选
 */
@Entity(tableName = "workout_plans")
data class WorkoutPlanEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val description: String?,
    val goal: String?,
    val durationWeeks: Int,
    val sessionsPerWeek: Int,
    val isCustom: Boolean,
    val createdAt: LocalDate,
    val rawPlanText: String?,
)
