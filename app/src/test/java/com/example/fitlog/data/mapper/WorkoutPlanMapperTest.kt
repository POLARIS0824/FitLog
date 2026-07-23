package com.example.fitlog.data.mapper

import com.example.fitlog.data.local.entity.plan.PlannedSessionEntity
import com.example.fitlog.data.local.entity.plan.WorkoutPlanEntity
import com.example.fitlog.data.local.relation.WorkoutPlanWithSessions
import com.example.fitlog.model.PlannedExerciseItem
import com.example.fitlog.model.PlannedSession
import com.example.fitlog.model.WorkoutPlan
import com.example.fitlog.model.user.TrainingGoal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * [WorkoutPlanMapper] 的单元测试。
 * 验证训练计划 2 层结构（WorkoutPlan -> PlannedSession，动作清单内嵌 JSON）的映射，
 * 以及 goal 枚举与 rawPlanText 的序列化与反序列化。
 */
class WorkoutPlanMapperTest {

    private val createdAt = LocalDate.of(2026, 5, 1)

    private fun planEntity(
        goal: String? = "HYPERTROPHY",
        rawPlanText: String? = "AI 原始文本",
    ) = WorkoutPlanEntity(
        id = "plan-1",
        name = "增肌计划",
        description = "四周增肌",
        goal = goal,
        durationWeeks = 4,
        sessionsPerWeek = 3,
        isCustom = false,
        createdAt = createdAt,
        rawPlanText = rawPlanText,
    )

    /**
     * 测试扁平 [WorkoutPlanEntity] 转领域模型：枚举字符串还原为枚举，sessions 为空。
     */
    @Test
    fun testPlanEntityToModel_enumsParsed_sessionsEmpty() {
        val model = planEntity().toModel()

        assertEquals("plan-1", model.id)
        assertEquals("增肌计划", model.name)
        assertEquals("四周增肌", model.description)
        assertEquals(TrainingGoal.HYPERTROPHY, model.goal)
        assertEquals(4, model.durationWeeks)
        assertEquals(3, model.sessionsPerWeek)
        assertEquals(false, model.isCustom)
        assertEquals(createdAt, model.createdAt)
        assertEquals("AI 原始文本", model.rawPlanText)
        assertTrue(model.sessions.isEmpty())
    }

    /**
     * 测试 goal/rawPlanText 为 null 时映射为 null（可选字段）。
     */
    @Test
    fun testPlanEntityToModel_nullFields_passThroughAsNull() {
        val model = planEntity(goal = null, rawPlanText = null).toModel()

        assertNull(model.goal)
        assertNull(model.rawPlanText)
    }

    /**
     * 测试级联关系 [WorkoutPlanWithSessions] 转领域模型：训练日与内嵌动作清单完整映射。
     */
    @Test
    fun testRelationToModel_fullHierarchyPreserved() {
        val relation = WorkoutPlanWithSessions(
            plan = planEntity(),
            sessions = listOf(
                PlannedSessionEntity(
                    id = "session-1",
                    planId = "plan-1",
                    name = "推日",
                    description = "胸肩三头",
                    dayNumber = 1,
                    weekNumber = 2,
                    targetDurationMinutes = 60,
                    exercises = listOf(
                        PlannedExerciseItem(
                            exerciseKey = "barbell-bench-press",
                            exerciseName = "杠铃卧推",
                            targetSets = 4,
                            targetRepsMin = 8,
                            targetRepsMax = 12,
                            notes = "控制离心",
                            order = 1,
                        ),
                    ),
                    completedWorkoutId = 99L,
                ),
            ),
        )

        val model = relation.toModel()

        assertEquals(1, model.sessions.size)
        val session = model.sessions[0]
        assertEquals("session-1", session.id)
        assertEquals("推日", session.name)
        assertEquals("胸肩三头", session.description)
        assertEquals(1, session.dayNumber)
        assertEquals(2, session.weekNumber)
        assertEquals(60, session.targetDurationMinutes)
        assertEquals(99L, session.completedWorkoutId)

        assertEquals(1, session.exercises.size)
        val exercise = session.exercises[0]
        assertEquals("barbell-bench-press", exercise.exerciseKey)
        assertEquals("杠铃卧推", exercise.exerciseName)
        assertEquals(4, exercise.targetSets)
        assertEquals(8, exercise.targetRepsMin)
        assertEquals(12, exercise.targetRepsMax)
        assertEquals("控制离心", exercise.notes)
        assertEquals(1, exercise.order)
    }

    /**
     * 测试领域模型转 Entity：枚举序列化为 name 字符串，sessions 不随 plan Entity 存储。
     */
    @Test
    fun testPlanToEntity_enumsSerializedToNames() {
        val model = WorkoutPlan(
            id = "plan-2",
            name = "力量计划",
            description = null,
            goal = TrainingGoal.STRENGTH,
            durationWeeks = 8,
            sessionsPerWeek = 4,
            isCustom = true,
            createdAt = createdAt,
            rawPlanText = "# 4 周力量计划\n...",
            sessions = emptyList(),
        )

        val entity = model.toEntity()

        assertEquals("plan-2", entity.id)
        assertEquals("STRENGTH", entity.goal)
        assertEquals(true, entity.isCustom)
        assertEquals(createdAt, entity.createdAt)
        assertNull(entity.description)
        assertEquals("# 4 周力量计划\n...", entity.rawPlanText)
    }

    /**
     * 测试 [PlannedSession] 转 [PlannedSessionEntity]：planId 注入，动作清单原样透传。
     */
    @Test
    fun testSessionToEntity_planIdInjected_exercisesPassThrough() {
        val session = PlannedSession(
            id = "s1",
            name = "深蹲日",
            description = null,
            dayNumber = 1,
            weekNumber = 1,
            targetDurationMinutes = null,
            exercises = listOf(
                PlannedExerciseItem(
                    exerciseKey = "barbell-squat",
                    exerciseName = null,
                    targetSets = 5,
                    order = 1,
                ),
            ),
            completedWorkoutId = null,
        )

        val entity = session.toEntity(planId = "plan-9")

        assertEquals("s1", entity.id)
        assertEquals("plan-9", entity.planId)
        assertEquals(1, entity.exercises.size)
        assertEquals("barbell-squat", entity.exercises[0].exerciseKey)
        assertEquals(5, entity.exercises[0].targetSets)
        assertNull(entity.exercises[0].targetRepsMin)
    }
}
