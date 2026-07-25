package com.example.fitlog.feature.today

import com.example.fitlog.model.BodyPart
import com.example.fitlog.model.Exercise
import com.example.fitlog.model.Muscle
import com.example.fitlog.model.SetType
import com.example.fitlog.model.Workout
import com.example.fitlog.model.WorkoutPlan
import com.example.fitlog.util.TrainingLevelCalculator
import java.time.LocalDate

/**
 * 本周训练进度的渲染项计算器。
 *
 * 纯函数对象：按 [WeekProgressDisplayMode] 把本周训练数据聚合为统一的
 * [ProgressItemState] 列表（**契约：最多 4 个**，对应 MetricDashboardGrid 的 1 大 + 3 小槽位），
 * UI 只需循环渲染，无需感知模式差异。
 *
 * 口径约定：组数/容量只累加 [SetType.WORKING] 正式组（热身组不计）；
 * 动作元数据经 `exerciseKey → 动作目录` 查询，查不到时用动作名兜底，仍失败则跳过该动作。
 */
object WeekProgressCalculator {

    private const val MAX_ITEMS = 4

    /**
     * 按模式计算渲染项列表（已截断为最多 4 个）。
     *
     * @param mode 展示模式
     * @param weekWorkouts 本周（weekStart 起）的训练记录
     * @param allWorkouts 全部历史训练记录（VOLUME_PR 的历史对比用）
     * @param activePlan 当前激活计划（无则为 null）
     * @param catalog 动作目录（exerciseKey/name → 肌群与部位元数据）
     * @param weekStart 本周起始日期（周一）
     */
    fun calculate(
        mode: WeekProgressDisplayMode,
        weekWorkouts: List<Workout>,
        allWorkouts: List<Workout>,
        activePlan: WorkoutPlan?,
        catalog: List<Exercise>,
        weekStart: LocalDate,
    ): List<ProgressItemState> = when (mode) {
        WeekProgressDisplayMode.SPLIT -> splitItems(activePlan, weekWorkouts)
        WeekProgressDisplayMode.MUSCLE_SETS -> muscleSetItems(weekWorkouts, catalog)
        WeekProgressDisplayMode.VOLUME_PR -> volumePrItems(weekWorkouts, allWorkouts, weekStart)
        WeekProgressDisplayMode.CATEGORY -> categoryItems(weekWorkouts, catalog)
    }

    // ──────────────────────────────────────
    // SPLIT：力量分化日
    // ──────────────────────────────────────

    /**
     * 分化日模式：item[0] 固定为本周完成度，后续为激活计划的各分化课完成状态。
     */
    private fun splitItems(
        activePlan: WorkoutPlan?,
        weekWorkouts: List<Workout>,
    ): List<ProgressItemState> {
        if (activePlan == null) {
            return listOf(
                ProgressItemState(id = "no-plan", title = "未激活计划", subtitle = "去选择一套计划"),
            )
        }
        val weekWorkoutIds = weekWorkouts.map { it.id }.toSet()
        val head = ProgressItemState(
            id = "week-total",
            title = "本周训练",
            subtitle = "${weekWorkouts.size}/${activePlan.sessionsPerWeek} 次",
        )
        val splits = activePlan.sessions
            .distinctBy { it.name }
            .take(MAX_ITEMS - 1)
            .map { session ->
                val doneThisWeek =
                    session.completedWorkoutId != null && session.completedWorkoutId in weekWorkoutIds
                ProgressItemState(
                    id = session.id,
                    title = session.name,
                    subtitle = if (doneThisWeek) "本周已练" else "待训练",
                )
            }
        return listOf(head) + splits
    }

    // ──────────────────────────────────────
    // MUSCLE_SETS：肌肉组数量
    // ──────────────────────────────────────

    /**
     * 肌肉组数模式：本周正式组按主要肌群展开计数，降序取 top 4。
     */
    private fun muscleSetItems(
        weekWorkouts: List<Workout>,
        catalog: List<Exercise>,
    ): List<ProgressItemState> {
        val lookup = ExerciseLookup(catalog)
        val counts = mutableMapOf<Muscle, Int>()
        weekWorkouts.forEach { workout ->
            workout.exercises.forEach { log ->
                val exercise = lookup.find(log.exerciseKey, log.name) ?: return@forEach
                val workingSets = log.sets.count { it.setType == SetType.WORKING }
                exercise.primaryMuscles.forEach { muscle ->
                    counts[muscle] = (counts[muscle] ?: 0) + workingSets
                }
            }
        }
        if (counts.isEmpty()) {
            return listOf(ProgressItemState(id = "empty", title = "暂无数据", subtitle = "本周还没有正式组"))
        }
        return counts.entries
            .sortedByDescending { it.value }
            .take(MAX_ITEMS)
            .map { (muscle, count) ->
                ProgressItemState(id = muscle.name, title = muscle.displayName(), subtitle = "$count 组")
            }
    }

    // ──────────────────────────────────────
    // VOLUME_PR：容量与突破
    // ──────────────────────────────────────

    /**
     * 容量突破模式：item[0] 固定为周总容量（仅正式组）；
     * 后续为本周估算 1RM 超过历史最佳的动作（新 PR），最多 3 个。
     */
    private fun volumePrItems(
        weekWorkouts: List<Workout>,
        allWorkouts: List<Workout>,
        weekStart: LocalDate,
    ): List<ProgressItemState> {
        if (weekWorkouts.isEmpty()) {
            return listOf(ProgressItemState(id = "empty", title = "暂无数据", subtitle = "本周还没有训练"))
        }
        val weekVolume = weekWorkouts.sumOf { workout ->
            workout.exercises.sumOf { log ->
                log.sets
                    .filter { it.setType == SetType.WORKING }
                    .sumOf { (it.weightKg * it.reps).toDouble() }
            }
        }
        val head = ProgressItemState(
            id = "week-volume",
            title = "周总容量",
            subtitle = formatVolume(weekVolume),
        )

        // 本周各动作最佳 e1RM vs 本周之前历史最佳（复用 TrainingLevelCalculator 的 Epley 口径）
        val weekBest = TrainingLevelCalculator.calculate(weekWorkouts, null).exercises
        val historyBest = TrainingLevelCalculator.calculate(
            allWorkouts.filter { it.date < weekStart },
            null,
        ).exercises
        // exerciseKey（或动作名兜底）→ 展示名
        val displayNames = weekWorkouts
            .flatMap { it.exercises }
            .associate { (it.exerciseKey ?: it.name) to it.name }

        val prs = weekBest.entries
            .mapNotNull { (key, weekLevel) ->
                val weekRm = weekLevel.estimatedOneRMKg ?: return@mapNotNull null
                val historyRm = historyBest[key]?.estimatedOneRMKg ?: return@mapNotNull null
                if (weekRm > historyRm) key to weekRm else null
            }
            .sortedByDescending { it.second }
            .take(MAX_ITEMS - 1)
            .map { (key, rm) ->
                ProgressItemState(
                    id = "pr-$key",
                    title = displayNames[key] ?: key,
                    subtitle = "新 PR · ${formatWeight(rm)}",
                )
            }

        return if (prs.isEmpty()) {
            listOf(head) + ProgressItemState(id = "no-pr", title = "本周暂无突破", subtitle = "继续保持")
        } else {
            listOf(head) + prs
        }
    }

    // ──────────────────────────────────────
    // CATEGORY：训练类别
    // ──────────────────────────────────────

    /**
     * 训练类别模式：本周每个 workout 出现过的身体部位集合按部位计训练次数
     * （同一 workout 内同部位只计 1 次），降序取 top 4。
     */
    private fun categoryItems(
        weekWorkouts: List<Workout>,
        catalog: List<Exercise>,
    ): List<ProgressItemState> {
        val lookup = ExerciseLookup(catalog)
        val counts = mutableMapOf<BodyPart, Int>()
        weekWorkouts.forEach { workout ->
            val parts = workout.exercises
                .mapNotNull { log -> lookup.find(log.exerciseKey, log.name)?.bodyPart }
                .toSet()
            parts.forEach { part -> counts[part] = (counts[part] ?: 0) + 1 }
        }
        if (counts.isEmpty()) {
            return listOf(ProgressItemState(id = "empty", title = "暂无数据", subtitle = "本周还没有训练"))
        }
        return counts.entries
            .sortedByDescending { it.value }
            .take(MAX_ITEMS)
            .map { (part, count) ->
                ProgressItemState(id = part.name, title = part.displayName(), subtitle = "$count sessions")
            }
    }

    // ──────────────────────────────────────
    // 内部工具
    // ──────────────────────────────────────

    /**
     * 动作目录查询：优先 exerciseKey，查不到时用动作名兜底。
     */
    private class ExerciseLookup(catalog: List<Exercise>) {
        private val byKey = catalog.associateBy { it.id }
        private val byName = catalog.associateBy { it.name }

        fun find(exerciseKey: String?, name: String): Exercise? =
            exerciseKey?.let { byKey[it] } ?: byName[name]
    }

    /** 容量格式化：≥1000kg 显示吨（保留一位小数），否则显示 kg。 */
    private fun formatVolume(volumeKg: Double): String =
        if (volumeKg >= 1000) {
            "%.1f 吨".format(volumeKg / 1000)
        } else {
            "%.0f kg".format(volumeKg)
        }

    /** 重量格式化：整数不带小数点。 */
    private fun formatWeight(weightKg: Double): String =
        if (weightKg % 1.0 == 0.0) {
            "${weightKg.toInt()} kg"
        } else {
            "%.1f kg".format(weightKg)
        }

    /** [Muscle] → 中文显示名（仅 UI 展示，不改 model 枚举）。 */
    private fun Muscle.displayName(): String = when (this) {
        Muscle.CHEST -> "胸部"
        Muscle.SHOULDERS -> "肩部"
        Muscle.TRICEPS -> "肱三头肌"
        Muscle.LATS -> "背阔肌"
        Muscle.UPPER_BACK -> "上背"
        Muscle.TRAPS -> "斜方肌"
        Muscle.BICEPS -> "肱二头肌"
        Muscle.FOREARMS -> "前臂"
        Muscle.QUADRICEPS -> "股四头肌"
        Muscle.HAMSTRINGS -> "腘绳肌"
        Muscle.GLUTES -> "臀部"
        Muscle.CALVES -> "小腿"
        Muscle.HIP_FLEXORS -> "髋屈肌"
        Muscle.ADDUCTORS -> "内收肌"
        Muscle.ABDUCTORS -> "外展肌"
        Muscle.CORE -> "核心"
        Muscle.LOWER_BACK -> "下背"
        Muscle.NECK -> "颈部"
        Muscle.CARDIO -> "心肺"
    }

    /** [BodyPart] → 中文显示名（仅 UI 展示，不改 model 枚举）。 */
    private fun BodyPart.displayName(): String = when (this) {
        BodyPart.CHEST -> "胸部"
        BodyPart.BACK -> "背部"
        BodyPart.SHOULDERS -> "肩部"
        BodyPart.UPPER_ARMS -> "上臂"
        BodyPart.LOWER_ARMS -> "前臂"
        BodyPart.UPPER_LEGS -> "腿/臀"
        BodyPart.LOWER_LEGS -> "小腿"
        BodyPart.WAIST -> "腰腹"
        BodyPart.NECK -> "颈部"
        BodyPart.CARDIO -> "有氧"
    }
}
