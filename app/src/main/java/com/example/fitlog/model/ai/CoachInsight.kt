package com.example.fitlog.model.ai

import com.example.fitlog.model.Exercise
import com.example.fitlog.model.PlannedSession
import com.example.fitlog.model.Workout
import com.example.fitlog.model.WorkoutPlan
import com.example.fitlog.model.user.UserProfile
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate

/**
 * 教练建议关联的动作标签（AI 单轮决策结果，非 function calling）。
 *
 * UI 按此标签决定 CoachInsightCard 的按钮槽：
 * - [START_WORKOUT] → 显示"开始训练"按钮
 * - 其余 → 不显示按钮
 *
 * v1 prompt 只产出 [START_WORKOUT] / [REST]；[ADJUST_PLAN] 为后续版本预留。
 */
enum class CoachAction {
    /** 建议按计划/按状态开始训练 */
    START_WORKOUT,

    /** 建议休息恢复（无按钮） */
    REST,

    /** 建议调整计划（v1 不产出、UI 不渲染按钮，预留） */
    ADJUST_PLAN,

    /** 无动作（解析失败/规则兜底的默认值） */
    NONE,
    ;

    companion object {
        /** 从 AI 输出的字符串解析动作标签：未知/缺失一律安全降级为 [NONE]。 */
        fun fromString(raw: String?): CoachAction =
            entries.firstOrNull { it.name == raw?.trim()?.uppercase() } ?: NONE
    }
}

/**
 * AI 生成的教练建议（观察 + 建议 + 动作标签）。
 *
 * @param observation 基于最近训练的观察（如"昨天练了腿，今天身体需要中等强度"）
 * @param recommendation 基于观察的建议（如"建议 30 分钟 Zone 2 有氧 + 10 分钟灵活性训练"）
 * @param action 建议关联的动作标签
 */
data class CoachInsight(
    val observation: String,
    val recommendation: String,
    val action: CoachAction,
)

/**
 * 一次教练建议请求的全部上下文材料。
 *
 * 由 ViewModel 从数据层 Flow 组装，贯穿 prompt 构建（[CoachInsightPrompt]）、
 * 缓存指纹（[fingerprint]）与 AI 请求（CoachInsightRepository），
 * 避免长参数列表在三层之间传递。
 *
 * 契约：[recentWorkouts] 必须按时间倒序（首条为最近一次训练），
 * [fingerprint] 依赖此约定。
 */
data class CoachInsightContext(
    val profile: UserProfile?,
    val weekCompleted: Int,
    val weekTarget: Int,
    val todayCompleted: Boolean,
    val activePlan: WorkoutPlan?,
    val nextSession: PlannedSession?,
    val recentWorkouts: List<Workout>,
    val catalog: List<Exercise>,
    val today: LocalDate,
) {
    /** 缓存指纹：任一维度变化即视为上下文已变，需要重新请求 AI。 */
    fun fingerprint(): String = coachInsightFingerprint(
        today = today,
        planId = activePlan?.id,
        nextSessionId = nextSession?.id,
        latestWorkoutId = recentWorkouts.firstOrNull()?.id,
        weekCompleted = weekCompleted,
        todayCompleted = todayCompleted,
    )
}

/**
 * AI 回复内容的 JSON 结构（message.content 内嵌，非 Retrofit 层 DTO）。
 */
@Serializable
internal data class CoachInsightResponseDto(
    val observation: String = "",
    val recommendation: String = "",
    val action: String? = null,
)

private val insightJson = Json { ignoreUnknownKeys = true }

/**
 * 从 AI 回复原文中容错解析 [CoachInsight]。
 *
 * 容忍常见脏输出：markdown code fence（```json ... ```）、JSON 前后多余文字；
 * observation/recommendation 为空或 JSON 畸形时返回 null（调用方走规则兜底）。
 *
 * @param raw AI 回复的 message.content 原文
 * @return 解析成功返回 [CoachInsight]，失败返回 null
 */
fun parseCoachInsight(raw: String): CoachInsight? {
    val start = raw.indexOf('{')
    val end = raw.lastIndexOf('}')
    if (start < 0 || end <= start) return null
    val dto = runCatching {
        insightJson.decodeFromString<CoachInsightResponseDto>(raw.substring(start, end + 1))
    }.getOrNull() ?: return null
    if (dto.observation.isBlank() || dto.recommendation.isBlank()) return null
    return CoachInsight(
        observation = dto.observation.trim(),
        recommendation = dto.recommendation.trim(),
        action = CoachAction.fromString(dto.action),
    )
}

/**
 * 教练建议的缓存指纹：任一维度变化即视为上下文已变，需要重新请求 AI。
 *
 * 同一天内反复进入 Today 页指纹不变 → 命中缓存零网络请求；
 * 记一笔训练（latestWorkoutId/weekCompleted/todayCompleted 变化）→ 触发重新生成。
 *
 * @param today 今天日期（跨天必刷新）
 * @param planId 激活计划 ID
 * @param nextSessionId 下一未完成课次 ID
 * @param latestWorkoutId 最近一条训练记录 ID
 * @param weekCompleted 本周已完成训练次数
 * @param todayCompleted 今日训练是否已完成
 */
fun coachInsightFingerprint(
    today: LocalDate,
    planId: String?,
    nextSessionId: String?,
    latestWorkoutId: Long?,
    weekCompleted: Int,
    todayCompleted: Boolean,
): String = listOf(
    today.toString(),
    planId ?: "-",
    nextSessionId ?: "-",
    (latestWorkoutId ?: -1L).toString(),
    weekCompleted.toString(),
    todayCompleted.toString(),
).joinToString("|")
