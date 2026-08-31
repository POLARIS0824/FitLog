package com.example.fitlog.util

import com.example.fitlog.model.SetType
import com.example.fitlog.model.Workout
import java.time.LocalDate

/**
 * 训练容量聚合的统一口径（Today 周进度与 Stats 系 builder 共用）。
 *
 * 容量口径：只累加 [SetType.WORKING] 正式组的 重量kg × 次数（热身组不计）——
 * 此前该逻辑在 WeekProgressCalculator / StatsChartDataBuilder / StatsHeatmapBuilder
 * 各有一份实现，收口至此消除口径漂移面。
 */
object VolumeAggregator {

    /**
     * 训练集合的总正式组容量（kg）。
     *
     * @param workouts 训练日志
     * @return Σ 正式组 weight×reps
     */
    fun workingVolume(workouts: List<Workout>): Double = workouts.sumOf(::workoutVolume)

    /**
     * 单次训练的正式组容量（kg）。
     * Agent 工具（getRecentWorkouts/getWeeklySummary）与 Stats 概览的
     * 单 workout 聚合统一走此出口，消除各自手写的口径漂移面。
     */
    fun workingVolumeOf(workout: Workout): Double = workoutVolume(workout)

    /**
     * 单次训练的正式组数（热身组不计）。
     * 与 [workingVolumeOf] 同口径配对使用。
     */
    fun workingSetCountOf(workout: Workout): Int =
        workout.exercises.sumOf { log ->
            log.sets.count { it.setType == SetType.WORKING }
        }

    /**
     * 按日期聚合的正式组容量：同日多次训练合并，0 容量日不进 map
     * （调用方按"缺席 = 0"语义处理，如热力图空档）。
     *
     * @param workouts 训练日志（窗口裁剪由调用方对结果按日期过滤完成）
     * @return date → 当日总容量（kg）
     */
    fun volumeByDate(workouts: List<Workout>): Map<LocalDate, Double> {
        val volumeByDate = mutableMapOf<LocalDate, Double>()
        workouts.forEach { workout ->
            val volume = workoutVolume(workout)
            if (volume > 0.0) {
                volumeByDate.merge(workout.date, volume, Double::plus)
            }
        }
        return volumeByDate
    }

    /** 单次训练的正式组容量（kg）。 */
    private fun workoutVolume(workout: Workout): Double =
        workout.exercises.sumOf { log ->
            log.sets
                .filter { it.setType == SetType.WORKING }
                .sumOf { (it.weightKg * it.reps).toDouble() }
        }
}
