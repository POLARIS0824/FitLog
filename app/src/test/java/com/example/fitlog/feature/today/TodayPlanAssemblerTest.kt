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

    @Test
    fun `assemble populates tagText and exercise list with Chinese names and prescription`() {
        val next = session(
            id = "s1",
            name = "腿日 · 股四头后侧链",
            targetDurationMinutes = 65,
            exercises = listOf(
                PlannedExerciseItem(
                    exerciseKey = "barbell-full-squat",
                    exerciseName = "Barbell full squat",
                    targetSets = 4,
                    targetRepsMin = 6,
                    targetRepsMax = 8,
                    order = 0,
                ),
                PlannedExerciseItem(
                    exerciseKey = "barbell-romanian-deadlift",
                    exerciseName = "Barbell romanian deadlift",
                    targetSets = 3,
                    targetRepsMin = 8,
                    targetRepsMax = 10,
                    order = 1,
                ),
            ),
        )
        val testPlan = plan(sessions = listOf(next)).copy(name = "推拉腿 PPL · 4 周")

        val state = assemble(activePlan = testPlan, nextSession = next)

        assertEquals("推拉腿 · 第1天", state.tagText)
        assertEquals("腿日 · 股四头后侧链", state.title)
        assertEquals("2 个动作 · 65 分钟", state.subtitle)
        assertEquals(2, state.exercises.size)
        assertEquals("杠铃深蹲", state.exercises[0].name)
        assertEquals("4 组 × 6-8", state.exercises[0].setsRepsText)
        assertEquals("罗马尼亚硬拉", state.exercises[1].name)
        assertEquals("3 组 × 8-10", state.exercises[1].setsRepsText)
    }

    @Test
    fun `checkedExerciseKeys marks exercise completed and updates progress`() {
        val next = session(
            id = "s1",
            exercises = listOf(
                PlannedExerciseItem(exerciseKey = "ex-1", targetSets = 4, order = 0),
                PlannedExerciseItem(exerciseKey = "ex-2", targetSets = 3, order = 1),
            ),
        )
        val testPlan = plan(sessions = listOf(next))

        val state = assemble(
            activePlan = testPlan,
            nextSession = next,
            // 打卡 key 是行唯一键（exerciseKey#order）
            checkedExerciseKeys = setOf("ex-1#0"),
        )

        assertEquals(PlanStatus.IN_PROGRESS, state.status)
        assertEquals(0.5f, state.progress)
        assertEquals(true, state.exercises[0].isCompleted)
        assertEquals(false, state.exercises[1].isCompleted)
    }

    @Test
    fun `assemble looks up recent weight from allWorkouts`() {
        val next = session(
            id = "s1",
            exercises = listOf(
                PlannedExerciseItem(exerciseKey = "barbell-full-squat", targetSets = 4, order = 0),
            ),
        )
        val historyWorkouts = listOf(
            workout(
                id = 1L,
                exercises = listOf(
                    ExerciseLog(
                        name = "杠铃深蹲",
                        exerciseKey = "barbell-full-squat",
                        sets = listOf(
                            SetLog(100f, 6, SetType.WORKING),
                        ),
                    ),
                ),
            ),
        )

        val state = assemble(
            activePlan = plan(sessions = listOf(next)),
            nextSession = next,
            allWorkouts = historyWorkouts,
        )

        assertEquals("100 kg", state.exercises[0].weightText)
    }

    // ── 辅助方法 ──

    private fun assemble(
        activePlan: WorkoutPlan?,
        nextSession: PlannedSession? = null,
        todayWorkouts: List<Workout> = emptyList(),
        allWorkouts: List<Workout> = emptyList(),
        checkedExerciseKeys: Set<String> = emptySet(),
    ) = TodayPlanAssembler.assemble(
        activePlan = activePlan,
        nextSession = nextSession,
        todayWorkouts = todayWorkouts,
        allWorkouts = allWorkouts,
        checkedExerciseKeys = checkedExerciseKeys,
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
