package com.example.fitlog.feature.stats

import com.example.fitlog.model.BodyMetric
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * [StatsWeightBuilder] 的单元测试（纯 JVM）。
 *
 * 验证折线数据的升序/id/标签、y 区间留白（span×15% 下限 0.8kg）、
 * 最近 30 条截断、x 标签等长抽稀、环比文案与空态 Dotted 占位。
 */
class StatsWeightBuilderTest {

    private val today = LocalDate.of(2026, 7, 25)

    @Test
    fun `entries are ascending with ISO ids and M-d labels`() {
        val state = StatsWeightBuilder.build(
            metrics = listOf(
                BodyMetric(date = today.minusDays(4), weightKg = 74.8f),
                BodyMetric(date = today, weightKg = 74.5f),
            ),
            today = today,
        )

        assertTrue(state.hasData)
        val entries = state.chartData.entries
        assertEquals(2, entries.size)
        assertEquals(today.minusDays(4).toString(), entries[0].id)
        assertEquals(today.toString(), entries[1].id)
        assertEquals("${today.monthValue}/${today.dayOfMonth}", entries[1].label)
        assertEquals(74.8f, entries[0].value, 0.01f)
        assertEquals(74.5f, entries[1].value, 0.01f)
    }

    @Test
    fun `y range pads span by 15 percent when above floor`() {
        // span = 6.0 → pad = max(0.9, 0.8) = 0.9 → [73.1, 80.9]
        val state = StatsWeightBuilder.build(
            metrics = listOf(
                BodyMetric(date = today.minusDays(2), weightKg = 74f),
                BodyMetric(date = today, weightKg = 80f),
            ),
            today = today,
        )

        assertEquals(73.1f, state.chartData.yMin, 0.001f)
        assertEquals(80.9f, state.chartData.yMax, 0.001f)
    }

    @Test
    fun `y range pad falls back to 0-8 floor for small span`() {
        // span = 2.0 → pad = max(0.3, 0.8) = 0.8 → [73.2, 76.8]
        val state = StatsWeightBuilder.build(
            metrics = listOf(
                BodyMetric(date = today.minusDays(2), weightKg = 74f),
                BodyMetric(date = today, weightKg = 76f),
            ),
            today = today,
        )

        assertEquals(73.2f, state.chartData.yMin, 0.001f)
        assertEquals(76.8f, state.chartData.yMax, 0.001f)
    }

    @Test
    fun `single record pads by 0-8 floor`() {
        val state = StatsWeightBuilder.build(
            metrics = listOf(BodyMetric(date = today, weightKg = 74f)),
            today = today,
        )

        assertEquals(73.2f, state.chartData.yMin, 0.001f)
        assertEquals(74.8f, state.chartData.yMax, 0.001f)
        assertNull(state.deltaText)
    }

    @Test
    fun `keeps only latest 30 records`() {
        val metrics = (0L..39L).map { i ->
            BodyMetric(date = today.minusDays(39 - i), weightKg = 70f + i)
        }

        val state = StatsWeightBuilder.build(metrics, today)

        assertEquals(30, state.chartData.entries.size)
        // 40 条取最近 30：截掉最旧 10 条，首条是 minusDays(29)
        assertEquals(today.minusDays(29).toString(), state.chartData.entries.first().id)
        assertEquals(today.toString(), state.chartData.entries.last().id)
    }

    @Test
    fun `xLabels are entry-length sparse with last always labeled`() {
        val metrics = (0L..29L).map { i ->
            BodyMetric(date = today.minusDays(29 - i), weightKg = 70f + i * 0.1f)
        }

        val state = StatsWeightBuilder.build(metrics, today)

        assertEquals(state.chartData.entries.size, state.xLabels.size)
        val visible = state.xLabels.count { it.isNotEmpty() }
        assertTrue(visible <= 6)
        assertTrue(state.xLabels.first().isNotEmpty())
        assertTrue(state.xLabels.last().isNotEmpty())
    }

    @Test
    fun `delta text compares latest two records with sign`() {
        val state = StatsWeightBuilder.build(
            metrics = listOf(
                BodyMetric(date = today.minusDays(3), weightKg = 74.2f),
                BodyMetric(date = today, weightKg = 74.5f),
            ),
            today = today,
        )

        assertEquals("较上次 +0.3 kg", state.deltaText)
    }

    @Test
    fun `delta text shows negative sign`() {
        val state = StatsWeightBuilder.build(
            metrics = listOf(
                BodyMetric(date = today.minusDays(3), weightKg = 75.0f),
                BodyMetric(date = today, weightKg = 74.5f),
            ),
            today = today,
        )

        assertEquals("较上次 -0.5 kg", state.deltaText)
    }

    @Test
    fun `value text shows latest weight`() {
        val state = StatsWeightBuilder.build(
            metrics = listOf(BodyMetric(date = today, weightKg = 74.5f)),
            today = today,
        )

        assertEquals("74.5 kg", state.valueText)
    }

    @Test
    fun `metrics outside 90 day window are excluded`() {
        val state = StatsWeightBuilder.build(
            metrics = listOf(
                BodyMetric(date = today.minusDays(100), weightKg = 80f),
                BodyMetric(date = today, weightKg = 74.5f),
            ),
            today = today,
        )

        assertEquals(1, state.chartData.entries.size)
        assertEquals(today.toString(), state.chartData.entries.single().id)
    }

    @Test
    fun `empty metrics degrade to dotted placeholder`() {
        val state = StatsWeightBuilder.build(emptyList(), today)

        assertFalse(state.hasData)
        assertEquals("暂无记录", state.valueText)
        assertNull(state.deltaText)
        assertEquals(7, state.chartData.entries.size)
        assertTrue(state.chartData.entries.all { it.value == 0f })
        assertEquals(0f, state.chartData.yMin)
        assertEquals(0f, state.chartData.yMax)
        assertTrue(state.xLabels.isEmpty())
    }
}
