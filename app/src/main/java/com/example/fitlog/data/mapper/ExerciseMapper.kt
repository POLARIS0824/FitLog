package com.example.fitlog.data.mapper

import com.example.fitlog.data.local.entity.ExerciseEntity
import com.example.fitlog.model.Exercise

fun ExerciseEntity.toModel() : Exercise {
    return Exercise(
        id = id,
        name = name,
        primaryMuscle = primaryMuscle,
        secondaryMuscles = secondaryMuscles,
        movementPattern = movementPattern,
        force = force,
        difficulty = difficulty,
        isCompound = isCompound,
        isCustom = isCustom,
        equipment = equipment,
        category = category,
        description = description,
        instructions = instructions,
    )
}

fun Exercise.toEntity() : ExerciseEntity {
    return ExerciseEntity(
        id = id,
        name = name,
        primaryMuscle = primaryMuscle,
        secondaryMuscles = secondaryMuscles,
        movementPattern = movementPattern,
        force = force,
        difficulty = difficulty,
        isCompound = isCompound,
        isCustom = isCustom,
        equipment = equipment,
        category = category,
        description = description,
        instructions = instructions,
    )
}