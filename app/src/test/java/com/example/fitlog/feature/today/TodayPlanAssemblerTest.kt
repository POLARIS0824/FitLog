package com.example.fitlog.feature.today

import com.example.fitlog.model.ExerciseLog
import com.example.fitlog.model.PlannedExerciseItem
import com.example.fitlog.model.PlannedSession
import com.example.fitlog.model.SetLog
import com.example.fitlog.model.SetType
import com.example.fitlog.model.Workout
import com.example.fitlog.model.WorkoutPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        val todayWorkouts = listOf(workout(id = 42L, startedAt = 1000L, endedAt = 2000L))

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
                PlannedExerciseItem(id = "ex-a", exerciseKey = "a", targetSets = 4, order = 0),
                PlannedExerciseItem(id = "ex-b", exerciseKey = "b", targetSets = 6, order = 1),
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
                    plannedExerciseId = "ex-a",
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
        assertEquals(4f / 10f, state.progress)
        assertEquals("40%", state.progressPercentageText)
        assertEquals("继续训练", state.buttonText)
        assertEquals(7L, state.workoutId)
    }

    @Test
    fun `IN_PROGRESS with 0 sets has 0 progress and stays IN_PROGRESS`() {
        val next = session(
            id = "s1",
            exercises = listOf(PlannedExerciseItem(id = "ex-a", exerciseKey = "a", targetSets = 3, order = 0)),
        )
        val plan = plan(sessions = listOf(next))
        val emptyWorkout = workout(id = 7L, startedAt = 1L, endedAt = null)

        val state = assemble(
            activePlan = plan,
            nextSession = next,
            todayWorkouts = listOf(emptyWorkout),
        )

        assertEquals(PlanStatus.IN_PROGRESS, state.status)
        assertEquals(0f, state.progress)
    }

    @Test
    fun `IN_PROGRESS with 100 percent sets completed stays IN_PROGRESS until ended`() {
        val next = session(
            id = "s1",
            exercises = listOf(PlannedExerciseItem(id = "ex-a", exerciseKey = "a", targetSets = 2, order = 0)),
        )
        val plan = plan(sessions = listOf(next))
        val fullWorkout = workout(
            id = 7L,
            startedAt = 1L,
            endedAt = null,
            exercises = listOf(
                ExerciseLog(
                    name = "a",
                    exerciseKey = "a",
                    plannedExerciseId = "ex-a",
                    sets = listOf(
                        SetLog(50f, 10, SetType.WORKING),
                        SetLog(50f, 10, SetType.WORKING),
                    ),
                ),
            ),
        )

        val state = assemble(
            activePlan = plan,
            nextSession = next,
            todayWorkouts = listOf(fullWorkout),
        )

        assertEquals(PlanStatus.IN_PROGRESS, state.status)
        assertEquals(1f, state.progress)
        assertEquals("100%", state.progressPercentageText)
        assertEquals(true, state.exercises[0].targetReached)
        assertEquals(true, state.exercises[0].displayChecked)
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
    fun `manual checkboxes do not alter PlanStatus or domain progress`() {
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
            checkedExerciseKeys = setOf("ex-1#0", "ex-2#1"),
        )

        // UI 勾选态反映手动打卡
        assertEquals(true, state.exercises[0].displayChecked)
        assertEquals(true, state.exercises[1].displayChecked)
        assertEquals(true, state.exercises[0].manuallyChecked)
        assertEquals(false, state.exercises[0].targetReached)
        // 领域状态与进度不受手动打卡影响，无进行中记录时保持 NOT_STARTED
        assertEquals(PlanStatus.NOT_STARTED, state.status)
        assertEquals(0f, state.progress)
        assertNull(state.workoutId)
    }

    @Test
    fun `empty plan sessions does not mark completed`() {
        val plan = plan(sessions = emptyList())
        val state = assemble(activePlan = plan, nextSession = null)
        assertEquals(PlanStatus.NOT_STARTED, state.status)
        assertEquals(0f, state.progress)
    }

    @Test
    fun `completed plan without today workout has null workoutId and does not crash`() {
        val plan = plan(sessions = listOf(session(id = "s1", completedWorkoutId = 9L)))
        val state = assemble(activePlan = plan, nextSession = null, todayWorkouts = emptyList())
        assertEquals(PlanStatus.COMPLETED, state.status)
        assertNull(state.workoutId)
        assertEquals(1f, state.progress)
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

    @Test
    fun `duplicate exercises in session match distinct logs by plannedExerciseId`() {
        val next = session(
            id = "s1",
            exercises = listOf(
                PlannedExerciseItem(id = "bench-1", exerciseKey = "barbell-bench-press", targetSets = 4, order = 0),
                PlannedExerciseItem(id = "bench-2", exerciseKey = "barbell-bench-press", targetSets = 3, order = 1),
            ),
        )
        val inProgress = workout(
            id = 100L,
            startedAt = 1000L,
            endedAt = null,
            exercises = listOf(
                ExerciseLog(
                    name = "卧推1",
                    exerciseKey = "barbell-bench-press",
                    plannedExerciseId = "bench-1",
                    sets = List(4) { SetLog(100f, 5, SetType.WORKING) },
                ),
                ExerciseLog(
                    name = "卧推2",
                    exerciseKey = "barbell-bench-press",
                    plannedExerciseId = "bench-2",
                    sets = List(1) { SetLog(80f, 10, SetType.WORKING) },
                ),
            ),
        )

        val state = assemble(
            activePlan = plan(sessions = listOf(next)),
            nextSession = next,
            todayWorkouts = listOf(inProgress),
        )

        assertEquals(4, state.exercises[0].loggedWorkingSets)
        assertTrue(state.exercises[0].targetReached)

        assertEquals(1, state.exercises[1].loggedWorkingSets)
        assertFalse(state.exercises[1].targetReached)
    }

    @Test
    fun `manually added exercise in workout is not counted towards planned exercise target`() {
        val next = session(
            id = "s1",
            exercises = listOf(
                PlannedExerciseItem(id = "bench-p", exerciseKey = "barbell-bench-press", targetSets = 4, order = 0),
            ),
        )
        val inProgress = workout(
            id = 101L,
            startedAt = 1000L,
            endedAt = null,
            exercises = listOf(
                ExerciseLog(
                    name = "计划卧推",
                    exerciseKey = "barbell-bench-press",
                    plannedExerciseId = "bench-p",
                    sets = List(2) { SetLog(100f, 5, SetType.WORKING) },
                ),
                ExerciseLog(
                    name = "手动加卧推",
                    exerciseKey = "barbell-bench-press",
                    plannedExerciseId = null,
                    sets = List(3) { SetLog(60f, 12, SetType.WORKING) },
                ),
            ),
        )

        val state = assemble(
            activePlan = plan(sessions = listOf(next)),
            nextSession = next,
            todayWorkouts = listOf(inProgress),
        )

        assertEquals(2, state.exercises[0].loggedWorkingSets)
        assertFalse(state.exercises[0].targetReached)
    }

    /**
     * 验证已批准的严格 ID 匹配策略调整：
     * 没有 plannedExerciseId 的旧训练记录不再猜测匹配计划动作项，不贡献计划动作进度。
     */
    @Test
    fun `legacy workout without plannedExerciseId does not contribute to planned progress`() {
        val next = session(
            id = "s1",
            exercises = listOf(
                PlannedExerciseItem(id = null, exerciseKey = "barbell-bench-press", targetSets = 4, order = 0),
                PlannedExerciseItem(id = null, exerciseKey = "barbell-bench-press", targetSets = 3, order = 1),
            ),
        )
        val legacyWorkout = workout(
            id = 102L,
            startedAt = 1000L,
            endedAt = null,
            exercises = listOf(
                ExerciseLog(
                    name = "卧推1",
                    exerciseKey = "barbell-bench-press",
                    plannedExerciseId = null,
                    sets = List(4) { SetLog(100f, 5, SetType.WORKING) },
                ),
                ExerciseLog(
                    name = "卧推2",
                    exerciseKey = "barbell-bench-press",
                    plannedExerciseId = null,
                    sets = List(2) { SetLog(80f, 10, SetType.WORKING) },
                ),
            ),
        )

        val state = assemble(
            activePlan = plan(sessions = listOf(next)),
            nextSession = next,
            todayWorkouts = listOf(legacyWorkout),
        )

        assertEquals(0, state.exercises[0].loggedWorkingSets)
        assertFalse(state.exercises[0].targetReached)

        assertEquals(0, state.exercises[1].loggedWorkingSets)
        assertFalse(state.exercises[1].targetReached)
    }

    private fun plan(
        sessions: List<PlannedSession> = emptyList(),
    ) = WorkoutPlan(
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
