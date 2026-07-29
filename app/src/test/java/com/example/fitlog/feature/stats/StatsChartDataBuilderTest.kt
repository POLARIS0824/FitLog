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
import java.time.YearMonth

/**
 * [StatsChartDataBuilder] 的单元测试（纯 JVM）。
 *
 * 验证四档分桶的边界与 id 命名空间（插值动画的匹配锚点）、WORKING 口径、
 * yMax 留白取整（1-2-5-10）、日均与区间文案，以及空数据降级。
 *
 * 注意：周/年档标签文本随 Locale 变化（NARROW/SHORT），一律不断言其内容；
 * 月/三月档标签是数字拼接，可安全断言。
 */
class StatsChartDataBuilderTest {

    // 2026-07-25 为周六，所在周周一 = 2026-07-20
    private val today = LocalDate.of(2026, 7, 25)

    // ── 分桶边界与 id 命名空间 ──

    @Test
    fun `WEEK produces 7 daily buckets ending today`() {
        val state = build(StatsPeriod.WEEK, workout(today, 800.0))

        val entries = state.chartData.entries
        assertEquals(7, entries.size)
        assertEquals(today.minusDays(6).toString(), entries.first().id)
        assertEquals(today.toString(), entries.last().id)
        assertEquals(800f, entries.last().value, 0.01f)
    }

    @Test
    fun `MONTH produces 30 daily buckets with day-of-month labels`() {
        val state = build(StatsPeriod.MONTH)

        val entries = state.chartData.entries
        assertEquals(30, entries.size)
        assertEquals(today.minusDays(29).toString(), entries.first().id)
        assertEquals(today.toString(), entries.last().id)
        assertEquals("${today.dayOfMonth}", entries.last().label)
    }

    @Test
    fun `THREE_MONTHS produces 13 Monday-aligned weekly buckets`() {
        val state = build(StatsPeriod.THREE_MONTHS, workout(today, 1300.0))

        val entries = state.chartData.entries
        assertEquals(13, entries.size)
        val firstMonday = today.with(DayOfWeek.MONDAY).minusWeeks(12)
        assertEquals(firstMonday.toString(), entries.first().id)
        entries.forEach { entry ->
            assertEquals(DayOfWeek.MONDAY, LocalDate.parse(entry.id).dayOfWeek)
        }
        // 最末桶为不完整桶（本周一 .. today），本周训练计入末桶
        assertEquals(1300f, entries.last().value, 0.01f)
        assertEquals(
            "${firstMonday.monthValue}/${firstMonday.dayOfMonth}",
            entries.first().label,
        )
    }

    @Test
    fun `YEAR produces 12 calendar month buckets`() {
        val state = build(StatsPeriod.YEAR, workout(today, 600.0))

        val entries = state.chartData.entries
        assertEquals(12, entries.size)
        assertEquals(YearMonth.from(today).minusMonths(11).toString(), entries.first().id)
        assertEquals(YearMonth.from(today).toString(), entries.last().id)
        assertEquals(600f, entries.last().value, 0.01f)
    }

    // ── 容量口径 ──

    @Test
    fun `volume counts WORKING sets only and sums workouts on the same day`() {
        val mixed = Workout(
            id = 0L,
            userId = 0L,
            date = today,
            exercises = listOf(
                ExerciseLog(
                    name = "卧推",
                    sets = listOf(
                        SetLog(40f, 12, SetType.WARMUP), // 480 不计
                        SetLog(80f, 10, SetType.WORKING), // 800
                        SetLog(85f, 8, SetType.WORKING), // 680
                    ),
                ),
            ),
            feelings = null,
        )
        val state = build(StatsPeriod.WEEK, mixed, workout(today, 520.0))

        assertEquals(2000f, state.chartData.entries.last().value, 0.01f)
    }

    @Test
    fun `workouts outside the window are ignored`() {
        val state = build(StatsPeriod.WEEK, workout(today.minusDays(40), 5000.0))

        assertFalse(state.hasData)
        assertTrue(state.chartData.entries.all { it.value == 0f })
    }

    // ── yMax / 日均 / 文案 ──

    @Test
    fun `yMax is nice-ceiled with 15 percent headroom`() {
        // 1000 × 1.15 = 1150 → 2000；400 × 1.15 = 460 → 500
        assertEquals(2000f, build(StatsPeriod.WEEK, workout(today, 1000.0)).chartData.yMax, 0.01f)
        assertEquals(500f, build(StatsPeriod.WEEK, workout(today, 400.0)).chartData.yMax, 0.01f)
    }

    @Test
    fun `niceCeil snaps up to the 1-2-5 series with floor 1`() {
        assertEquals(1f, StatsChartDataBuilder.niceCeil(0.0))
        assertEquals(1f, StatsChartDataBuilder.niceCeil(-3.0))
        assertEquals(1f, StatsChartDataBuilder.niceCeil(1.0))
        assertEquals(2f, StatsChartDataBuilder.niceCeil(1.15))
        assertEquals(200f, StatsChartDataBuilder.niceCeil(200.0))
        assertEquals(500f, StatsChartDataBuilder.niceCeil(430.0))
        assertEquals(1000f, StatsChartDataBuilder.niceCeil(999.0))
        assertEquals(2000f, StatsChartDataBuilder.niceCeil(1150.0))
    }

    @Test
    fun `average divides total volume by calendar days in range`() {
        // WEEK 区间恒为 7 天：700 / 7 = 100
        val week = build(StatsPeriod.WEEK, workout(today, 700.0))
        assertEquals("100 kg", week.averageVolumeText)
        // 总量 14000 / 7 = 2000 → 吨的格式化口径（≥1000kg 一位小数）
        val tonnes = build(StatsPeriod.WEEK, workout(today, 14000.0))
        assertEquals("2.0 吨", tonnes.averageVolumeText)
    }

    @Test
    fun `rangeText omits start year within the same year and keeps both across years`() {
        val week = build(StatsPeriod.WEEK, workout(today, 700.0))
        assertEquals("7月19日 – 7月25日", week.rangeText)

        val year = build(StatsPeriod.YEAR, workout(today, 700.0))
        assertEquals("2025年8月1日 – 2026年7月25日", year.rangeText)
    }

    @Test
    fun `empty input yields zero chart with floored yMax`() {
        val state = build(StatsPeriod.WEEK)

        assertFalse(state.hasData)
        assertEquals(1f, state.chartData.yMax)
        assertEquals("0 kg", state.averageVolumeText)
    }

    // ── 查询区间 ──

    @Test
    fun `rangeOf covers exactly the bucket windows`() {
        assertEquals(today.minusDays(6), StatsChartDataBuilder.rangeOf(StatsPeriod.WEEK, today).start)
        assertEquals(today.minusDays(29), StatsChartDataBuilder.rangeOf(StatsPeriod.MONTH, today).start)
        assertEquals(
            today.with(DayOfWeek.MONDAY).minusWeeks(12),
            StatsChartDataBuilder.rangeOf(StatsPeriod.THREE_MONTHS, today).start,
        )
        assertEquals(
            YearMonth.from(today).minusMonths(11).atDay(1),
            StatsChartDataBuilder.rangeOf(StatsPeriod.YEAR, today).start,
        )
        StatsPeriod.entries.forEach { period ->
            assertEquals(today, StatsChartDataBuilder.rangeOf(period, today).endInclusive)
        }
    }

    // ── 刻度格式化 ──

    @Test
    fun `formatAxisValue compacts tonnes without unit suffix below 1t`() {
        assertEquals("0", StatsChartDataBuilder.formatAxisValue(0f))
        assertEquals("500", StatsChartDataBuilder.formatAxisValue(500f))
        assertEquals("999", StatsChartDataBuilder.formatAxisValue(999f))
        assertEquals("1t", StatsChartDataBuilder.formatAxisValue(1000f))
        assertEquals("1.5t", StatsChartDataBuilder.formatAxisValue(1500f))
        assertEquals("12t", StatsChartDataBuilder.formatAxisValue(12000f))
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

    private fun build(period: StatsPeriod, vararg workouts: Workout): StatsChartState =
        StatsChartDataBuilder.build(workouts.toList(), period, today)
}
