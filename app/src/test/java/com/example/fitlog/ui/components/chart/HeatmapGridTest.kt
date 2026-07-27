package com.example.fitlog.ui.components.chart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month

/**
 * [HeatmapGrid] 与 [HeatmapLevels] 的单元测试（纯 JVM）。
 *
 * 验证周列网格对齐（周一始、末列含 endDate）、线性分档边界、
 * 月标签的位置与最小间距规则、命中测试的间距带/越界行为。
 * 不断言任何 Locale 相关文本。
 */
class HeatmapGridTest {

    // 2026-07-25 为周六，所在周周一 = 2026-07-20
    private val endDate = LocalDate.of(2026, 7, 25)

    // ── buildWeekColumns ──

    @Test
    fun `week columns are Monday-start seven-day weeks ending at endDate's week`() {
        val columns = HeatmapGrid.buildWeekColumns(endDate, weekCount = 53)

        assertEquals(53, columns.size)
        columns.forEach { column ->
            assertEquals(HeatmapGrid.DAYS_PER_WEEK, column.size)
            assertEquals(DayOfWeek.MONDAY, column.first().dayOfWeek)
            assertEquals(column.first().plusDays(6), column.last())
        }
        // 末列 = endDate 所在周；首列 = 末列周一 - 52 周
        assertTrue(columns.last().contains(endDate))
        assertEquals(LocalDate.of(2026, 7, 20), columns.last().first())
        assertEquals(LocalDate.of(2025, 7, 21), columns.first().first())
        // 相邻列恰好差 7 天
        columns.zipWithNext().forEach { (prev, next) ->
            assertEquals(prev.first().plusWeeks(1), next.first())
        }
    }

    @Test
    fun `weekCount of one yields only the endDate week`() {
        val columns = HeatmapGrid.buildWeekColumns(endDate, weekCount = 1)

        assertEquals(1, columns.size)
        assertEquals(LocalDate.of(2026, 7, 20), columns.single().first())
        assertTrue(columns.single().contains(endDate))
    }

    // ── linearByMax ──

    @Test
    fun `linearByMax maps non-positive inputs to level zero`() {
        assertEquals(0, HeatmapLevels.linearByMax(0f, 100f))
        assertEquals(0, HeatmapLevels.linearByMax(-5f, 100f))
        assertEquals(0, HeatmapLevels.linearByMax(10f, 0f))
        assertEquals(0, HeatmapLevels.linearByMax(10f, -3f))
    }

    @Test
    fun `linearByMax maps fractions into four positive levels with clamping`() {
        assertEquals(1, HeatmapLevels.linearByMax(0.01f, 100f)) // 微量即第 1 档
        assertEquals(1, HeatmapLevels.linearByMax(25f, 100f)) // 0.25 → 1
        assertEquals(2, HeatmapLevels.linearByMax(26f, 100f)) // 0.26 → 2
        assertEquals(2, HeatmapLevels.linearByMax(50f, 100f)) // 0.50 → 2
        assertEquals(3, HeatmapLevels.linearByMax(75f, 100f)) // 0.75 → 3
        assertEquals(4, HeatmapLevels.linearByMax(100f, 100f)) // 1.00 → 4
        assertEquals(4, HeatmapLevels.linearByMax(150f, 100f)) // 超 max 钳顶档
    }

    @Test
    fun `levelsOf computes max once and maps every entry`() {
        val levels = HeatmapLevels.levelsOf(
            mapOf(
                LocalDate.of(2026, 7, 20) to 50f,
                LocalDate.of(2026, 7, 21) to 100f,
                LocalDate.of(2026, 7, 22) to 0f,
            ),
            HeatmapLevels::linearByMax,
        )

        assertEquals(2, levels.getValue(LocalDate.of(2026, 7, 20)))
        assertEquals(4, levels.getValue(LocalDate.of(2026, 7, 21)))
        assertEquals(0, levels.getValue(LocalDate.of(2026, 7, 22)))
        assertTrue(HeatmapLevels.levelsOf(emptyMap(), HeatmapLevels::linearByMax).isEmpty())
    }

    // ── buildMonthLabels ──

    @Test
    fun `month labels anchor column zero and cover every month on a year grid`() {
        val columns = HeatmapGrid.buildWeekColumns(endDate, weekCount = 53)
        val labels = HeatmapGrid.buildMonthLabels(columns)

        // 2025-07-21 .. 2026-07-26 横跨 13 个月；月份间隔 ≥4 列，默认间距不丢弃
        assertEquals(13, labels.size)
        assertEquals(0, labels.first().columnIndex)
        labels.zipWithNext().forEach { (prev, next) ->
            // 月份递增，允许 12 月 → 1 月的跨年回绕
            assertTrue(
                next.month > prev.month ||
                    (prev.month == Month.DECEMBER && next.month == Month.JANUARY),
            )
        }
        labels.forEach { label ->
            assertTrue(label.columnIndex in columns.indices)
            assertEquals(columns[label.columnIndex].first().month, label.month)
        }
    }

    @Test
    fun `month labels respect the minimum column gap`() {
        val columns = HeatmapGrid.buildWeekColumns(endDate, weekCount = 53)
        val labels = HeatmapGrid.buildMonthLabels(columns, minGapColumns = 10)

        assertTrue(labels.size < 13)
        labels.zipWithNext().forEach { (prev, next) ->
            assertTrue(next.columnIndex - prev.columnIndex >= 10)
        }
    }

    // ── cellAt ──

    private val columns = HeatmapGrid.buildWeekColumns(endDate, weekCount = 4)
    private val cellPx = 12f
    private val pitchPx = 15f

    @Test
    fun `cellAt hits cell centers`() {
        assertEquals(columns[0][0], HeatmapGrid.cellAt(columns, 6f, 6f, cellPx, pitchPx))
        assertEquals(columns[1][2], HeatmapGrid.cellAt(columns, 15f + 6f, 2 * 15f + 6f, cellPx, pitchPx))
        assertEquals(columns[3][6], HeatmapGrid.cellAt(columns, 3 * 15f + 11.9f, 6 * 15f + 11.9f, cellPx, pitchPx))
    }

    @Test
    fun `cellAt misses the spacing band and outside of the grid`() {
        // 间距带（pitch 内 cell 之外）
        assertNull(HeatmapGrid.cellAt(columns, 13f, 6f, cellPx, pitchPx))
        assertNull(HeatmapGrid.cellAt(columns, 6f, 13f, cellPx, pitchPx))
        // 月份标签行（row ≥ 7）
        assertNull(HeatmapGrid.cellAt(columns, 6f, 7 * 15f + 3f, cellPx, pitchPx))
        // 网格外（4 列宽 60px）与负坐标
        assertNull(HeatmapGrid.cellAt(columns, 61f, 6f, cellPx, pitchPx))
        assertNull(HeatmapGrid.cellAt(columns, -1f, 6f, cellPx, pitchPx))
        assertNull(HeatmapGrid.cellAt(columns, 6f, -1f, cellPx, pitchPx))
    }
}
