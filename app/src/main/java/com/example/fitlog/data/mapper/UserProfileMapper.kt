package com.example.fitlog.data.mapper

import com.example.fitlog.data.local.entity.UserProfileEntity
import com.example.fitlog.model.user.Gender
import com.example.fitlog.model.user.TrainingGoal
import com.example.fitlog.model.user.TrainingLevel
import com.example.fitlog.model.user.UserProfile

fun UserProfileEntity.toModel() : UserProfile {
    return UserProfile(
        id = id,
        name = name,
        age = age,
        gender = gender?.let { Gender.valueOf(it) },
        height = height,
        weight = weight,
        trainingLevel = TrainingLevel(emptyMap()),
        trainingGoal = trainingGoal?.let { TrainingGoal.valueOf(it) },
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