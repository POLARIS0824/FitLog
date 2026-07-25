package com.example.fitlog.data.local.entity.workout

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 单组训练的数据库实体。
 *
 * @property id 主键，自动递增
 * @property exerciseLogId 所属动作记录的外键
 * @property setNumber 组号（第几组）
 * @property weightKg 重量（kg）
 * @property reps 次数
 * @property setType 组类型（[com.example.fitlog.model.SetType] 枚举名）：
 *     "WARMUP" 热身组 / "WORKING" 正式组；容量类统计只累加正式组
 */
@Entity(
    tableName = "set_logs",
    foreignKeys = [
        ForeignKey(
            entity = ExerciseLogEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseLogId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["exerciseLogId"])],
)
data class SetLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val exerciseLogId: Long,
    val setNumber: Int,
    val weightKg: Float,
    val reps: Int,
    val setType: String = "WORKING",
)