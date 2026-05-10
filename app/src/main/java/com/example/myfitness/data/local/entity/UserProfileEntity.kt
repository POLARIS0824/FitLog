package com.example.myfitness.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 用户资料的数据库实体。
 *
 * @property id 主键，自动递增
 * @property name 用户姓名
 * @property age 年龄（可选）
 * @property gender 性别，存储为 "MALE"、"FEMALE" 或 "OTHER"
 * @property height 身高（cm，可选）
 * @property weight 体重（kg，可选）
 * @property trainingGoal 训练目标（可选）
 */
@Entity(tableName = "user_profiles")
data class UserProfileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val age: Int?,
    val gender: String?,
    val height: Float?,
    val weight: Float?,
    val trainingGoal: String?,
)
