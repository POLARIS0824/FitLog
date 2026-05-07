package com.example.myfitness.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.myfitness.domain.model.Gender
import com.example.myfitness.domain.model.TrainingLevel

@Entity(tableName = "user_profiles")
data class UserProfileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val age: Int?,
    val gender: String?,
    val height: Float?,
    val weight: Float?,
)

// TODO
//@Entity(tableName = training_levels)

