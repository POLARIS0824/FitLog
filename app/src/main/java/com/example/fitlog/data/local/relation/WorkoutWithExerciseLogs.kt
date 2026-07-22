package com.example.fitlog.data.local.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.example.fitlog.data.local.entity.workout.ExerciseLogEntity
import com.example.fitlog.data.local.entity.workout.SetLogEntity
import com.example.fitlog.data.local.entity.workout.WorkoutEntity

/**
 * Workout -> ExerciseLog -> SetLog
 */

data class WorkoutWithExerciseLogs(
    @Embedded val workout: WorkoutEntity,

    @Relation(
        entity = ExerciseLogEntity::class,
        parentColumn = "id",
        entityColumn = "workoutId"
    )
    val exerciseLogs: List<ExerciseLogWithSets>
)

data class ExerciseLogWithSets(
    @Embedded val exerciseLog: ExerciseLogEntity,

    @Relation(
        parentColumn = "id",
        entityColumn = "exerciseLogId"
    )
    val sets: List<SetLogEntity>
)
