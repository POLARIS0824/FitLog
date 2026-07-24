package com.example.fitlog.util

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
                for (set in exercise.sets) {
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
}
