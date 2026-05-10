package com.example.fitlog.data.local.entity.plan

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 计划中的动作配置的数据库实体。
 *
 * @property id 业务标识主键
 * @property sessionId 所属训练日 ID
 * @property exerciseKey 关联 [Exercise.id]（kebab-case）
 * @property exerciseName 动作名称缓存
 * @property targetSets 目标组数
 * @property targetRepsMin 目标次数下限
 * @property targetRepsMax 目标次数上限
 * @property targetWeightKg 目标重量（公斤）
 * @property targetRpe 目标 RPE（1-10）
 * @property restSeconds 组间休息秒数
 * @property notes AI 指导备注
 * @property order 在训练日中的执行顺序
 */
@Entity(
    tableName = "planned_exercises",
    foreignKeys = [
        ForeignKey(
            entity = PlannedSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["sessionId"])],
)
data class PlannedExerciseEntity(
    @PrimaryKey
    val id: String,
    val sessionId: String,
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