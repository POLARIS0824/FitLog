package com.example.fitlog.data.local.entity.workout

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.fitlog.data.local.entity.ExerciseEntity

/**
 * 单次训练中某个动作的数据库实体。
 *
 * @property id 主键，自动递增
 * @property workoutId 所属训练日的外键
 * @property exerciseKey 关联 [ExerciseEntity.id] 的业务标识（kebab-case），可空
 * @property name 动作名称（冗余存储，用于 exerciseKey 为空时的降级显示）
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
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseKey"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["workoutId"]),
        Index(value = ["exerciseKey"]),
    ],
)
data class ExerciseLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val workoutId: Long,
    val exerciseKey: String? = null,
    val name: String,
    val sortOrder: Int,
)