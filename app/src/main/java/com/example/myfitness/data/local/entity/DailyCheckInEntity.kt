package com.example.myfitness.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(tableName = "daily_check_ins")
data class DailyCheckInEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val date: String,
    val content: String, // TODO: 直接用 String 表示 Markdown 格式的训练记录？
)

// TODO exercise_sets
//@Entity(
//    tableName = "exercise_sets",
//    foreignKeys = [ForeignKey(
//        entity = DailyCheckInEntity::class,
//        parentColumns = ["id"],
//        childColumns = ["checkInId"],
//        onDelete = ForeignKey.CASCADE
//    )]
//)
//data class ExerciseSetEntity(
//    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
//    val checkInId: Long,
//    val exerciseName: String,
//    val setNumber: Int,            // 第几组，UI 展示用
//    val weightKg: Float,
//    val reps: Int,
//)
