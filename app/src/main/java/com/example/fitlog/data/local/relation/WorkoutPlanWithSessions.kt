package com.example.fitlog.data.local.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.example.fitlog.data.local.entity.plan.PlannedExerciseEntity
import com.example.fitlog.data.local.entity.plan.PlannedSessionEntity
import com.example.fitlog.data.local.entity.plan.WorkoutPlanEntity

/**
 * WorkoutPlan -> PlanedSession -> PlannedExercise
 */

data class WorkoutPlanWithSessions(
    @Embedded val plan: WorkoutPlanEntity,

    @Relation(
        parentColumn = "id",
        entityColumn = "planId"
    )
    val sessions: List<PlannedSessionWithExercises>
)

data class PlannedSessionWithExercises(
    @Embedded val session: PlannedSessionEntity,

    @Relation(
        parentColumn = "id",
        entityColumn = "sessionId"
    )
    val exercises: List<PlannedExerciseEntity>
)