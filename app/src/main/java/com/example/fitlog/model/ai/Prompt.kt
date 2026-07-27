package com.example.fitlog.model.ai

import com.example.fitlog.model.Exercise
import com.example.fitlog.model.SetType
import com.example.fitlog.model.Workout
import com.example.fitlog.model.user.Gender
import com.example.fitlog.model.user.TrainingGoal

object SystemPrompt {
    val SYSTEM_PROMPT = ChatMessage(
        role = "system",
        content = "You are a professional fitness coach"
    )
}

/**
 * Coach Insight 卡片的 AI Prompt 构建器。
 *
 * ## 设计要点
 *
 * - **JSON 结构化输出**（非 function calling）：模型单轮返回
 *   `{"observation", "recommendation", "action"}`，客户端解析后按
 *   [CoachAction] 驱动卡片按钮槽。解析容错见 [parseCoachInsight]。
 * - **计划优先原则**：今日课次（[PlannedSession]）注入上下文，AI 在
 *   "恢复状态"与"计划课表"之间做裁判——避免与同屏今日训练卡互相矛盾。
 * - v1 action 仅允许 `START_WORKOUT` / `REST`（[CoachAction.ADJUST_PLAN] 预留）。
 * - 全部中文输出，字段长度受约束（卡片版面有限）。
 */
object CoachInsightPrompt {

    /** 系统提示词：输出契约 + 决策原则。 */
    val SYSTEM_PROMPT = ChatMessage(
        role = "system",
        content = """
            你是一位熟悉用户的专业私教，为用户生成今日训练观察与建议。

            【输出契约】
            - 只输出一个 JSON 对象，禁止输出 markdown 代码块、解释或任何其他文字
            - JSON 结构：{"observation": "...", "recommendation": "...", "action": "..."}
            - observation：基于用户最近训练的观察，中文，40 字以内
            - recommendation：给出的今日建议，中文，50 字以内
            - action：只能是 "START_WORKOUT" 或 "REST"

            【决策原则】
            - 计划优先：用户有今日计划课次且身体状态正常时，action 输出 "START_WORKOUT"，recommendation 围绕该课次展开
            - 仅当恢复信号强烈时（昨天练过相同部位、连续多天高强度训练），action 输出 "REST"，observation 说明原因，recommendation 给出恢复建议（如拉伸、散步、Zone 2 有氧）
            - 今日已完成训练时，action 输出 "REST"，recommendation 为恢复与休息建议
            - 语气温和专业，像了解用户的私教在说话；不使用 emoji；不提供医疗建议
        """.trimIndent(),
    )

    /**
     * 组装一次教练建议请求的完整消息列表（system + user）。
     *
     * @param context 全部上下文材料（见 [CoachInsightContext]）
     */
    fun buildMessages(context: CoachInsightContext): List<ChatMessage> {
        val profile = context.profile
        val activePlan = context.activePlan
        val nextSession = context.nextSession
        val today = context.today
        val user = buildString {
            appendLine("【今天】${today.year}年${today.monthValue}月${today.dayOfMonth}日")

            // ── 用户 ──
            val profileParts = buildList {
                profile?.name?.takeIf { it.isNotBlank() }?.let { add("名字：$it") }
                profile?.trainingGoal?.let { add("训练目标：${it.displayName()}") }
                profile?.age?.let { add("年龄：$it") }
                profile?.gender?.let { add("性别：${it.displayName()}") }
            }
            if (profileParts.isNotEmpty()) appendLine("【用户】${profileParts.joinToString("，")}")

            // ── 本周进度 ──
            appendLine(
                "【本周进度】已完成 ${context.weekCompleted}/${context.weekTarget} 次；" +
                    if (context.todayCompleted) "今天已练" else "今天未练",
            )

            // ── 今日课次 ──
            when {
                nextSession != null -> {
                    val position = activePlan?.let { "第 ${nextSession.weekNumber} 周第 ${nextSession.dayNumber} 天" }
                    val duration = nextSession.targetDurationMinutes?.let { "，目标 $it 分钟" } ?: ""
                    val planName = activePlan?.name?.let { "（计划「$it」${position ?: ""}）" } ?: ""
                    appendLine(
                        "【今日课次】${nextSession.name}$planName：" +
                            "${nextSession.exercises.size} 个动作$duration",
                    )
                }

                activePlan != null -> appendLine("【今日课次】当前计划已全部完成")
                else -> appendLine("【今日课次】无激活计划（自由训练）")
            }

            // ── 最近训练 ──
            if (context.recentWorkouts.isEmpty()) {
                appendLine("【最近训练】无训练记录")
            } else {
                appendLine("【最近训练】")
                context.recentWorkouts.forEach { workout ->
                    appendLine("- ${summarizeWorkout(workout, context.catalog)}")
                }
            }

            append("请生成今日观察与建议。")
        }
        return listOf(SYSTEM_PROMPT, ChatMessage(role = "user", content = user))
    }

    /**
     * 单次训练摘要：日期 + 主导部位（按正式组数取前 2）+ 正式组数 + 容量。
     * 如 "7月26日：腿/臀、小腿为主，18 组正式组，容量 1.2 吨"。
     */
    private fun summarizeWorkout(workout: Workout, catalog: List<Exercise>): String {
        val byKey = catalog.associateBy { it.id }
        val byName = catalog.associateBy { it.name }

        // 各部位正式组数
        val setsByPart = mutableMapOf<String, Int>()
        var workingSets = 0
        var volumeKg = 0.0
        workout.exercises.forEach { log ->
            val exercise = log.exerciseKey?.let { byKey[it] } ?: byName[log.name]
            val partName = exercise?.bodyPart?.displayName()
            log.sets.filter { it.setType == SetType.WORKING }.forEach { set ->
                workingSets++
                volumeKg += set.weightKg * set.reps
                if (partName != null) {
                    setsByPart[partName] = (setsByPart[partName] ?: 0) + 1
                }
            }
        }

        val datePart = "${workout.date.monthValue}月${workout.date.dayOfMonth}日"
        val partPart = setsByPart.entries.sortedByDescending { it.value }
            .take(2)
            .joinToString("、") { it.key }
            .takeIf { it.isNotEmpty() }
            ?.let { "$it 为主，" } ?: ""
        val volumePart = if (volumeKg >= 1000) {
            "，容量 %.1f 吨".format(volumeKg / 1000)
        } else if (volumeKg > 0) {
            "，容量 ${volumeKg.toInt()} kg"
        } else {
            ""
        }
        return "$datePart：$partPart$workingSets 组正式组$volumePart"
    }

    private fun TrainingGoal.displayName(): String = when (this) {
        TrainingGoal.HYPERTROPHY -> "增肌"
        TrainingGoal.FATLOSS -> "减脂"
        TrainingGoal.STRENGTH -> "力量"
    }

    private fun Gender.displayName(): String = when (this) {
        Gender.MALE -> "男"
        Gender.FEMALE -> "女"
        Gender.OTHER -> "其他"
    }

    /** [com.example.fitlog.model.BodyPart] → 中文名（与 WeekProgressCalculator 口径一致）。 */
    private fun com.example.fitlog.model.BodyPart.displayName(): String = when (this) {
        com.example.fitlog.model.BodyPart.CHEST -> "胸部"
        com.example.fitlog.model.BodyPart.BACK -> "背部"
        com.example.fitlog.model.BodyPart.SHOULDERS -> "肩部"
        com.example.fitlog.model.BodyPart.UPPER_ARMS -> "上臂"
        com.example.fitlog.model.BodyPart.LOWER_ARMS -> "前臂"
        com.example.fitlog.model.BodyPart.UPPER_LEGS -> "腿/臀"
        com.example.fitlog.model.BodyPart.LOWER_LEGS -> "小腿"
        com.example.fitlog.model.BodyPart.WAIST -> "腰腹"
        com.example.fitlog.model.BodyPart.NECK -> "颈部"
        com.example.fitlog.model.BodyPart.CARDIO -> "有氧"
    }
}
