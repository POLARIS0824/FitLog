package com.example.myfitness.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 单次训练中某个动作的数据库实体。
 *
 * @property id 主键，自动递增
 * @property workoutId 所属训练日的外键
 * @property name 动作名称
 * @property sortOrder 动作在当天的排序序号
 */
@Entity(
    tableName = "exercise_logs",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutEntity::class,
            parentColumns = ["id"],
            childColumns = ["workoutId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["workoutId"])],
)
data class ExerciseLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val workoutId: Long,
    val name: String,
    val sortOrder: Int,
)
