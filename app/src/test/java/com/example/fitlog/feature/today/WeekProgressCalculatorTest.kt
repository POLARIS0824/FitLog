package com.example.fitlog.feature.today

import com.example.fitlog.model.BodyPart
import com.example.fitlog.model.Exercise
import com.example.fitlog.model.ExerciseLog
import com.example.fitlog.model.Muscle
import com.example.fitlog.model.PlannedExerciseItem
import com.example.fitlog.model.PlannedSession
import com.example.fitlog.model.SetLog
import com.example.fitlog.model.SetType
import com.example.fitlog.model.Workout
import com.example.fitlog.model.WorkoutPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * [WeekProgressCalculator] 的单元测试（纯 JVM）。
 *
 * 验证四种展示模式的聚合口径：SPLIT 完成状态、MUSCLE_SETS 正式组计数、
 * VOLUME_PR 容量与新 PR、CATEGORY 部位去重，以及降级文案与 top 4 截断。
 */
class WeekProgressCalculatorTest {

    private val weekStart = LocalDate.of(2026, 7, 20) // 周一
    private val catalog = listOf(
        exercise("barbell-bench-press", "Barbell bench press", listOf(Muscle.CHEST, Muscle.TRICEPS), BodyPart.CHEST),
        exercise("barbell-full-squat", "Barbell full squat", listOf(Muscle.QUADRICEPS, Muscle.GLUTES), BodyPart.UPPER_LEGS),
        exercise("barbell-bent-over-row", "Barbell bent over row", listOf(Muscle.LATS, Muscle.BICEPS), BodyPart.BACK),
        exercise("dumbbell-lateral-raise", "Dumbbell lateral raise", listOf(Muscle.SHOULDERS), BodyPart.SHOULDERS),
        exercise("barbell-curl", "Barbell curl", listOf(Muscle.BICEPS), BodyPart.UPPER_ARMS),
        exercise("hanging-leg-raise", "Hanging leg raise", listOf(Muscle.CORE), BodyPart.WAIST),
    )

    // ── SPLIT ──

    @Test
    fun `SPLIT without active plan returns single fallback item`() {
        val items = calculate(WeekProgressDisplayMode.SPLIT, activePlan = null)
        assertEquals(1, items.size)
        assertEquals("未激活计划", items[0].title)
        assertEquals("去选择一套计划", items[0].subtitle)
    }

    @Test
    fun `SPLIT shows completion head and per-session status`() {
        val plan = plan(
            sessionsPerWeek = 3,
            sessions = listOf(
                session(id = "w1d1", name = "推日", completedWorkoutId = 10L),
                session(id = "w1d2", name = "拉日"),
                session(id = "w1d3", name = "腿日"),
            ),
        )
        val weekWorkouts = listOf(workout(id = 10L, date = weekStart.plusDays(1)))

        val items = calculate(WeekProgressDisplayMode.SPLIT, weekWorkouts = weekWorkouts, activePlan = plan)

        assertEquals(4, items.size)
        assertEquals("本周训练", items[0].title)
        assertEquals("1/3 次", items[0].subtitle)
        assertEquals("推日", items[1].title)
        assertEquals("本周已练", items[1].subtitle)
        assertEquals("拉日", items[2].title)
        assertEquals("待训练", items[2].subtitle)
    }

    // ── MUSCLE_SETS ──

    @Test
    fun `MUSCLE_SETS counts only working sets expanded per primary muscle`() {
        val weekWorkouts = listOf(
            workout(
                id = 1L,
                date = weekStart,
                exercises = listOf(
                    exerciseLog(
                        "barbell-bench-press", "Barbell bench press",
                        warmup(40f, 12), working(80f, 10), working(85f, 8),
                    ),
                    exerciseLog(
                        "barbell-bent-over-row", "Barbell bent over row",
                        working(60f, 10),
                    ),
                ),
            ),
        )

        val items = calculate(WeekProgressDisplayMode.MUSCLE_SETS, weekWorkouts = weekWorkouts)

        // 卧推 2 个正式组 → CHEST+2 / TRICEPS+2；划船 1 组 → LATS+1 / BICEPS+1
        val byTitle = items.associateBy { it.title }
        assertEquals("2 组", byTitle["胸部"]?.subtitle)
        assertEquals("2 组", byTitle["肱三头肌"]?.subtitle)
        assertEquals("1 组", byTitle["背阔肌"]?.subtitle)
        assertEquals("1 组", byTitle["肱二头肌"]?.subtitle)
    }

    @Test
    fun `MUSCLE_SETS falls back to exercise name when key misses`() {
        val weekWorkouts = listOf(
            workout(
                id = 1L,
                date = weekStart,
                exercises = listOf(
                    // exerciseKey 查不到，name 兜底命中
                    exerciseLog("unknown-key", "Barbell curl", working(20f, 12)),
                ),
            ),
        )

        val items = calculate(WeekProgressDisplayMode.MUSCLE_SETS, weekWorkouts = weekWorkouts)

        assertEquals(1, items.size)
        assertEquals("肱二头肌", items[0].title)
        assertEquals("1 组", items[0].subtitle)
    }

    @Test
    fun `MUSCLE_SETS truncates to top 4 in descending order`() {
        val weekWorkouts = listOf(
            workout(
                id = 1L,
                date = weekStart,
                exercises = listOf(
                    exerciseLog("barbell-bench-press", "Barbell bench press", *sets(5)),
                    exerciseLog("barbell-full-squat", "Barbell full squat", *sets(4)),
                    exerciseLog("barbell-bent-over-row", "Barbell bent over row", *sets(3)),
                    exerciseLog("dumbbell-lateral-raise", "Dumbbell lateral raise", *sets(2)),
                    exerciseLog("barbell-curl", "Barbell curl", *sets(1)),
                ),
            ),
        )

        val items = calculate(WeekProgressDisplayMode.MUSCLE_SETS, weekWorkouts = weekWorkouts)

        // 计数：胸 5、肱三头 5、股四头 4、臀 4、肱二头 4（3+1）、背阔 3、肩 2 → top4 截断
        assertEquals(4, items.size)
        assertEquals(listOf("5 组", "5 组", "4 组", "4 组"), items.map { it.subtitle })
        assertTrue(items.none { it.title == "肩部" }) // 2 组被截断
    }

    @Test
    fun `MUSCLE_SETS empty week returns fallback`() {
        val items = calculate(WeekProgressDisplayMode.MUSCLE_SETS)
        assertEquals(1, items.size)
        assertEquals("暂无数据", items[0].title)
    }

    // ── VOLUME_PR ──

    @Test
    fun `VOLUME_PR head formats tonnage above 1000kg`() {
        val weekWorkouts = listOf(
            workout(
                id = 1L,
                date = weekStart,
                exercises = listOf(
                    exerciseLog("barbell-full-squat", "Barbell full squat", working(100f, 10), working(100f, 10)),
                ),
            ),
        )

        val items = calculate(
            WeekProgressDisplayMode.VOLUME_PR,
            weekWorkouts = weekWorkouts,
            allWorkouts = weekWorkouts,
        )

        assertEquals("周总容量", items[0].title)
        assertEquals("2.0 吨", items[0].subtitle)
    }

    @Test
    fun `VOLUME_PR detects new PR against history`() {
        // 历史最佳：卧推 80kg × 5 → e1RM ≈ 93.3；本周：85kg × 5 → e1RM ≈ 99.2
        val history = listOf(
            workout(
                id = 1L,
                date = weekStart.minusDays(7),
                exercises = listOf(exerciseLog("barbell-bench-press", "Barbell bench press", working(80f, 5))),
            ),
        )
        val thisWeek = listOf(
            workout(
                id = 2L,
                date = weekStart.plusDays(1),
                exercises = listOf(exerciseLog("barbell-bench-press", "Barbell bench press", working(85f, 5))),
            ),
        )

        val items = calculate(
            WeekProgressDisplayMode.VOLUME_PR,
            weekWorkouts = thisWeek,
            allWorkouts = history + thisWeek,
        )

        assertTrue(items.size >= 2)
        assertEquals("Barbell bench press", items[1].title)
        assertEquals("新 PR · 99.2 kg", items[1].subtitle)
    }

    @Test
    fun `VOLUME_PR without history shows no-breakthrough fallback`() {
        val thisWeek = listOf(
            workout(
                id = 1L,
                date = weekStart,
                exercises = listOf(exerciseLog("barbell-bench-press", "Barbell bench press", working(80f, 5))),
            ),
        )

        val items = calculate(
            WeekProgressDisplayMode.VOLUME_PR,
            weekWorkouts = thisWeek,
            allWorkouts = thisWeek,
        )

        assertEquals(2, items.size)
        assertEquals("本周暂无突破", items[1].title)
        assertEquals("继续保持", items[1].subtitle)
    }

    @Test
    fun `VOLUME_PR empty week returns fallback`() {
        val items = calculate(WeekProgressDisplayMode.VOLUME_PR)
        assertEquals(1, items.size)
        assertEquals("暂无数据", items[0].title)
    }

    // ── CATEGORY ──

    @Test
    fun `CATEGORY counts same body part once per workout and accumulates across workouts`() {
        val weekWorkouts = listOf(
            workout(
                id = 1L,
                date = weekStart,
                exercises = listOf(
                    // 同一次训练两个胸部动作 → 胸只计 1 次
                    exerciseLog("barbell-bench-press", "Barbell bench press", working(80f, 10)),
                    exerciseLog("barbell-bench-press", "Barbell bench press", working(85f, 8)),
                    exerciseLog("barbell-full-squat", "Barbell full squat", working(100f, 5)),
                ),
            ),
            workout(
                id = 2L,
                date = weekStart.plusDays(2),
                exercises = listOf(
                    exerciseLog("barbell-bench-press", "Barbell bench press", working(80f, 10)),
                ),
            ),
        )

        val items = calculate(WeekProgressDisplayMode.CATEGORY, weekWorkouts = weekWorkouts)

        val byTitle = items.associateBy { it.title }
        assertEquals("2 sessions", byTitle["胸部"]?.subtitle)
        assertEquals("1 sessions", byTitle["腿/臀"]?.subtitle)
    }

    @Test
    fun `CATEGORY empty week returns fallback`() {
        val items = calculate(WeekProgressDisplayMode.CATEGORY)
        assertEquals(1, items.size)
        assertEquals("暂无数据", items[0].title)
    }

    // ── 辅助方法 ──

    private fun calculate(
        mode: WeekProgressDisplayMode,
        weekWorkouts: List<Workout> = emptyList(),
        allWorkouts: List<Workout> = emptyList(),
        activePlan: WorkoutPlan? = null,
    ) = WeekProgressCalculator.calculate(
        mode = mode,
        weekWorkouts = weekWorkouts,
        allWorkouts = allWorkouts,
        activePlan = activePlan,
        catalog = catalog,
        weekStart = weekStart,
    )

    private fun exercise(key: String, name: String, primary: List<Muscle>, bodyPart: BodyPart) = Exercise(
        id = key,
        name = name,
        primaryMuscles = primary,
        bodyPart = bodyPart,
    )

    private fun workout(id: Long, date: LocalDate, exercises: List<ExerciseLog> = emptyList()) = Workout(
        id = id,
        userId = 0L,
        date = date,
        exercises = exercises,
        feelings = null,
    )

    private fun exerciseLog(key: String?, name: String, vararg setLogs: SetLog) = ExerciseLog(
        name = name,
        exerciseKey = key,
        sets = setLogs.toList(),
    )

    private fun working(weight: Float, reps: Int) = SetLog(weight, reps, SetType.WORKING)

    private fun warmup(weight: Float, reps: Int) = SetLog(weight, reps, SetType.WARMUP)

    /** 生成 count 个正式组（容量测试之外的凑数组）。 */
    private fun sets(count: Int) = Array(count) { working(50f, 10) }

    private fun plan(sessionsPerWeek: Int, sessions: List<PlannedSession>) = WorkoutPlan(
        id = "plan-1",
        name = "测试计划",
        description = null,
        goal = null,
        durationWeeks = 4,
        sessionsPerWeek = sessionsPerWeek,
        isCustom = false,
        createdAt = weekStart,
        rawPlanText = null,
        sessions = sessions,
    )

    private fun session(id: String, name: String, completedWorkoutId: Long? = null) = PlannedSession(
        id = id,
        name = name,
        description = null,
        dayNumber = 1,
        weekNumber = 1,
        targetDurationMinutes = 60,
        exercises = listOf(
            PlannedExerciseItem(exerciseKey = "barbell-bench-press", targetSets = 4, order = 0),
        ),
        completedWorkoutId = completedWorkoutId,
    )
}
