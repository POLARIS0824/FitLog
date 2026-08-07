package com.example.fitlog.util

import com.example.fitlog.model.ExerciseLog
import com.example.fitlog.model.SetLog
import com.example.fitlog.model.SetType
import com.example.fitlog.model.Workout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * [TrainingLevelCalculator] 的单元测试。
 *
 * 验证从训练历史按需推导训练水平的纯函数逻辑：
 * Epley 估算 1RM、相对力量、单次最佳容量，以及 key 降级与边界情况。
 */
class TrainingLevelCalculatorTest {

    private fun workout(
        date: LocalDate,
        vararg exercises: ExerciseLog,
    ) = Workout(
        id = 0,
        userId = 0,
        date = date,
        exercises = exercises.toList(),
        feelings = null,
    )

    private fun exercise(
        name: String,
        key: String? = null,
        vararg sets: SetLog,
    ) = ExerciseLog(name = name, exerciseKey = key, sets = sets.toList())

    private fun set(weightKg: Float, reps: Int) = SetLog(weightKg = weightKg, reps = reps)

    private fun warmup(weightKg: Float, reps: Int) =
        SetLog(weightKg = weightKg, reps = reps, setType = SetType.WARMUP)

    /**
     * 空历史返回空 Map。
     */
    @Test
    fun `empty workouts returns empty map`() {
        val result = TrainingLevelCalculator.calculate(emptyList(), bodyWeightKg = 70f)
        assertTrue(result.exercises.isEmpty())
    }

    /**
     * 单次训练单动作：1RM 取 Epley 估算的最大值，容量为当日总和。
     */
    @Test
    fun `single workout computes epley max and volume`() {
        val workouts = listOf(
            workout(
                LocalDate.of(2026, 5, 20),
                exercise(
                    "杠铃卧推", "barbell-bench-press",
                    set(80f, 10),   // Epley: 80 × (1 + 10/30) ≈ 106.67
                    set(85f, 8),    // Epley: 85 × (1 + 8/30) ≈ 107.67  ← 更大
                ),
            ),
        )

        val result = TrainingLevelCalculator.calculate(workouts, bodyWeightKg = 70f)

        val level = result.exercises.getValue("barbell-bench-press")
        assertEquals(85.0 * (1 + 8 / 30.0), level.estimatedOneRMKg!!, 0.001)
        // 容量 = 80×10 + 85×8 = 800 + 680 = 1480
        assertEquals(1480.0, level.bestVolumeLoadKg!!, 0.001)
        // 相对力量 = 1RM / 70
        assertEquals(level.estimatedOneRMKg!! / 70.0, level.relativeStrength!!, 0.001)
    }

    /**
     * 多次训练：最佳容量取各训练日的最大值（而非总和）。
     */
    @Test
    fun `best volume is max across workout days`() {
        val workouts = listOf(
            workout(
                LocalDate.of(2026, 5, 18),
                exercise("深蹲", "barbell-squat", set(100f, 5), set(100f, 5)), // 当日 1000
            ),
            workout(
                LocalDate.of(2026, 5, 20),
                exercise("深蹲", "barbell-squat", set(110f, 5)),               // 当日 550
            ),
        )

        val result = TrainingLevelCalculator.calculate(workouts, bodyWeightKg = 80f)

        val level = result.exercises.getValue("barbell-squat")
        assertEquals(1000.0, level.bestVolumeLoadKg!!, 0.001)
        // 1RM 取两天中最大的 Epley：110 × (1 + 5/30) ≈ 128.33
        assertEquals(110.0 * (1 + 5 / 30.0), level.estimatedOneRMKg!!, 0.001)
    }

    /**
     * 体重为 null 时 relativeStrength 为 null，其余指标正常。
     */
    @Test
    fun `null bodyweight yields null relative strength`() {
        val workouts = listOf(
            workout(LocalDate.of(2026, 5, 20), exercise("卧推", "bench", set(60f, 10))),
        )

        val result = TrainingLevelCalculator.calculate(workouts, bodyWeightKg = null)

        val level = result.exercises.getValue("bench")
        assertNull(level.relativeStrength)
        assertTrue(level.estimatedOneRMKg!! > 0)
        assertTrue(level.bestVolumeLoadKg!! > 0)
    }

    /**
     * exerciseKey 为 null 时降级使用动作名称作为 Map key。
     */
    @Test
    fun `null exerciseKey falls back to exercise name`() {
        val workouts = listOf(
            workout(LocalDate.of(2026, 5, 20), exercise("自定义动作", null, set(20f, 12))),
        )

        val result = TrainingLevelCalculator.calculate(workouts, bodyWeightKg = null)

        assertTrue(result.exercises.containsKey("自定义动作"))
    }

    /**
     * 同一动作跨多个训练日聚合到同一个 key 下。
     */
    @Test
    fun `same exercise across workouts aggregates under one key`() {
        val workouts = listOf(
            workout(LocalDate.of(2026, 5, 18), exercise("卧推", "bench", set(70f, 10))),
            workout(LocalDate.of(2026, 5, 20), exercise("卧推", "bench", set(75f, 8))),
        )

        val result = TrainingLevelCalculator.calculate(workouts, bodyWeightKg = null)

        assertEquals(1, result.exercises.size)
        // 1RM 取全局最佳：75 × (1 + 8/30) = 95 > 70 × (1 + 10/30) ≈ 93.33
        assertEquals(75.0 * (1 + 8 / 30.0), result.exercises.getValue("bench").estimatedOneRMKg!!, 0.001)
    }

    /**
     * 口径：热身组不计入 Epley 1RM 与容量，只有正式组参与统计。
     */
    @Test
    fun `warmup sets are excluded from 1RM and volume`() {
        val workouts = listOf(
            workout(
                LocalDate.of(2026, 5, 20),
                exercise(
                    "杠铃卧推", "barbell-bench-press",
                    // 热身组重量/次数更高，但不得污染 1RM 与容量
                    warmup(140f, 30),
                    set(80f, 10),   // Epley: 80 × (1 + 10/30) ≈ 106.67
                    set(85f, 8),    // Epley: 85 × (1 + 8/30) ≈ 107.67  ← 正式组最大
                ),
            ),
        )

        val result = TrainingLevelCalculator.calculate(workouts, bodyWeightKg = null)

        val level = result.exercises.getValue("barbell-bench-press")
        // 1RM 只取正式组：热身组 140×30 被排除
        assertEquals(85.0 * (1 + 8 / 30.0), level.estimatedOneRMKg!!, 0.001)
        // 容量只累加正式组：80×10 + 85×8 = 1480（热身组 140×30 不计）
        assertEquals(1480.0, level.bestVolumeLoadKg!!, 0.001)
    }

    /**
     * 边界：只有热身组、没有任何正式组时，该动作不产生训练水平条目。
     */
    @Test
    fun `only warmup sets yields no level entry`() {
        val workouts = listOf(
            workout(
                LocalDate.of(2026, 5, 20),
                exercise("杠铃卧推", "barbell-bench-press", warmup(40f, 12)),
            ),
        )

        val result = TrainingLevelCalculator.calculate(workouts, bodyWeightKg = null)

        assertTrue(result.exercises.isEmpty())
    }
}
