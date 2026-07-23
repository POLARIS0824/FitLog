package com.example.fitlog.data.mapper

import com.example.fitlog.data.local.entity.ExerciseEntity
import com.example.fitlog.model.Exercise

fun ExerciseEntity.toModel() : Exercise {
    return Exercise(
        id = id,
        name = name,
        primaryMuscles = primaryMuscles,
        secondaryMuscles = secondaryMuscles,
        isCompound = isCompound,
        isCustom = isCustom,
        equipment = equipment,
        bodyPart = bodyPart,
        description = description,
        instructions = instructions,
        imageUrl = imageUrl,
        gifUrl = gifUrl,
    )
}

fun Exercise.toEntity() : ExerciseEntity {
    return ExerciseEntity(
        id = id,
        name = name,
        primaryMuscles = primaryMuscles,
        secondaryMuscles = secondaryMuscles,
        isCompound = isCompound,
        isCustom = isCustom,
        equipment = equipment,
        bodyPart = bodyPart,
        description = description,
        instructions = instructions,
        imageUrl = imageUrl,
        gifUrl = gifUrl,
    )
}
