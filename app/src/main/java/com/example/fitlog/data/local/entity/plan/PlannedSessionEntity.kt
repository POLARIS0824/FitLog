package com.example.fitlog.data.local.entity.plan

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.fitlog.data.local.entity.workout.WorkoutEntity

/**
 * 计划中的单次训练的数据库实体。
 *
 * @property id 业务标识主键
 * @property planId 所属计划 ID
 * @property name 训练日名称
 * @property description 训练日说明
 * @property dayNumber 计划中的第几天（1-based）
 * @property weekNumber 第几周（1-based）
 * @property targetDurationMinutes 目标训练时长
 * @property completedWorkoutId 关联实际完成的 workouts.id
 */
@Entity(
    tableName = "planned_sessions",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutPlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["planId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = WorkoutEntity::class,
            parentColumns = ["id"],
            childColumns = ["completedWorkoutId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["planId"]),
        Index(value = ["completedWorkoutId"]),
    ],
)
data class PlannedSessionEntity(
    @PrimaryKey
    val id: String,
    val planId: String,
    val name: String,
    val description: String?,
    val dayNumber: Int,
    val weekNumber: Int,
    val targetDurationMinutes: Int?,
    val completedWorkoutId: Long?,
)