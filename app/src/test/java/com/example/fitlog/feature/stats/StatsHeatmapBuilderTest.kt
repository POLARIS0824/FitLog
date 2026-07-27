package com.example.fitlog.feature.stats

import com.example.fitlog.model.ExerciseLog
import com.example.fitlog.model.SetLog
import com.example.fitlog.model.SetType
import com.example.fitlog.model.Workout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * [StatsHeatmapBuilder] 的单元测试（纯 JVM）。
 *
 * 验证 53 周窗口的周一对齐与裁剪（归一 max 不被窗口外离群值带偏）、
 * 同日容量合并、0 容量日缺席，以及最长连续天数的链式计算。
 */
class StatsHeatmapBuilderTest {

    // 2026-07-25 为周六，所在周周一 = 2026-07-20
    private val today = LocalDate.of(2026, 7, 25)

    @Test
    fun `windowStart is Monday aligned 52 weeks back`() {
        val start = StatsHeatmapBuilder.windowStart(today)

        assertEquals(DayOfWeek.MONDAY, start.dayOfWeek)
        assertEquals(today.with(DayOfWeek.MONDAY).minusWeeks(52), start)
    }

    @Test
    fun `daily volumes merge same-day workouts and skip zero volume days`() {
        val state = StatsHeatmapBuilder.build(
            workouts = listOf(
                workout(today, 800.0),
                workout(today, 400.0), // 同日第二次：合并为 1200
                workout(today.minusDays(1), 0.0), // 0 容量日：缺席
            ),
            today = today,
        )

        assertEquals(1200f, state.values[today] ?: 0f, 0.01f)
        assertFalse(state.values.containsKey(today.minusDays(1)))
        assertEquals(1, state.trainedDays)
    }

    @Test
    fun `workouts outside 53 week window are clipped`() {
        val outside = StatsHeatmapBuilder.windowStart(today).minusDays(1)
        val state = StatsHeatmapBuilder.build(
            workouts = listOf(
                workout(outside, 99999.0), // 窗口外离群高容量：不得带偏归一 max
                workout(today, 800.0),
            ),
            today = today,
        )

        assertFalse(state.values.containsKey(outside))
        assertEquals(1, state.trainedDays)
        assertEquals(800f, state.values.values.max(), 0.01f)
    }

    @Test
    fun `warmup sets do not count`() {
        val warmupOnly = Workout(
            id = 0L,
            userId = 0L,
            date = today,
            exercises = listOf(
                ExerciseLog(
                    name = "测试动作",
                    sets = listOf(SetLog(weightKg = 50f, reps = 12, setType = SetType.WARMUP)),
                ),
            ),
            feelings = null,
        )

        val state = StatsHeatmapBuilder.build(listOf(warmupOnly), today)

        assertTrue(state.values.isEmpty())
        assertEquals(0, state.trainedDays)
    }

    @Test
    fun `longestStreak counts consecutive natural days across weeks`() {
        val dates = listOf(
            LocalDate.of(2026, 7, 18), // 周六
            LocalDate.of(2026, 7, 19), // 周日
            LocalDate.of(2026, 7, 20), // 周一（跨周不断）
            LocalDate.of(2026, 7, 23), // 孤立日
        )

        assertEquals(3, StatsHeatmapBuilder.longestStreak(dates))
    }

    @Test
    fun `longestStreak of empty is zero`() {
        assertEquals(0, StatsHeatmapBuilder.longestStreak(emptyList()))
    }

    @Test
    fun `empty workouts degrade to empty state`() {
        val state = StatsHeatmapBuilder.build(emptyList(), today)

        assertTrue(state.values.isEmpty())
        assertEquals(0, state.trainedDays)
        assertEquals(0, state.longestStreak)
    }

    // ── 辅助方法 ──

    /** 单动作单组正式组，重量×次数 = [volumeKg]。 */
    private fun workout(date: LocalDate, volumeKg: Double): Workout = Workout(
        id = 0L,
        userId = 0L,
        date = date,
        exercises = listOf(
            ExerciseLog(
                name = "测试动作",
                sets = listOf(
                    SetLog(
                        weightKg = (volumeKg / 10).toFloat(),
                        reps = 10,
                        setType = SetType.WORKING,
                    ),
                ),
            ),
        ),
        feelings = null,
    )
}
