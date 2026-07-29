package com.example.fitlog.feature.today

import com.example.fitlog.model.ExerciseLog
import com.example.fitlog.model.PlannedExerciseItem
import com.example.fitlog.model.PlannedSession
import com.example.fitlog.model.SetLog
import com.example.fitlog.model.SetType
import com.example.fitlog.model.Workout
import com.example.fitlog.model.WorkoutPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * [TodayPlanAssembler] 的单元测试（纯 JVM）。
 *
 * 验证今日训练计划卡片的五态状态机、副标题格式与派生属性。
 */
class TodayPlanAssemblerTest {

    private val today = LocalDate.of(2026, 7, 24)

    // ── 状态机 ──

    @Test
    fun `NO_PLAN when no active plan`() {
        val state = assemble(activePlan = null)

        assertEquals(PlanStatus.NO_PLAN, state.status)
        assertEquals("还没有训练计划", state.title)
        assertEquals("选择训练计划", state.buttonText)
        assertEquals(0f, state.progress)
        assertNull(state.planId)
    }

    @Test
    fun `COMPLETED when plan fully completed`() {
        val plan = plan(sessions = listOf(session(id = "s1", completedWorkoutId = 9L)))

        val state = assemble(activePlan = plan, nextSession = null)

        assertEquals(PlanStatus.COMPLETED, state.status)
        assertEquals("测试计划", state.title)
        assertEquals("全部训练日已完成", state.subtitle)
        assertEquals(1f, state.progress)
        assertEquals("100%", state.progressPercentageText)
        assertEquals("查看训练记录", state.buttonText)
    }

    @Test
    fun `COMPLETED when session linked to today's workout`() {
        val plan = plan(
            sessions = listOf(
                session(id = "s1", name = "课 A", completedWorkoutId = 42L),
                session(id = "s2", name = "课 B"),
            ),
        )
        val todayWorkouts = listOf(workout(id = 42L))

        val state = assemble(
            activePlan = plan,
            nextSession = plan.sessions[1],
            todayWorkouts = todayWorkouts,
        )

        assertEquals(PlanStatus.COMPLETED, state.status)
        assertEquals("课 A", state.title)
        assertEquals(42L, state.workoutId)
        assertEquals(1f, state.progress)
    }

    @Test
    fun `IN_PROGRESS derives progress from logged working sets over target sets`() {
        val next = session(
            id = "s1",
            name = "课 A",
            exercises = listOf(
                PlannedExerciseItem(exerciseKey = "a", targetSets = 4, order = 0),
                PlannedExerciseItem(exerciseKey = "b", targetSets = 6, order = 1),
            ),
        )
        val plan = plan(sessions = listOf(next))
        val inProgressWorkout = workout(
            id = 7L,
            startedAt = 1_777_000_000_000L,
            endedAt = null,
            exercises = listOf(
                ExerciseLog(
                    name = "卧推",
                    exerciseKey = "a",
                    sets = listOf(
                        SetLog(40f, 12, SetType.WARMUP), // 热身组不计
                        SetLog(80f, 10, SetType.WORKING),
                        SetLog(80f, 10, SetType.WORKING),
                        SetLog(80f, 10, SetType.WORKING),
                        SetLog(80f, 10, SetType.WORKING),
                        SetLog(80f, 10, SetType.WORKING),
                    ),
                ),
            ),
        )

        val state = assemble(
            activePlan = plan,
            nextSession = next,
            todayWorkouts = listOf(inProgressWorkout),
        )

        assertEquals(PlanStatus.IN_PROGRESS, state.status)
        assertEquals(5f / 10f, state.progress)
        assertEquals("50%", state.progressPercentageText)
        assertEquals("继续训练", state.buttonText)
        assertEquals(7L, state.workoutId)
    }

    @Test
    fun `IN_PROGRESS progress coerced into 1 to 99 percent`() {
        val next = session(
            id = "s1",
            exercises = listOf(PlannedExerciseItem(exerciseKey = "a", targetSets = 3, order = 0)),
        )
        val plan = plan(sessions = listOf(next))
        val emptyWorkout = workout(id = 7L, startedAt = 1L, endedAt = null)

        val state = assemble(
            activePlan = plan,
            nextSession = next,
            todayWorkouts = listOf(emptyWorkout),
        )

        assertEquals(PlanStatus.IN_PROGRESS, state.status)
        assertEquals(0.01f, state.progress)
    }

    @Test
    fun `NOT_STARTED uses next session title and subtitle`() {
        val next = session(
            id = "s1",
            name = "课 A · 下肢 + 推",
            targetDurationMinutes = 60,
            exercises = listOf(
                PlannedExerciseItem(exerciseKey = "a", targetSets = 4, order = 0),
                PlannedExerciseItem(exerciseKey = "b", targetSets = 3, order = 1),
            ),
        )
        val plan = plan(sessions = listOf(next))

        val state = assemble(activePlan = plan, nextSession = next)

        assertEquals(PlanStatus.NOT_STARTED, state.status)
        assertEquals("课 A · 下肢 + 推", state.title)
        assertEquals("2 个动作 · 60 分钟", state.subtitle)
        assertEquals(0f, state.progress)
        assertEquals("开始训练", state.buttonText)
        assertEquals("plan-1", state.planId)
        assertEquals("s1", state.sessionId)
    }

    @Test
    fun `subtitle omits duration when target duration is null`() {
        val next = session(
            id = "s1",
            name = "课 A",
            targetDurationMinutes = null,
            exercises = listOf(PlannedExerciseItem(exerciseKey = "a", targetSets = 4, order = 0)),
        )

        val state = assemble(activePlan = plan(sessions = listOf(next)), nextSession = next)

        assertEquals("1 个动作", state.subtitle)
    }

    // ── 辅助方法 ──

    private fun assemble(
        activePlan: WorkoutPlan?,
        nextSession: PlannedSession? = null,
        todayWorkouts: List<Workout> = emptyList(),
    ) = TodayPlanAssembler.assemble(
        activePlan = activePlan,
        nextSession = nextSession,
        todayWorkouts = todayWorkouts,
    )

    private fun plan(sessions: List<PlannedSession>) = WorkoutPlan(
        id = "plan-1",
        name = "测试计划",
        description = null,
        goal = null,
        durationWeeks = 4,
        sessionsPerWeek = 3,
        isCustom = false,
        createdAt = today,
        rawPlanText = null,
        sessions = sessions,
    )

    private fun session(
        id: String,
        name: String = "训练日",
        targetDurationMinutes: Int? = 60,
        exercises: List<PlannedExerciseItem> = emptyList(),
        completedWorkoutId: Long? = null,
    ) = PlannedSession(
        id = id,
        name = name,
        description = null,
        dayNumber = 1,
        weekNumber = 1,
        targetDurationMinutes = targetDurationMinutes,
        exercises = exercises,
        completedWorkoutId = completedWorkoutId,
    )

    private fun workout(
        id: Long,
        startedAt: Long? = null,
        endedAt: Long? = null,
        exercises: List<ExerciseLog> = emptyList(),
    ) = Workout(
        id = id,
        userId = 0L,
        date = today,
        exercises = exercises,
        feelings = null,
        startedAt = startedAt,
        endedAt = endedAt,
    )
}
