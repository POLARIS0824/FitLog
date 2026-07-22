package com.example.fitlog.data.mapper

import com.example.fitlog.data.local.entity.workout.WorkoutEntity
import com.example.fitlog.data.local.relation.ExerciseLogWithSets
import com.example.fitlog.data.local.relation.WorkoutWithExerciseLogs
import com.example.fitlog.model.ExerciseLog
import com.example.fitlog.model.SetLog
import com.example.fitlog.model.Workout

fun WorkoutEntity.toModel(): Workout {
    return Workout(
        id = id,
        userId = userId,
        date = date,
        exercises = emptyList(),
        feelings = feelings,
        sourceFileName = sourceFileName,
        rawContent = rawContent,
    )
}

fun WorkoutWithExerciseLogs.toModel(): Workout {
    return Workout(
        id = workout.id,
        userId = workout.userId,
        date = workout.date,
        exercises = exerciseLogs.map { it.toModel() },
        feelings = workout.feelings,
        sourceFileName = workout.sourceFileName,
        rawContent = workout.rawContent,
    )
}

fun ExerciseLogWithSets.toModel(): ExerciseLog {
    return ExerciseLog(
        name = exerciseLog.name,
        exerciseKey = exerciseLog.exerciseKey,
        sets = sets.map { SetLog(weightKg = it.weightKg, reps = it.reps) },
    )
}

fun Workout.toEntity(): WorkoutEntity {
    return WorkoutEntity(
        id = id,
        userId = userId,
        date = date,
        feelings = feelings,
        sourceFileName = sourceFileName,
        rawContent = rawContent,
    )
}