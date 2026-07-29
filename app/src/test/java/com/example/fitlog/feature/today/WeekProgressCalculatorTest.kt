package com.example.fitlog.feature.today

import com.example.fitlog.model.BodyPart
import com.example.fitlog.model.Equipment
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
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * [WeekProgressCalculator] 的单元测试（纯 JVM）。
 *
 * 验证四种展示模式「固定 4 个渲染项（1 大 + 3 小）」的新契约：
 * SPLIT 的下一/最近训练解析、MUSCLE_SETS 的大部位覆盖与最少/最多肌群、
 * VOLUME_PR 的环比文案/PR 实际最佳组/最大增长、CATEGORY 的力量有氧二分与环形分段。
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
        exercise("treadmill-run", "Treadmill run", listOf(Muscle.CARDIO), BodyPart.CARDIO, Equipment.CARDIO_MACHINE),
    )

    // ── SPLIT ──

    @Test
    fun `SPLIT head shows count target and progress, placeholders explicit`() {
        val items = calculate(
            WeekProgressDisplayMode.SPLIT,
            weekWorkouts = listOf(workout(id = 1L, date = weekStart)),
            targetWorkouts = 3,
        )

        assertEquals(4, items.size)
        val head = items[0]
        assertEquals("本周训练", head.title)
        assertEquals("1 次", head.valueText)
        assertEquals("目标 3 次", head.subtitle)
        assertEquals(1f / 3f, head.progress!!, 1e-6f)
        // 无计划时下一训练降级
        assertEquals("下一训练", items[1].title)
        assertEquals("无计划", items[1].subtitle)
        // 补剂摄入占位
        assertEquals("补剂摄入", items[3].title)
        assertEquals("即将上线", items[3].subtitle)
    }

    @Test
    fun `SPLIT next session shows name, or completion state`() {
        val plan = plan(sessionsPerWeek = 3, sessions = listOf(session(id = "w1d1", name = "推日")))

        val withNext = calculate(
            WeekProgressDisplayMode.SPLIT,
            activePlan = plan,
            nextSession = session(id = "w1d1", name = "推日"),
        )
        assertEquals("推日", withNext[1].subtitle)

        val allDone = calculate(WeekProgressDisplayMode.SPLIT, activePlan = plan, nextSession = null)
        assertEquals("全部完成", allDone[1].subtitle)
    }

    @Test
    fun `SPLIT last session resolves plan session name via completed marker`() {
        val plan = plan(
            sessionsPerWeek = 3,
            sessions = listOf(session(id = "w1d2", name = "拉日 · 背二头", completedWorkoutId = 10L)),
        )
        val latest = workout(id = 10L, date = weekStart.minusDays(1))

        val items = calculate(
            WeekProgressDisplayMode.SPLIT,
            activePlan = plan,
            latestWorkout = latest,
        )

        assertEquals("最近训练", items[2].title)
        assertEquals("拉日 · 背二头", items[2].subtitle)
    }

    @Test
    fun `SPLIT last session derives dominant body part when not in plan`() {
        val latest = workout(
            id = 1L,
            date = weekStart,
            exercises = listOf(
                exerciseLog("barbell-bench-press", "Barbell bench press", working(80f, 10), working(85f, 8)),
                exerciseLog("barbell-bent-over-row", "Barbell bent over row", working(60f, 10)),
            ),
        )

        val items = calculate(WeekProgressDisplayMode.SPLIT, latestWorkout = latest)

        assertEquals("胸部训练", items[2].subtitle)
    }

    @Test
    fun `SPLIT last session falls back to free workout or empty`() {
        val unknown = workout(
            id = 1L,
            date = weekStart,
            exercises = listOf(exerciseLog("unknown-key", "未知动作", working(20f, 12))),
        )
        assertEquals(
            "自由训练",
            calculate(WeekProgressDisplayMode.SPLIT, latestWorkout = unknown)[2].subtitle,
        )
        assertEquals(
            "暂无训练",
            calculate(WeekProgressDisplayMode.SPLIT, latestWorkout = null)[2].subtitle,
        )
    }

    // ── MUSCLE_SETS ──

    @Test
    fun `MUSCLE_SETS head reports coverage and working sets, warmups excluded`() {
        val weekWorkouts = listOf(
            workout(
                id = 1L,
                date = weekStart,
                exercises = listOf(
                    exerciseLog(
                        "barbell-bench-press", "Barbell bench press",
                        warmup(40f, 12), working(80f, 10), working(85f, 8),
                    ),
                    exerciseLog("barbell-bent-over-row", "Barbell bent over row", working(60f, 10)),
                ),
            ),
        )

        val items = calculate(WeekProgressDisplayMode.MUSCLE_SETS, weekWorkouts = weekWorkouts)

        assertEquals(4, items.size)
        val head = items[0]
        assertEquals("肌肉覆盖", head.title)
        assertEquals("2 部位", head.valueText)
        assertEquals("有效 3 组", head.subtitle)
        assertEquals(2f / 7f, head.progress!!, 1e-6f)
    }

    @Test
    fun `MUSCLE_SETS under-trained picks a zero-set major part, top muscle shows sets`() {
        val weekWorkouts = listOf(
            workout(
                id = 1L,
                date = weekStart,
                exercises = listOf(
                    exerciseLog("barbell-bench-press", "Barbell bench press", working(80f, 10), working(85f, 8)),
                    exerciseLog("barbell-bent-over-row", "Barbell bent over row", working(60f, 10)),
                ),
            ),
        )

        val items = calculate(WeekProgressDisplayMode.MUSCLE_SETS, weekWorkouts = weekWorkouts)

        // 大部位中肩/上臂/腿/小腿/腰腹均为 0 组，按 MAJOR_BODY_PARTS 列表序首个为肩部
        assertEquals("训练不足", items[1].title)
        assertEquals("肩部", items[1].subtitle)
        assertEquals("重点肌群", items[2].title)
        assertEquals("胸部 · 2 组", items[2].subtitle)
        assertEquals("肌群平衡", items[3].title)
        assertEquals("AI 即将上线", items[3].subtitle)
    }

    @Test
    fun `MUSCLE_SETS resolves exercise by name when key misses`() {
        val weekWorkouts = listOf(
            workout(
                id = 1L,
                date = weekStart,
                exercises = listOf(exerciseLog("unknown-key", "Barbell curl", working(20f, 12))),
            ),
        )

        val items = calculate(WeekProgressDisplayMode.MUSCLE_SETS, weekWorkouts = weekWorkouts)

        assertEquals("1 部位", items[0].valueText)
        assertEquals("上臂 · 1 组", items[2].subtitle)
    }

    @Test
    fun `MUSCLE_SETS empty week shows zero coverage and dashes`() {
        val items = calculate(WeekProgressDisplayMode.MUSCLE_SETS)

        assertEquals("0 部位", items[0].valueText)
        assertEquals("有效 0 组", items[0].subtitle)
        assertEquals(0f, items[0].progress!!, 1e-6f)
        assertEquals("—", items[1].subtitle)
        assertEquals("—", items[2].subtitle)
    }

    // ── VOLUME_PR ──

    @Test
    fun `VOLUME_PR head compares with previous week`() {
        val prev = listOf(
            workout(
                id = 1L,
                date = weekStart.minusDays(7),
                exercises = listOf(exerciseLog("barbell-full-squat", "Barbell full squat", working(100f, 10))),
            ),
        )
        val week = listOf(
            workout(
                id = 2L,
                date = weekStart,
                exercises = listOf(
                    exerciseLog("barbell-full-squat", "Barbell full squat", working(100f, 10), working(100f, 10)),
                ),
            ),
        )

        val items = calculate(
            WeekProgressDisplayMode.VOLUME_PR,
            weekWorkouts = week,
            prevWeekWorkouts = prev,
        )

        val head = items[0]
        assertEquals("训练容量", head.title)
        assertEquals("2.0 吨", head.valueText)
        assertEquals("较上周 +100%", head.subtitle)
        assertEquals(1f, head.progress!!, 1e-6f) // 2.0/1.0 截断到 1
    }

    @Test
    fun `VOLUME_PR head handles decrease and parity`() {
        val prev = listOf(
            workout(
                id = 1L,
                date = weekStart.minusDays(7),
                exercises = listOf(
                    exerciseLog("barbell-full-squat", "Barbell full squat", working(100f, 10), working(100f, 10)),
                ),
            ),
        )
        val week = listOf(
            workout(
                id = 2L,
                date = weekStart,
                exercises = listOf(exerciseLog("barbell-full-squat", "Barbell full squat", working(100f, 10))),
            ),
        )

        val decreased = calculate(
            WeekProgressDisplayMode.VOLUME_PR,
            weekWorkouts = week,
            prevWeekWorkouts = prev,
        )
        assertEquals("较上周 -50%", decreased[0].subtitle)
        assertEquals(0.5f, decreased[0].progress!!, 1e-6f)

        val parity = calculate(
            WeekProgressDisplayMode.VOLUME_PR,
            weekWorkouts = week,
            prevWeekWorkouts = week,
        )
        assertEquals("与上周持平", parity[0].subtitle)
    }

    @Test
    fun `VOLUME_PR head without baseline is static`() {
        val week = listOf(
            workout(
                id = 1L,
                date = weekStart,
                exercises = listOf(exerciseLog("barbell-full-squat", "Barbell full squat", working(100f, 10))),
            ),
        )

        val items = calculate(WeekProgressDisplayMode.VOLUME_PR, weekWorkouts = week)

        assertEquals("上周无数据", items[0].subtitle)
        assertNull(items[0].progress)

        val empty = calculate(WeekProgressDisplayMode.VOLUME_PR)
        assertEquals("本周还没有训练", empty[0].subtitle)
        assertNull(empty[0].progress)
    }

    @Test
    fun `VOLUME_PR shows top PR with actual best working set`() {
        // 历史最佳：卧推 80kg×5（e1RM≈93.3）、深蹲 100kg×5（e1RM≈116.7）
        val history = listOf(
            workout(
                id = 1L,
                date = weekStart.minusDays(7),
                exercises = listOf(
                    exerciseLog("barbell-bench-press", "Barbell bench press", working(80f, 5)),
                    exerciseLog("barbell-full-squat", "Barbell full squat", working(100f, 5)),
                ),
            ),
        )
        // 本周：卧推 82kg×5（margin≈2.3）、深蹲 110kg×5（margin≈11.7）→ 取 margin 最大的深蹲
        val week = listOf(
            workout(
                id = 2L,
                date = weekStart.plusDays(1),
                exercises = listOf(
                    exerciseLog("barbell-bench-press", "Barbell bench press", working(82f, 5)),
                    exerciseLog(
                        "barbell-full-squat", "Barbell full squat",
                        warmup(60f, 10), working(110f, 5),
                    ),
                ),
            ),
        )

        val items = calculate(
            WeekProgressDisplayMode.VOLUME_PR,
            weekWorkouts = week,
            prevWeekWorkouts = history,
            allWorkouts = history + week,
        )

        assertEquals("PR", items[1].title)
        assertEquals("Barbell full squat 110kg×5", items[1].subtitle)
    }

    @Test
    fun `VOLUME_PR without breakthrough falls back`() {
        val week = listOf(
            workout(
                id = 1L,
                date = weekStart,
                exercises = listOf(exerciseLog("barbell-bench-press", "Barbell bench press", working(80f, 5))),
            ),
        )

        val noHistory = calculate(
            WeekProgressDisplayMode.VOLUME_PR,
            weekWorkouts = week,
            allWorkouts = week,
        )
        assertEquals("暂无突破", noHistory[1].subtitle)

        val emptyWeek = calculate(WeekProgressDisplayMode.VOLUME_PR)
        assertEquals("暂无数据", emptyWeek[1].subtitle)
    }

    @Test
    fun `VOLUME_PR growth compares volume per body part`() {
        val prev = listOf(
            workout(
                id = 1L,
                date = weekStart.minusDays(7),
                exercises = listOf(exerciseLog("barbell-bent-over-row", "Barbell bent over row", working(60f, 10))),
            ),
        )
        val week = listOf(
            workout(
                id = 2L,
                date = weekStart,
                exercises = listOf(
                    exerciseLog("barbell-bent-over-row", "Barbell bent over row", working(60f, 10)),
                    exerciseLog("barbell-bench-press", "Barbell bench press", working(80f, 10)),
                ),
            ),
        )

        val items = calculate(
            WeekProgressDisplayMode.VOLUME_PR,
            weekWorkouts = week,
            prevWeekWorkouts = prev,
        )

        // 背部 +0、胸部 +800kg → 胸部为最大增长
        assertEquals("最大增长", items[2].title)
        assertEquals("胸部 +800 kg", items[2].subtitle)
    }

    @Test
    fun `VOLUME_PR growth handles missing baseline and no growth`() {
        val week = listOf(
            workout(
                id = 1L,
                date = weekStart,
                exercises = listOf(exerciseLog("barbell-bent-over-row", "Barbell bent over row", working(60f, 10))),
            ),
        )

        val noBaseline = calculate(WeekProgressDisplayMode.VOLUME_PR, weekWorkouts = week)
        assertEquals("暂无基线", noBaseline[2].subtitle)

        val prev = listOf(
            workout(
                id = 2L,
                date = weekStart.minusDays(7),
                exercises = listOf(exerciseLog("barbell-bent-over-row", "Barbell bent over row", working(80f, 10))),
            ),
        )
        val noGrowth = calculate(
            WeekProgressDisplayMode.VOLUME_PR,
            weekWorkouts = week,
            prevWeekWorkouts = prev,
        )
        assertEquals("暂无增长", noGrowth[2].subtitle)

        assertEquals("AI 分析", noGrowth[3].title)
        assertEquals("即将上线", noGrowth[3].subtitle)
    }

    // ── CATEGORY ──

    @Test
    fun `CATEGORY splits workouts into strength and cardio with ring segments`() {
        val weekWorkouts = listOf(
            workout(
                id = 1L,
                date = weekStart,
                exercises = listOf(exerciseLog("barbell-bench-press", "Barbell bench press", working(80f, 10))),
            ),
            workout(
                id = 2L,
                date = weekStart.plusDays(1),
                exercises = listOf(exerciseLog("barbell-full-squat", "Barbell full squat", working(100f, 5))),
            ),
            workout(
                id = 3L,
                date = weekStart.plusDays(2),
                exercises = listOf(exerciseLog("treadmill-run", "Treadmill run", working(0f, 30))),
            ),
        )

        val items = calculate(WeekProgressDisplayMode.CATEGORY, weekWorkouts = weekWorkouts)

        val head = items[0]
        assertEquals("训练分布", head.title)
        assertEquals("3 次", head.valueText)
        assertEquals("力量 2 · 有氧 1", head.subtitle)
        assertNull(head.progress)
        val segments = head.ringSegments!!
        assertEquals(3, segments.size)
        assertEquals("力量", segments[0].label)
        assertEquals(2f / 3f, segments[0].fraction, 1e-6f)
        assertEquals("67%", segments[0].valueText)
        assertEquals("有氧", segments[1].label)
        assertEquals(1f / 3f, segments[1].fraction, 1e-6f)
        assertEquals("33%", segments[1].valueText)
        // 恢复类保留位：不进环，图例显 —
        assertEquals("恢复", segments[2].label)
        assertEquals(0f, segments[2].fraction, 1e-6f)
        assertEquals("—", segments[2].valueText)

        assertEquals("力量训练", items[1].title)
        assertEquals("2 次", items[1].subtitle)
        assertEquals("有氧训练", items[2].title)
        assertEquals("1 次", items[2].subtitle)
        assertEquals("运动目标", items[3].title)
        assertEquals("即将上线", items[3].subtitle)
    }

    @Test
    fun `CATEGORY dominant category by working sets, tie goes to strength`() {
        val tie = workout(
            id = 1L,
            date = weekStart,
            exercises = listOf(
                exerciseLog("barbell-bench-press", "Barbell bench press", working(80f, 10)),
                exerciseLog("treadmill-run", "Treadmill run", working(0f, 30)),
            ),
        )
        val tieItems = calculate(WeekProgressDisplayMode.CATEGORY, weekWorkouts = listOf(tie))
        assertEquals("1 次", tieItems[1].subtitle) // 力量
        assertEquals("0 次", tieItems[2].subtitle) // 有氧

        val cardioDominant = workout(
            id = 2L,
            date = weekStart,
            exercises = listOf(
                exerciseLog("barbell-bench-press", "Barbell bench press", working(80f, 10)),
                exerciseLog("treadmill-run", "Treadmill run", working(0f, 30), working(0f, 20)),
            ),
        )
        val cardioItems = calculate(WeekProgressDisplayMode.CATEGORY, weekWorkouts = listOf(cardioDominant))
        assertEquals("0 次", cardioItems[1].subtitle)
        assertEquals("1 次", cardioItems[2].subtitle)
    }

    @Test
    fun `CATEGORY excludes workouts with no resolvable working sets`() {
        val weekWorkouts = listOf(
            workout(
                id = 1L,
                date = weekStart,
                exercises = listOf(exerciseLog("unknown-key", "未知动作", working(20f, 12))),
            ),
            workout(
                id = 2L,
                date = weekStart.plusDays(1),
                exercises = listOf(exerciseLog("barbell-bench-press", "Barbell bench press", working(80f, 10))),
            ),
        )

        val items = calculate(WeekProgressDisplayMode.CATEGORY, weekWorkouts = weekWorkouts)

        assertEquals("2 次", items[0].valueText) // 大卡仍计全部训练次数
        assertEquals("力量 1 · 有氧 0", items[0].subtitle)
    }

    @Test
    fun `CATEGORY empty week shows dashes in legend`() {
        val items = calculate(WeekProgressDisplayMode.CATEGORY)

        assertEquals("0 次", items[0].valueText)
        val segments = items[0].ringSegments!!
        assertEquals("—", segments[0].valueText)
        assertEquals("—", segments[1].valueText)
        assertEquals("0 次", items[1].subtitle)
        assertEquals("0 次", items[2].subtitle)
    }

    // ── 辅助方法 ──

    private fun calculate(
        mode: WeekProgressDisplayMode,
        weekWorkouts: List<Workout> = emptyList(),
        prevWeekWorkouts: List<Workout> = emptyList(),
        allWorkouts: List<Workout> = emptyList(),
        activePlan: WorkoutPlan? = null,
        nextSession: PlannedSession? = null,
        latestWorkout: Workout? = null,
        targetWorkouts: Int = 4,
    ) = WeekProgressCalculator.calculate(
        mode = mode,
        weekWorkouts = weekWorkouts,
        prevWeekWorkouts = prevWeekWorkouts,
        allWorkouts = allWorkouts,
        activePlan = activePlan,
        nextSession = nextSession,
        latestWorkout = latestWorkout,
        targetWorkouts = targetWorkouts,
        catalog = catalog,
        weekStart = weekStart,
    )

    private fun exercise(
        key: String,
        name: String,
        primary: List<Muscle>,
        bodyPart: BodyPart,
        equipment: Equipment? = null,
    ) = Exercise(
        id = key,
        name = name,
        primaryMuscles = primary,
        bodyPart = bodyPart,
        equipment = equipment,
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
