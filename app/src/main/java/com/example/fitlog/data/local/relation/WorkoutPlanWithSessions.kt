package com.example.fitlog.data.local.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.example.fitlog.data.local.entity.plan.PlannedSessionEntity
import com.example.fitlog.data.local.entity.plan.WorkoutPlanEntity

/**
 * WorkoutPlan -> PlannedSession（动作清单已内嵌在 session 的 JSON 列中）。
 */
data class WorkoutPlanWithSessions(
    @Embedded val plan: WorkoutPlanEntity,

    @Relation(
        parentColumn = "id",
        entityColumn = "planId"
    )
    val sessions: List<PlannedSessionEntity>
)
