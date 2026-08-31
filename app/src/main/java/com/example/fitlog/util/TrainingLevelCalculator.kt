package com.example.fitlog.util

import com.example.fitlog.model.SetLog
import com.example.fitlog.model.SetType
import com.example.fitlog.model.Workout
import com.example.fitlog.model.user.ExerciseTrainingLevel
import com.example.fitlog.model.user.TrainingLevel

/**
 * 训练水平计算器（方案 B：按需计算，不落库）。
 *
 * 从训练历史（[Workout] → ExerciseLog → SetLog）推导各动作的训练水平指标，
 * 真相源唯一（set_logs），任何时刻的计算结果都与最新历史一致，
 * 无需维护重算触发点，也不存在快照脏数据问题。
 *
 * 纯函数设计，无 Android 依赖，便于单元测试；
 * 调用方（如 AI 教练 ViewModel）可在内存中缓存结果（stateIn），避免重复扫描。
 *
 * 口径约定：与全局一致，**只统计 [SetType.WORKING] 正式组**，
 * 热身组 [SetType.WARMUP] 不计入 Epley 1RM 与容量，
 * 避免热身组被误报为新 PR。
 */
object TrainingLevelCalculator {

    /**
     * 计算各动作的训练水平。
     *
     * @param workouts 完整训练历史（含动作与组），时间范围由调用方决定（全部或近 N 周）
     * @param bodyWeightKg 当前体重（kg），为 null 时 [ExerciseTrainingLevel.relativeStrength] 为 null
     * @return 按动作组织的训练水平；key 为 exerciseKey，无 key 时降级为动作名称
     */
    fun calculate(workouts: List<Workout>, bodyWeightKg: Float?): TrainingLevel {
        // 每个动作的 Epley 估算 1RM 历史最佳
        val bestOneRM = mutableMapOf<String, Double>()
        // 每个动作在每个训练日的容量（Σ weight × reps）
        val volumeByExerciseAndDate = mutableMapOf<String, MutableMap<java.time.LocalDate, Double>>()

        for (workout in workouts) {
            for (exercise in workout.exercises) {
                val key = exercise.exerciseKey ?: exercise.name
                // 口径：只统计正式组，热身组不计入 1RM/容量
                val workingSets = exercise.sets.filter { it.setType == SetType.WORKING }
                for (set in workingSets) {
                    // Epley 公式：1RM ≈ weight × (1 + reps / 30)
                    val epleyOneRM = set.weightKg * (1 + set.reps / 30.0)
                    bestOneRM.merge(key, epleyOneRM, ::maxOf)

                    volumeByExerciseAndDate
                        .getOrPut(key) { mutableMapOf() }
                        .merge(workout.date, set.weightKg * set.reps.toDouble(), Double::plus)
                }
            }
        }

        val exercises = bestOneRM.mapValues { (key, oneRM) ->
            ExerciseTrainingLevel(
                estimatedOneRMKg = oneRM,
                relativeStrength = bodyWeightKg
                    ?.takeIf { it > 0f }
                    ?.let { oneRM / it },
                bestVolumeLoadKg = volumeByExerciseAndDate[key]?.values?.maxOrNull(),
            )
        }
        return TrainingLevel(exercises)
    }

    /**
     * 按 Epley 公式返回估算 1RM 最大的那组（"最佳组"展示出口）。
     *
     * 与 [calculate] 的 1RM 口径同源（同一公式，杜绝两处实现漂移）；
     * reps ≤ 0 的组没有 1RM 语义（Epley 退化为重量本身），过滤不计。
     *
     * @param sets 候选组（通常已按正式组过滤；热身组由调用方决定是否传入）
     * @return 估算 1RM 最大的组；无有效组（reps 全为 0 或空列表）时返回 null
     */
    fun bestOneRMSet(sets: List<SetLog>): SetLog? =
        sets.filter { it.reps > 0 }.maxByOrNull { it.weightKg * (1 + it.reps / 30.0) }
}
