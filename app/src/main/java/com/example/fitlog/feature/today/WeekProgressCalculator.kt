package com.example.fitlog.feature.today

import com.example.fitlog.model.BodyPart
import com.example.fitlog.model.Equipment
import com.example.fitlog.model.Exercise
import com.example.fitlog.model.Muscle
import com.example.fitlog.model.PlannedSession
import com.example.fitlog.model.SetType
import com.example.fitlog.model.Workout
import com.example.fitlog.model.WorkoutPlan
import com.example.fitlog.ui.components.RingSegment
import com.example.fitlog.util.TrainingLevelCalculator
import com.example.fitlog.util.VolumeAggregator
import com.example.fitlog.util.VolumeFormatter
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * 本周训练进度的渲染项计算器。
 *
 * 纯函数对象：按 [WeekProgressDisplayMode] 把本周训练数据聚合为统一的
 * [ProgressItemState] 列表（**契约：固定 4 个**，items[0] 进左侧大卡，
 * items[1..3] 进右侧小卡；占位卡是显式 item，不留空槽），
 * UI 只需循环渲染，无需感知模式差异。
 *
 * 各模式卡片映射：
 * - SPLIT 分化日：本周训练（次数/目标进度）｜下一训练｜最近训练｜补剂摄入（占位）
 * - MUSCLE_SETS 肌肉组数量：肌肉覆盖（部位数/有效组数）｜训练不足｜重点肌群｜肌群平衡（占位）
 * - VOLUME_PR 容量与突破：训练容量（环比上周）｜PR｜最大增长｜AI 分析（占位）
 * - CATEGORY 训练类别：训练分布（环形图）｜力量训练｜有氧训练｜运动目标（占位）
 *
 * 口径约定：组数/容量只累加 [SetType.WORKING] 正式组（热身组不计）；
 * 动作元数据经 `exerciseKey → 动作目录` 查询，查不到时用动作名兜底，仍失败则跳过该动作。
 */
object WeekProgressCalculator {

    /** 肌肉覆盖页统计的 7 大部位（剔除前臂/颈部/有氧）。 */
    private val MAJOR_BODY_PARTS = listOf(
        BodyPart.CHEST, BodyPart.BACK, BodyPart.SHOULDERS, BodyPart.UPPER_ARMS,
        BodyPart.UPPER_LEGS, BodyPart.LOWER_LEGS, BodyPart.WAIST,
    )

    /**
     * 按模式计算渲染项列表（固定 4 个：1 大 + 3 小）。
     *
     * @param mode 展示模式
     * @param weekWorkouts 本周（weekStart 起）的训练记录
     * @param prevWeekWorkouts 上周（weekStart-7 ~ weekStart-1）的训练记录（VOLUME_PR 环比基线）
     * @param allWorkouts 全部历史训练记录（VOLUME_PR 的历史对比用）
     * @param activePlan 当前激活计划（无则为 null）
     * @param nextSession 下一未完成课次（SPLIT「下一训练」，无计划/全完成时为 null）
     * @param latestWorkout 最近一条训练记录（SPLIT「最近训练」，无记录时为 null）
     * @param targetWorkouts 本周目标次数（调用方已含默认值兜底）
     * @param catalog 动作目录（exerciseKey/name → 肌群与部位元数据）
     * @param weekStart 本周起始日期（周一）
     */
    fun calculate(
        mode: WeekProgressDisplayMode,
        weekWorkouts: List<Workout>,
        prevWeekWorkouts: List<Workout>,
        allWorkouts: List<Workout>,
        activePlan: WorkoutPlan?,
        nextSession: PlannedSession?,
        latestWorkout: Workout?,
        targetWorkouts: Int,
        catalog: List<Exercise>,
        weekStart: LocalDate,
    ): List<ProgressItemState> = when (mode) {
        WeekProgressDisplayMode.SPLIT ->
            // 最近训练取"已结束"的最新一条：进行中的会话（endedAt 为空、含占位组）
            // 还没练完，不能作为"最近训练"参与主导部位推导
            splitItems(
                activePlan,
                nextSession,
                latestWorkout?.takeIf { it.endedAt != null },
                weekWorkouts,
                targetWorkouts,
                catalog,
            )
        WeekProgressDisplayMode.MUSCLE_SETS -> muscleSetItems(weekWorkouts, catalog)
        WeekProgressDisplayMode.VOLUME_PR ->
            volumePrItems(weekWorkouts, prevWeekWorkouts, allWorkouts, weekStart, catalog)
        WeekProgressDisplayMode.CATEGORY -> categoryItems(weekWorkouts, catalog)
    }

    // ──────────────────────────────────────
    // SPLIT：分化日
    // ──────────────────────────────────────

    /**
     * 分化日模式：大卡为本周完成度（次数/目标水波进度）；
     * 小卡为下一训练（计划课次名）、最近训练（课次名或部位推导）、补剂摄入（占位）。
     */
    private fun splitItems(
        activePlan: WorkoutPlan?,
        nextSession: PlannedSession?,
        latestWorkout: Workout?,
        weekWorkouts: List<Workout>,
        targetWorkouts: Int,
        catalog: List<Exercise>,
    ): List<ProgressItemState> {
        // 完成数口径统一走 Workout.isCountable（导入的表头存档记录不计入），
        // 与 ViewModel 规则版/AI 上下文的数字保持一致
        val completed = weekWorkouts.count { it.isCountable }
        val head = ProgressItemState(
            id = "week-total",
            title = "本周训练",
            subtitle = "目标 $targetWorkouts 次",
            progress = (completed.toFloat() / targetWorkouts.coerceAtLeast(1)).coerceIn(0f, 1f),
            valueText = "$completed 次",
        )
        val next = ProgressItemState(
            id = "next-session",
            title = "下一训练",
            subtitle = nextSession?.name ?: if (activePlan == null) "无计划" else "全部完成",
        )
        val last = ProgressItemState(
            id = "last-session",
            title = "最近训练",
            subtitle = resolveLastSessionName(activePlan, latestWorkout, catalog),
        )
        val supplement = ProgressItemState(
            id = "supplement",
            title = "补剂摄入",
            subtitle = "即将上线",
        )
        return listOf(head, next, last, supplement)
    }

    /**
     * 「最近训练」名称解析：优先匹配激活计划中完成标记指向该记录的课次名；
     * 否则按正式组数取主导身体部位生成 "XX训练"；均无法解析则 "自由训练"。
     */
    private fun resolveLastSessionName(
        activePlan: WorkoutPlan?,
        latestWorkout: Workout?,
        catalog: List<Exercise>,
    ): String {
        if (latestWorkout == null) return "暂无训练"
        activePlan?.sessions
            ?.firstOrNull { it.completedWorkoutId == latestWorkout.id }
            ?.let { return it.name }
        val lookup = ExerciseLookup(catalog)
        val counts = LinkedHashMap<BodyPart, Int>()
        latestWorkout.exercises.forEach { log ->
            val exercise = lookup.find(log.exerciseKey, log.name) ?: return@forEach
            // 只计已录入的正式组（reps>0）：占位组不参与主导部位推导
            val workingSets = log.sets.count { it.setType == SetType.WORKING && it.reps > 0 }
            if (workingSets > 0) {
                counts[exercise.bodyPart] = (counts[exercise.bodyPart] ?: 0) + workingSets
            }
        }
        return counts.entries.maxByOrNull { it.value }
            ?.let { "${it.key.displayName()}训练" } ?: "自由训练"
    }

    // ──────────────────────────────────────
    // MUSCLE_SETS：肌肉组数量
    // ──────────────────────────────────────

    /**
     * 肌肉组数模式：大卡为肌肉覆盖（本周练到的大部位数 / 7，副标题为全部正式组数）；
     * 小卡为训练不足（组数最少的大部位，含 0 组）、重点肌群（组数最多）、肌群平衡（占位）。
     */
    private fun muscleSetItems(
        weekWorkouts: List<Workout>,
        catalog: List<Exercise>,
    ): List<ProgressItemState> {
        val lookup = ExerciseLookup(catalog)
        val counts = mutableMapOf<BodyPart, Int>()
        var totalWorkingSets = 0
        weekWorkouts.forEach { workout ->
            workout.exercises.forEach { log ->
                // 只计已录入的正式组（reps>0）：进行中会话的占位组不虚增组数
                val workingSets = log.sets.count { it.setType == SetType.WORKING && it.reps > 0 }
                totalWorkingSets += workingSets
                val exercise = lookup.find(log.exerciseKey, log.name) ?: return@forEach
                if (workingSets > 0 && exercise.bodyPart in MAJOR_BODY_PARTS) {
                    counts[exercise.bodyPart] = (counts[exercise.bodyPart] ?: 0) + workingSets
                }
            }
        }
        val covered = counts.keys.size
        val head = ProgressItemState(
            id = "muscle-coverage",
            title = "肌肉覆盖",
            subtitle = "有效 $totalWorkingSets 组",
            progress = covered / MAJOR_BODY_PARTS.size.toFloat(),
            valueText = "$covered 部位",
        )
        val underTrained = ProgressItemState(
            id = "under-trained",
            title = "训练不足",
            subtitle = if (totalWorkingSets == 0) {
                "—"
            } else {
                // minByOrNull 取首个最小值，MAJOR_BODY_PARTS 列表序保证平局时的确定性
                MAJOR_BODY_PARTS.minByOrNull { counts[it] ?: 0 }!!.displayName()
            },
        )
        val topMuscle = ProgressItemState(
            id = "top-muscle",
            title = "重点肌群",
            subtitle = counts.entries.maxByOrNull { it.value }
                ?.takeIf { it.value > 0 }
                ?.let { "${it.key.displayName()} · ${it.value} 组" } ?: "—",
        )
        val balance = ProgressItemState(
            id = "muscle-balance",
            title = "肌群平衡",
            subtitle = "AI 即将上线",
        )
        return listOf(head, underTrained, topMuscle, balance)
    }

    // ──────────────────────────────────────
    // VOLUME_PR：容量与突破
    // ──────────────────────────────────────

    /**
     * 容量突破模式：大卡为周总容量（副标题环比上周，水波进度为本周/上周比值）；
     * 小卡为 PR（e1RM 突破最大者，展示实际最佳组）、最大增长（部位容量差）、AI 分析（占位）。
     */
    private fun volumePrItems(
        weekWorkouts: List<Workout>,
        prevWeekWorkouts: List<Workout>,
        allWorkouts: List<Workout>,
        weekStart: LocalDate,
        catalog: List<Exercise>,
    ): List<ProgressItemState> {
        val weekVolume = VolumeAggregator.workingVolume(weekWorkouts)
        val prevVolume = VolumeAggregator.workingVolume(prevWeekWorkouts)
        val head = ProgressItemState(
            id = "week-volume",
            title = "训练容量",
            subtitle = when {
                prevVolume > 0.0 -> {
                    val pct = (((weekVolume - prevVolume) / prevVolume) * 100).roundToInt()
                    when {
                        pct > 0 -> "较上周 +$pct%"
                        pct < 0 -> "较上周 $pct%"
                        else -> "与上周持平"
                    }
                }
                weekVolume > 0.0 -> "上周无数据"
                else -> "本周还没有训练"
            },
            progress = if (prevVolume > 0.0) {
                (weekVolume / prevVolume).toFloat().coerceIn(0f, 1f)
            } else {
                null
            },
            valueText = VolumeFormatter.formatVolume(weekVolume),
        )
        val pr = ProgressItemState(
            id = "pr",
            title = "PR",
            subtitle = detectTopPr(weekWorkouts, allWorkouts, weekStart)
                ?: if (weekWorkouts.isEmpty()) "暂无数据" else "暂无突破",
        )
        val growth = ProgressItemState(
            id = "growth",
            title = "最大增长",
            subtitle = topVolumeGrowth(weekWorkouts, prevWeekWorkouts, catalog),
        )
        val aiAnalysis = ProgressItemState(
            id = "ai-analysis",
            title = "AI 分析",
            subtitle = "即将上线",
        )
        return listOf(head, pr, growth, aiAnalysis)
    }

    /**
     * 本周新 PR 检测：各动作本周最佳 e1RM 超过周前历史最佳（Epley 口径，复用
     * [TrainingLevelCalculator]）时记为突破；取超出幅度（weekRm - historyRm）最大的
     * 1 个，展示其本周实际最佳正式组（如 "卧推 85kg×5"）。无突破返回 null。
     */
    private fun detectTopPr(
        weekWorkouts: List<Workout>,
        allWorkouts: List<Workout>,
        weekStart: LocalDate,
    ): String? {
        val weekBest = TrainingLevelCalculator.calculate(weekWorkouts, null).exercises
        val historyBest = TrainingLevelCalculator.calculate(
            allWorkouts.filter { it.date < weekStart },
            null,
        ).exercises
        // exerciseKey（或动作名兜底）→ 展示名
        val displayNames = weekWorkouts
            .flatMap { it.exercises }
            .associate { (it.exerciseKey ?: it.name) to it.name }

        val topKey = weekBest.entries
            .mapNotNull { (key, weekLevel) ->
                val weekRm = weekLevel.estimatedOneRMKg ?: return@mapNotNull null
                val historyRm = historyBest[key]?.estimatedOneRMKg ?: return@mapNotNull null
                if (weekRm > historyRm) key to (weekRm - historyRm) else null
            }
            .maxByOrNull { it.second }
            ?.first ?: return null

        // 该动作本周实际最佳正式组（Epley 出口与 1RM 计算同源，含 reps>0 过滤）
        val bestSet = TrainingLevelCalculator.bestOneRMSet(
            weekWorkouts
                .flatMap { it.exercises }
                .filter { (it.exerciseKey ?: it.name) == topKey }
                .flatMap { it.sets }
                .filter { it.setType == SetType.WORKING },
        ) ?: return null
        val name = displayNames[topKey] ?: topKey
        return "$name ${formatSetBrief(bestSet.weightKg, bestSet.reps)}"
    }

    /**
     * 「最大增长」：本周 vs 上周各身体部位正式组容量差的最大值（如 "背部 +1.5 吨"）。
     * 上周无容量基线时返回 "暂无基线"；无正增长返回 "暂无增长"。
     */
    private fun topVolumeGrowth(
        weekWorkouts: List<Workout>,
        prevWeekWorkouts: List<Workout>,
        catalog: List<Exercise>,
    ): String {
        if (VolumeAggregator.workingVolume(prevWeekWorkouts) <= 0.0) return "暂无基线"
        val lookup = ExerciseLookup(catalog)
        val weekVolumes = volumeByBodyPart(weekWorkouts, lookup)
        val prevVolumes = volumeByBodyPart(prevWeekWorkouts, lookup)
        val growth = weekVolumes.entries
            .map { (part, volume) -> part to (volume - (prevVolumes[part] ?: 0.0)) }
            .maxByOrNull { it.second }
            ?.takeIf { it.second > 0.0 }
            ?: return "暂无增长"
        return "${growth.first.displayName()} +${VolumeFormatter.formatVolume(growth.second)}"
    }

    // ──────────────────────────────────────
    // CATEGORY：训练类别
    // ──────────────────────────────────────

    /**
     * 训练类别模式：按各 workout 的主导类别（正式组数多者，平局算力量）计次，
     * 大卡以环形图展示力量/有氧占比（恢复类暂无数据来源，仅保留图例位）；
     * 小卡为力量次数、有氧次数、运动目标（占位）。
     */
    private fun categoryItems(
        weekWorkouts: List<Workout>,
        catalog: List<Exercise>,
    ): List<ProgressItemState> {
        val lookup = ExerciseLookup(catalog)
        var strengthCount = 0
        var cardioCount = 0
        weekWorkouts.forEach { workout ->
            var strengthSets = 0
            var cardioSets = 0
            workout.exercises.forEach { log ->
                val exercise = lookup.find(log.exerciseKey, log.name) ?: return@forEach
                val workingSets = log.sets.count { it.setType == SetType.WORKING }
                if (exercise.isCardio()) {
                    cardioSets += workingSets
                } else {
                    strengthSets += workingSets
                }
            }
            // 正式组无法解析的 workout 不计入分布
            if (strengthSets + cardioSets == 0) return@forEach
            if (cardioSets > strengthSets) cardioCount++ else strengthCount++
        }
        val total = strengthCount + cardioCount
        val strengthFraction = if (total > 0) strengthCount / total.toFloat() else 0f
        val cardioFraction = if (total > 0) cardioCount / total.toFloat() else 0f
        val ringSegments = listOf(
            RingSegment(
                label = "力量",
                fraction = strengthFraction,
                colorKey = "strength",
                valueText = if (total > 0) "${(strengthFraction * 100).roundToInt()}%" else "—",
            ),
            RingSegment(
                label = "有氧",
                fraction = cardioFraction,
                colorKey = "cardio",
                valueText = if (total > 0) "${(cardioFraction * 100).roundToInt()}%" else "—",
            ),
            RingSegment(label = "恢复", fraction = 0f, colorKey = "recovery", valueText = "—"),
        )
        val head = ProgressItemState(
            id = "category-distribution",
            title = "训练分布",
            subtitle = "力量 $strengthCount · 有氧 $cardioCount",
            // 与 SPLIT 大卡同口径：只计有动作明细的训练
            valueText = "${weekWorkouts.count { it.isCountable }} 次",
            ringSegments = ringSegments,
        )
        return listOf(
            head,
            ProgressItemState(id = "strength-count", title = "力量训练", subtitle = "$strengthCount 次"),
            ProgressItemState(id = "cardio-count", title = "有氧训练", subtitle = "$cardioCount 次"),
            ProgressItemState(id = "goal", title = "运动目标", subtitle = "即将上线"),
        )
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

    /** 有氧信号：身体部位/主要肌群/器械任一命中 CARDIO 即判定为有氧动作。 */
    private fun Exercise.isCardio(): Boolean =
        bodyPart == BodyPart.CARDIO ||
            Muscle.CARDIO in primaryMuscles ||
            equipment == Equipment.CARDIO_MACHINE

    /** 各身体部位的正式组容量聚合（动作目录解析失败的跳过）。 */
    private fun volumeByBodyPart(
        workouts: List<Workout>,
        lookup: ExerciseLookup,
    ): Map<BodyPart, Double> {
        val volumes = mutableMapOf<BodyPart, Double>()
        workouts.forEach { workout ->
            workout.exercises.forEach { log ->
                val exercise = lookup.find(log.exerciseKey, log.name) ?: return@forEach
                val volume = log.sets
                    .filter { it.setType == SetType.WORKING }
                    .sumOf { (it.weightKg * it.reps).toDouble() }
                if (volume > 0.0) {
                    volumes[exercise.bodyPart] = (volumes[exercise.bodyPart] ?: 0.0) + volume
                }
            }
        }
        return volumes
    }

    /** 正式组简写：如 "85kg×5"；重量格式化统一走 [VolumeFormatter.formatWeightKg] 收口。 */
    private fun formatSetBrief(weightKg: Float, reps: Int): String =
        "${VolumeFormatter.formatWeightKg(weightKg)}kg×$reps"

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
