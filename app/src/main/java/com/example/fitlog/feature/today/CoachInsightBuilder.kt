package com.example.fitlog.feature.today

import com.example.fitlog.model.PlannedSession
import com.example.fitlog.model.Workout
import com.example.fitlog.model.ai.CoachAction
import com.example.fitlog.model.user.UserProfile
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Coach Insight 卡片的文案生成器
 *
 * 承担两个角色：
 * 1. **即时底版**：AI 请求返回前先上屏，消除内容空白
 * 2. **降级兜底**：未配置 AI 服务商/无网络/AI 回复解析失败时静默保持
 *
 * 纯函数对象：所有输入显式传参（含日期与小时），便于 JVM 单元测试。
 * 降级策略：[CoachInsightState.isAvailable] 仅在"有任何训练数据或激活计划"时为 true，
 * 全新用户由 UI 渲染固定降级卡。
 */
object CoachInsightBuilder {

    /**
     * 生成 Coach Insight 卡片状态。
     *
     * @param profile 用户资料（未填写时为 null，greeting 不带名字）
     * @param weekCompleted 本周已完成训练次数
     * @param weekTarget 本周目标训练次数
     * @param latestWorkout 最近一次训练（无训练史为 null）
     * @param nextSession 激活计划的下一个未完成训练日（无激活计划或已全部完成为 null）
     * @param todayCompleted 今日训练是否已完成
     * @param hasActivePlan 是否有激活计划
     * @param today 今天日期
     * @param hour 当前小时（0-23）
     */
    fun build(
        profile: UserProfile?,
        weekCompleted: Int,
        weekTarget: Int,
        latestWorkout: Workout?,
        nextSession: PlannedSession?,
        todayCompleted: Boolean,
        hasActivePlan: Boolean,
        today: LocalDate,
        hour: Int,
    ): CoachInsightState {
        val timeGreeting = when (hour) {
            in 5..10 -> "早上好"
            in 11 .. 12 -> "中午好"
            in 13..18 -> "下午好"
            else -> "晚上好"
        }
        val name = profile?.name?.takeIf { it.isNotBlank() }
        val greeting = if (name != null) "$timeGreeting，$name" else timeGreeting

        val observation = if (latestWorkout != null) {
            val days = ChronoUnit.DAYS.between(latestWorkout.date, today)
            val lastPart = if (days <= 0L) "今天已练" else "距上次训练 $days 天"
            "本周已练 $weekCompleted/$weekTarget 次 · $lastPart"
        } else {
            "还没有训练记录，从第一练开始吧"
        }

        val recommendation = when {
            todayCompleted -> "今天的训练已完成，好好休息恢复"
            nextSession != null -> "下一课：${nextSession.name}"
            hasActivePlan -> "当前计划已全部完成，去挑选下一套计划吧"
            else -> "今天适合休息，或做一次轻量恢复训练"
        }

        // 规则版动作兜底：有今日课次且未练 → 开始训练；其余无按钮
        val action = if (!todayCompleted && nextSession != null) {
            CoachAction.START_WORKOUT
        } else {
            CoachAction.NONE
        }

        return CoachInsightState(
            userName = name.orEmpty(),
            greeting = greeting,
            observation = observation,
            recommendation = recommendation,
            action = action,
            isAvailable = hasActivePlan || latestWorkout != null,
        )
    }
}
