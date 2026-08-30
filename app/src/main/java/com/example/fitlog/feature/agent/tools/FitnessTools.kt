package com.example.fitlog.feature.agent.tools

import com.example.fitlog.data.repository.BodyMetricRepository
import com.example.fitlog.data.repository.ExerciseRepository
import com.example.fitlog.data.repository.UserProfileRepository
import com.example.fitlog.data.repository.WorkoutPlanRepository
import com.example.fitlog.data.repository.WorkoutRepository
import com.example.fitlog.model.SetType
import com.example.fitlog.model.Workout
import com.example.fitlog.model.user.UserProfile
import com.example.fitlog.util.TrainingLevelCalculator
import com.google.adk.kt.annotations.Param
import com.google.adk.kt.annotations.Tool
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject

/**
 * FitLog Agent 的工具集：把用户健身数据暴露给 ADK agent。
 *
 * ## 设计约束（来自 ADK KSP 处理器）
 *
 * - **返回类型**只支持原始类型 / 枚举 / List / Map / data class（不支持 LocalDate 等），
 *   因此全部返回轻量 [Tool] DTO（字符串日期）。
 * - **参数默认值必须 nullable**（`count: Int? = 5` 而非 `count: Int = 5`），
 *   否则 KSP 报错。
 * - **写操作**（[logBodyWeight]、[setActivePlan]）标 `requireConfirmation = true`，
 *   ADK 首次调用会暂停等用户确认，确认后才真正执行。
 *
 * ## 口径
 *
 * - 容量/训练水平统计与全 App 一致：**只计 [SetType.WORKING] 正式组**，
 *   热身组仅作展示。
 * - 中文输出为主（动作名/身体部位保留英文 id 供精确匹配）。
 */
class FitnessTools @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val workoutPlanRepository: WorkoutPlanRepository,
    private val userProfileRepository: UserProfileRepository,
    private val exerciseRepository: ExerciseRepository,
    private val bodyMetricRepository: BodyMetricRepository,
) {

    // ──────────────────────────────────────
    // 读工具：用户与训练数据
    // ──────────────────────────────────────

    /**
     * 获取用户个人资料（训练目标、年龄、性别、身高、体重）。
     */
    @Tool
    suspend fun getUserProfile(): UserProfileDto? =
        userProfileRepository.getFirst()?.toDto()

    /**
     * 获取最近 N 次训练摘要（日期、主导部位、正式组数、总容量、时长）。
     *
     * @param count 返回条数，默认 5，最大 20
     */
    @Tool
    suspend fun getRecentWorkouts(
        @Param("返回条数，1-20，默认 5") count: Int? = 5,
    ): List<WorkoutSummaryDto> {
        val limit = (count ?: 5).coerceIn(1, 20)
        return workoutRepository.getRecentWithDetails(limit).first().map { it.toSummaryDto() }
    }

    /**
     * 获取单次训练完整明细（每个动作的每组重量/次数/组类型）。
     *
     * @param workoutId 训练记录 id（来自 getRecentWorkouts）
     */
    @Tool
    suspend fun getWorkoutDetail(
        @Param("训练记录 id") workoutId: Int,
    ): WorkoutDetailDto? =
        workoutRepository.getById(workoutId.toLong())?.toDetailDto()

    /**
     * 读取 Markdown 导入的训练日原文（未结构化的训练笔记）。
     *
     * 历史导入的训练记录只存了表头与原文（动作未结构化），
     * AI 结合原文才能理解当天的实际训练内容。
     *
     * @param workoutId 训练记录 id（来自 getRecentWorkouts）
     */
    @Tool
    suspend fun getImportedWorkoutContent(
        @Param("训练记录 id") workoutId: Int,
    ): String? {
        val workout = workoutRepository.getById(workoutId.toLong()) ?: return null
        // 结构化记录没有原文可读，返回空让模型直接走 getWorkoutDetail
        return workout.rawContent?.takeIf { it.isNotBlank() }
    }

    /**
     * 获取当前激活训练计划（进度 + 下一未完成课次详情）。
     */
    @Tool
    suspend fun getActivePlan(): ActivePlanDto? {
        val plan = workoutPlanRepository.activePlan.first() ?: return null
        val next = workoutPlanRepository.getNextIncompleteSession(plan.id).first()
        return ActivePlanDto(
            id = plan.id,
            name = plan.name,
            goal = plan.goal?.name,
            durationWeeks = plan.durationWeeks,
            sessionsPerWeek = plan.sessionsPerWeek,
            nextSession = next?.toDto(),
        )
    }

    /**
     * 获取全部训练计划列表（id、名称、目标、每周次数）。
     */
    @Tool
    suspend fun getAllPlans(): List<PlanSummaryDto> =
        workoutPlanRepository.getAllPlans().map {
            PlanSummaryDto(
                id = it.id,
                name = it.name,
                goal = it.goal?.name,
                durationWeeks = it.durationWeeks,
                sessionsPerWeek = it.sessionsPerWeek,
            )
        }

    /**
     * 获取近 N 天体重趋势（日期 + 体重，升序）。
     *
     * @param days 回溯天数，默认 30，最大 365
     */
    @Tool
    suspend fun getBodyMetrics(
        @Param("回溯天数，1-365，默认 30") days: Int? = 30,
    ): List<BodyMetricDto> {
        val span = (days ?: 30).coerceIn(1, 365)
        val from = LocalDate.now().minusDays((span - 1).toLong())
        return bodyMetricRepository.getByDateRange(from, LocalDate.now()).first()
            .map { BodyMetricDto(date = it.date.toString(), weightKg = it.weightKg) }
    }

    /**
     * 在动作库中搜索动作（按名称模糊搜索或按身体部位筛选）。
     *
     * @param query 动作名称关键词（可选）
     * @param bodyPart 身体部位（可选，如 CHEST/BACK/LEGS）
     */
    @Tool
    suspend fun searchExercises(
        @Param("动作名称关键词，可选") query: String? = null,
        @Param("身体部位英文名，可选") bodyPart: String? = null,
    ): List<ExerciseDto> {
        val results = when {
            !query.isNullOrBlank() -> exerciseRepository.searchByName(query)
            !bodyPart.isNullOrBlank() -> exerciseRepository.getByBodyPart(bodyPart)
            else -> exerciseRepository.getAll()
        }
        return results.take(30).map { it.toDto() }
    }

    /**
     * 获取某动作的训练水平（估算 1RM、相对力量、历史最佳单次容量）。
     *
     * @param exerciseKey 动作 id（kebab-case，如 barbell-bench-press）
     */
    @Tool
    suspend fun getExerciseStats(
        @Param("动作 id，如 barbell-bench-press") exerciseKey: String,
    ): ExerciseStatsDto? {
        val profile = userProfileRepository.getFirst()
        val workouts = workoutRepository.getWorkouts().first()
        val level = TrainingLevelCalculator.calculate(workouts, profile?.weight).exercises[exerciseKey]
            ?: return null
        return ExerciseStatsDto(
            exerciseKey = exerciseKey,
            estimatedOneRMKg = level.estimatedOneRMKg,
            relativeStrength = level.relativeStrength,
            bestVolumeLoadKg = level.bestVolumeLoadKg,
        )
    }

    /**
     * 获取本周与上周训练对比概览（训练次数、总正式组数、总容量）。
     */
    @Tool
    suspend fun getWeeklySummary(): WeeklySummaryDto {
        val today = LocalDate.now()
        val thisWeekStart = today.with(DayOfWeek.MONDAY)
        val lastWeekStart = thisWeekStart.minusWeeks(1)
        val thisWeek = workoutRepository.getByDateRange(thisWeekStart, today).first()
        val lastWeek = workoutRepository.getByDateRange(lastWeekStart, thisWeekStart.minusDays(1)).first()
        return WeeklySummaryDto(
            thisWeek = thisWeek.toPeriodSummary(),
            lastWeek = lastWeek.toPeriodSummary(),
            todayWorkouts = workoutRepository.getByDate(today).first().size,
        )
    }

    // ──────────────────────────────────────
    // 写工具：低风险、可撤销、确认门控
    // ──────────────────────────────────────

    /**
     * 记录今日体重（按天去重，同日重复记录覆盖旧值）。
     *
     * @param weightKg 体重（公斤），如 72.5
     */
    @Tool(requireConfirmation = true)
    suspend fun logBodyWeight(
        @Param("体重公斤数，如 72.5（有效范围 20~300）") weightKg: Double,
    ): WriteResultDto {
        // 边界钳制：模型幻觉出负数/0/天文数字时不落脏数据（同日 upsert 会覆盖真实值）
        val sanitized = weightKg.coerceIn(20.0, 300.0)
        val date = LocalDate.now()
        bodyMetricRepository.upsert(
            com.example.fitlog.model.BodyMetric(date = date, weightKg = sanitized.toFloat()),
        )
        // 钳制发生时必须在结果里告知，否则模型拿到 success 会向用户复述被无声扭曲的数值
        val clampNote = if (sanitized != weightKg) {
            "（原值 $weightKg kg 超出有效范围，已按上下限钳制）"
        } else {
            ""
        }
        return WriteResultDto(
            success = true,
            message = "已记录 ${date} 体重 ${sanitized} kg$clampNote",
        )
    }

    /**
     * 切换当前激活的训练计划（可随时切回原计划，低风险）。
     *
     * @param planId 计划 id（来自 getAllPlans）
     */
    @Tool(requireConfirmation = true)
    suspend fun setActivePlan(
        @Param("计划 id") planId: String,
    ): WriteResultDto {
        val exists = workoutPlanRepository.getAllPlans().any { it.id == planId }
        if (!exists) {
            return WriteResultDto(success = false, message = "计划不存在：$planId")
        }
        workoutPlanRepository.setActivePlanId(planId)
        return WriteResultDto(success = true, message = "已切换到计划：$planId")
    }

    // ──────────────────────────────────────
    // DTO（KSP 可序列化：data class / 原始类型 / List / 枚举）
    // ──────────────────────────────────────

    /** 用户资料（日期无，字段全原始类型/枚举）。 */
    data class UserProfileDto(
        val id: Int,
        val name: String,
        val age: Int?,
        val gender: String?,
        val heightCm: Float?,
        val weightKg: Float?,
        val trainingGoal: String?,
    )

    /** 训练摘要（列表项）。 */
    data class WorkoutSummaryDto(
        val id: Int,
        val date: String,
        val durationMinutes: Int?,
        val workingSets: Int,
        val volumeKg: Double,
        /** 主导部位（逗号分隔），避免 KSP 嵌套 List 序列化限制。 */
        val dominantParts: String,
    )

    /** 训练明细（含每个动作的组）。 */
    data class WorkoutDetailDto(
        val id: Int,
        val date: String,
        val feelings: String?,
        /** 动作与组明细（每动作一段："动作名 | 每组重量kg×次数(组类型)"，多段换行分隔），
         * 避免 KSP 嵌套 List 序列化限制。 */
        val exercises: String,
    )

    data class ActivePlanDto(
        val id: String,
        val name: String,
        val goal: String?,
        val durationWeeks: Int,
        val sessionsPerWeek: Int,
        val nextSession: PlannedSessionDto?,
    )

    data class PlannedSessionDto(
        val id: String,
        val name: String,
        val weekNumber: Int,
        val dayNumber: Int,
        val targetDurationMinutes: Int?,
        /** 计划动作明细（每动作一行："动作名 | 组数x次数区间 [备注]"），
         * 避免 KSP 嵌套 List 序列化限制。 */
        val exercises: String,
    )

    data class PlanSummaryDto(
        val id: String,
        val name: String,
        val goal: String?,
        val durationWeeks: Int,
        val sessionsPerWeek: Int,
    )

    data class BodyMetricDto(
        val date: String,
        val weightKg: Float,
    )

    data class ExerciseDto(
        val id: String,
        val name: String,
        val bodyPart: String,
        /** 主要肌群（逗号分隔），避免 KSP 嵌套 List 序列化限制。 */
        val primaryMuscles: String,
        val equipment: String?,
        val isCompound: Boolean,
        val isCustom: Boolean,
        val description: String?,
    )

    data class ExerciseStatsDto(
        val exerciseKey: String,
        val estimatedOneRMKg: Double?,
        val relativeStrength: Double?,
        val bestVolumeLoadKg: Double?,
    )

    data class PeriodSummaryDto(
        val workouts: Int,
        val workingSets: Int,
        val volumeKg: Double,
    )

    data class WeeklySummaryDto(
        val thisWeek: PeriodSummaryDto,
        val lastWeek: PeriodSummaryDto,
        val todayWorkouts: Int,
    )

    data class WriteResultDto(
        val success: Boolean,
        val message: String,
    )

    // ──────────────────────────────────────
    // 映射（领域模型 → DTO）
    // ──────────────────────────────────────

    private fun UserProfile.toDto(): UserProfileDto = UserProfileDto(
        id = id.toInt(),
        name = name,
        age = age,
        gender = gender?.name,
        heightCm = height,
        weightKg = weight,
        trainingGoal = trainingGoal?.name,
    )

    private fun Workout.toSummaryDto(): WorkoutSummaryDto {
        var workingSets = 0
        var volumeKg = 0.0
        val setsByPart = mutableMapOf<String, Int>()
        exercises.forEach { log ->
            log.sets.filter { it.setType == SetType.WORKING }.forEach { set ->
                workingSets++
                volumeKg += set.weightKg * set.reps
            }
        }
        // 主导部位：按动作名聚合（无 bodyPart 映射，v1 用动作名）
        exercises.forEach { log ->
            log.sets.count { it.setType == SetType.WORKING }
                .takeIf { it > 0 }
                ?.let { setsByPart.merge(log.name, it, Int::plus) }
        }
        val duration = if (startedAt != null && endedAt != null) {
            ((endedAt - startedAt) / 60000L).toInt().coerceAtLeast(0)
        } else {
            null
        }
        return WorkoutSummaryDto(
            id = id.toInt(),
            date = date.toString(),
            durationMinutes = duration,
            workingSets = workingSets,
            volumeKg = volumeKg,
            dominantParts = setsByPart.entries.sortedByDescending { it.value }
                .take(2).joinToString(", ") { it.key },
        )
    }

    private fun Workout.toDetailDto(): WorkoutDetailDto = WorkoutDetailDto(
        id = id.toInt(),
        date = date.toString(),
        feelings = feelings,
        exercises = exercises.joinToString("\n\n") { log ->
            val setsText = log.sets.joinToString("; ") { set ->
                set.weightKg.toString() + "kg×" + set.reps + "(" + set.setType.name + ")"
            }
            log.name + (log.exerciseKey?.let { " [" + it + "]" } ?: "") + " | " + setsText
        },
    )

    private fun com.example.fitlog.model.PlannedSession.toDto(): PlannedSessionDto =
        PlannedSessionDto(
            id = id,
            name = name,
            weekNumber = weekNumber,
            dayNumber = dayNumber,
            targetDurationMinutes = targetDurationMinutes,
            exercises = exercises.joinToString("\n") { ex ->
                (ex.exerciseName ?: ex.exerciseKey) + 
                    " | " + ex.targetSets + "组 x " + 
                    (ex.targetRepsMin?.toString() ?: "") + "-" + (ex.targetRepsMax?.toString() ?: "") +
                    " 次" + (ex.notes?.let { " [" + it + "]" } ?: "")
            },
        )

    private fun com.example.fitlog.model.Exercise.toDto(): ExerciseDto = ExerciseDto(
        id = id,
        name = name,
        bodyPart = bodyPart.name,
        primaryMuscles = primaryMuscles.joinToString(", ") { it.name },
        equipment = equipment?.name,
        isCompound = isCompound,
        isCustom = isCustom,
        description = description,
    )

    private fun List<Workout>.toPeriodSummary(): PeriodSummaryDto {
        var workingSets = 0
        var volumeKg = 0.0
        forEach { workout ->
            workout.exercises.forEach { log ->
                log.sets.filter { it.setType == SetType.WORKING }.forEach { set ->
                    workingSets++
                    volumeKg += set.weightKg * set.reps
                }
            }
        }
        return PeriodSummaryDto(workouts = size, workingSets = workingSets, volumeKg = volumeKg)
    }
}
