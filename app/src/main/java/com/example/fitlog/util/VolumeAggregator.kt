package com.example.fitlog.util

import com.example.fitlog.model.ExerciseLog
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
        workout.exercises.sumOf(::workingSetCountOf)

    /**
     * 单个动作记录的正式组容量（kg）。
     *
     * AI 提示词（Prompt.summarizeWorkout）、会话实时统计（WorkoutSessionModels）、
     * Today 周进度（WeekProgressCalculator）等"按动作粒度累计"的调用方统一走此出口。
     */
    fun workingVolumeOf(log: ExerciseLog): Double =
        log.sets
            .filter { it.isCompleted && it.setType == SetType.WORKING && it.reps > 0 }
            .sumOf { (it.weightKg * it.reps).toDouble() }

    /**
     * 单个动作记录的正式组数。
     *
     * 口径：热身组不计；reps ≤ 0 的占位/失败组同样不计——占位组是会话中
     * 尚未录入的空行，计入会让"组数"随录入过程虚增（与 [workoutVolume] 中
     * 0 次组天然零容量不同，组数必须显式过滤）。
     */
    fun workingSetCountOf(log: ExerciseLog): Int =
        log.sets.count { it.isCompleted && it.setType == SetType.WORKING && it.reps > 0 }

    /**
     * 按日期聚合的正式组容量：同日多次训练合并，0 容量日不进 map
     * （调用方按"缺席 = 0"语义处理，如热力图空档）。
     *
     * 只统计 [Workout.isCountable] 的训练：进行中会话的容量还在增长，
     * 计入会让 Stats 图表/热力图随录入过程实时虚高，与概览卡（调用方
     * 自行过滤 isCountable）口径不一致。导入的表头存档记录容量为 0，
     * 过滤与否不影响结果，统一收口保证口径单一。
     *
     * 口径边界（刻意保留的差异）：Today 周进度的容量（WeekProgressCalculator）
     * 不过滤进行中会话——训练执行流与 Today 是实时激励反馈，会话进行中
     * 看到容量即时增长是预期行为；历史分析页（Stats/AI 周报）一律排除。
     *
     * @param workouts 训练日志（窗口裁剪由调用方对结果按日期过滤完成）
     * @return date → 当日总容量（kg）
     */
    fun volumeByDate(workouts: List<Workout>): Map<LocalDate, Double> {
        val volumeByDate = mutableMapOf<LocalDate, Double>()
        workouts.filter { it.isCountable }.forEach { workout ->
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
                .filter { it.isCompleted && it.setType == SetType.WORKING && it.reps > 0 }
                .sumOf { (it.weightKg * it.reps).toDouble() }
        }
}
