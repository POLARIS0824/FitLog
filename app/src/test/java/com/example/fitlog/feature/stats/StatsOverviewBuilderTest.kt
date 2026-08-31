package com.example.fitlog.feature.stats

import com.example.fitlog.model.ExerciseLog
import com.example.fitlog.model.SetLog
import com.example.fitlog.model.SetType
import com.example.fitlog.model.Workout
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * [StatsOverviewBuilder] 的单元测试（纯 JVM）。
 *
 * 验证四项指标的口径（WORKING 正式组）、窗口防御过滤、
 * 平均单次的除法语义与空数据零值降级。
 */
class StatsOverviewBuilderTest {

    // 2026-07-25 为周六
    private val today = LocalDate.of(2026, 7, 25)

    @Test
    fun `counts sessions and working sets within window`() {
        // 同一天两次训练：次数=2；组数=1+2=3（热身组不计）
        val state = StatsOverviewBuilder.build(
            workouts = listOf(
                workout(today, sets = listOf(workingSet(100f, 10))),
                workout(
                    today,
                    sets = listOf(
                        SetLog(weightKg = 50f, reps = 12, setType = SetType.WARMUP),
                        workingSet(100f, 10),
                        workingSet(100f, 10),
                    ),
                ),
            ),
            period = StatsPeriod.WEEK,
            today = today,
        )

        assertEquals("2 次", state.items[0].valueText)
        assertEquals("3 组", state.items[3].valueText)
    }

    @Test
    fun `total and average volume only count working sets`() {
        // 两次训练：各 1 组 100kg×10 = 1000kg；热身组不进容量
        val state = StatsOverviewBuilder.build(
            workouts = listOf(
                workout(
                    today,
                    sets = listOf(
                        SetLog(weightKg = 50f, reps = 12, setType = SetType.WARMUP),
                        workingSet(100f, 10),
                    ),
                ),
                workout(today.minusDays(1), sets = listOf(workingSet(100f, 10))),
            ),
            period = StatsPeriod.WEEK,
            today = today,
        )

        assertEquals("2.0 吨", state.items[1].valueText)
        // 平均 = 总容量 / 次数 = 2000 / 2 = 1000kg = 1.0 吨
        assertEquals("1.0 吨", state.items[2].valueText)
    }

    @Test
    fun `workouts outside period window are excluded`() {
        val state = StatsOverviewBuilder.build(
            workouts = listOf(
                workout(today, sets = listOf(workingSet(100f, 10))),
                workout(today.minusDays(20), sets = listOf(workingSet(100f, 10))),
            ),
            period = StatsPeriod.WEEK,
            today = today,
        )

        assertEquals("1 次", state.items[0].valueText)
        assertEquals("1 组", state.items[3].valueText)
        assertEquals("1.0 吨", state.items[1].valueText)
    }

    @Test
    fun `empty workouts degrade to zero texts`() {
        val state = StatsOverviewBuilder.build(
            workouts = emptyList(),
            period = StatsPeriod.MONTH,
            today = today,
        )

        assertEquals(4, state.items.size)
        assertEquals("0 次", state.items[0].valueText)
        assertEquals("0 kg", state.items[1].valueText)
        assertEquals("0 kg", state.items[2].valueText) // 无训练时平均为 0，不除零
        assertEquals("0 组", state.items[3].valueText)
    }

    @Test
    fun `item order is fixed`() {
        val state = StatsOverviewBuilder.build(emptyList(), StatsPeriod.WEEK, today)

        assertEquals(listOf("训练次数", "总容量", "平均单次", "正式组数"), state.items.map { it.title })
    }

    // ── 辅助方法 ──

    private fun workingSet(weightKg: Float, reps: Int) =
        SetLog(weightKg = weightKg, reps = reps, setType = SetType.WORKING)

    private fun workout(date: LocalDate, sets: List<SetLog>): Workout = Workout(
        id = 0L,
        userId = 0L,
        date = date,
        exercises = listOf(ExerciseLog(name = "测试动作", sets = sets)),
        feelings = null,
        // 夹具为"已结束"训练：isCountable 口径要求 endedAt 非空
        startedAt = 0L,
        endedAt = 3_600_000L,
    )
}
