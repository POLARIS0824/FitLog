package com.example.fitlog.data.mapper

import com.example.fitlog.data.local.entity.UserProfileEntity
import com.example.fitlog.model.user.Gender
import com.example.fitlog.model.user.TrainingGoal
import com.example.fitlog.model.user.UserProfile

fun UserProfileEntity.toModel() : UserProfile {
    return UserProfile(
        id = id,
        name = name,
        age = age,
        gender = gender?.let { raw -> Gender.entries.firstOrNull { it.name == raw } },
        height = height,
        weight = weight,
        trainingGoal = trainingGoal?.let { raw -> TrainingGoal.entries.firstOrNull { it.name == raw } },
    )
}

fun UserProfile.toEntity() : UserProfileEntity {
    return UserProfileEntity(
        id = id,
        name = name,
        age = age,
        gender = gender?.toString(),
        height = height,
        weight = weight,
        trainingGoal = trainingGoal?.toString(),
    )
}
