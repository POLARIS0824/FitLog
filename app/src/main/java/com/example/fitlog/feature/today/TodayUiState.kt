package com.example.fitlog.feature.today

import com.example.fitlog.model.ai.CoachAction
import com.example.fitlog.ui.components.RingSegment

data class TodayUiState(
    val coachInsight: CoachInsightState,
    val weekProgress: WeekProgressState,
    val todayPlan: TodayPlanState,
    val uiState: UiState,
)

/**
 * 顶部 Coach Insight 卡片状态。
 *
 * 内容来源有两级：规则版（[CoachInsightBuilder]，即时上屏）与 AI 版
 * （CoachInsightRepository，异步替换）。AI 失败/未配置时静默保持规则版。
 */
data class CoachInsightState(
    val userName: String = "",
    val greeting: String = "Hello",
    /** 基于最近训练的观察（规则版为训练摘要，AI 版为教练观察） */
    val observation: String = "",
    val recommendation: String = "",
    /** 建议关联的动作标签，驱动按钮槽（仅 [CoachAction.START_WORKOUT] 显示按钮） */
    val action: CoachAction = CoachAction.NONE,
    /** 当前内容是否由 AI 生成（区分 label 文案："AI Coach" / "Coach"） */
    val isAiGenerated: Boolean = false,
    /** AI 请求进行中（label 旁显示波浪进度；内容区仍展示规则版） */
    val isAiLoading: Boolean = false,
    // 降级策略
    val isAvailable: Boolean = false,
)

/** 本周训练进度 */
data class WeekProgressState(
    val completedWorkouts: Int = 0,
    val targetWorkouts: Int = 4,
    /** 当前选中的展示模式，默认为力量分化日 */
    val displayMode: WeekProgressDisplayMode = WeekProgressDisplayMode.SPLIT,
    /** 当前模式的渲染项列表（固定 4 个：1 大 + 3 小） */
    val items: List<ProgressItemState> = emptyList(),
    /** 预先计算好的全部模式渲染项，供 HorizontalPager 滑动时实现 0ms 零延迟极速切换 */
    val itemsMap: Map<WeekProgressDisplayMode, List<ProgressItemState>> = emptyMap(),
    /** 状态文案；现仅作旧契约（head 无 valueText）时大卡副标题的兜底 */
    val statusText: String = "Great job!",
)

enum class WeekProgressDisplayMode(val label: String) {
    SPLIT("分化日"),
    MUSCLE_SETS("肌肉组数量"),
    VOLUME_PR("容量与突破"),
    CATEGORY("训练类别"),
}

/**
 * 统一渲染项模型（通用抽象模型）。
 *
 * 契约：每个模式固定输出 4 个渲染项——items[0] 进左侧大卡
 * （title=标题、valueText=主数值、subtitle=副标题、progress=水波进度、
 * ringSegments=环形图分段）；items[1..3] 进右侧小卡（title=标题、subtitle=数值行）。
 * 占位卡是显式 item（如 value="即将上线"），不留空槽。
 * 无论切换到什么模式，UI 只需要循环渲染这个 List，无需写死字段！
 */
data class ProgressItemState(
    val id: String,
    val title: String,        // 例如 "本周训练", "重点肌群", "PR", "力量训练"
    val subtitle: String,     // 大卡为副标题（如 "目标 4 次"），小卡为数值行（如 "胸部 · 12 组"）
    val badgeIconType: String? = null, // 可选图标类型
    val progress: Float? = null,      // 大卡可选填充进度 (0.0f ~ 1.0f)
    /** 大卡主数值（仅 items[0] 使用；为 null 时大卡沿用 subtitle 作数值，兼容旧契约） */
    val valueText: String? = null,
    /** 大卡环形图分段（仅 items[0]；非空时大卡渲染环形图替代水波进度） */
    val ringSegments: List<RingSegment>? = null,
)

/** 今日训练计划 */
data class TodayPlanState(
    val planId: String? = null,
    val sessionId: String? = null,
    /** 训练标题（如 "Leg Day - Strength"） */
    val title: String = "",
    /** 训练副标题描述（如 "6 exercises · 45 min"） */
    val subtitle: String = "",
    /** 当前完成进度 (0.0f ~ 1.0f，如 0.45f) */
    val progress: Float = 0f,
    /** 关联的训练记录 ID（进行中或已完成时关联 workouts.id） */
    val workoutId: Long? = null,
    /** 核心驱动状态：未开始 / 进行中 / 已完成 / 无计划 */
    val status: PlanStatus = PlanStatus.NOT_STARTED,
) {
    /** 动态导出的按钮显示文字 */
    val buttonText: String
        get() = when (status) {
            PlanStatus.NO_PLAN -> "选择训练计划"
            PlanStatus.NOT_STARTED -> "开始训练"
            PlanStatus.IN_PROGRESS -> "继续训练"
            PlanStatus.COMPLETED -> "查看训练记录"
        }
    /** 动态导出的百分比文本（如 "45%"） */
    val progressPercentageText: String
        get() = "${(progress.coerceIn(0f, 1f) * 100).toInt()}%"
}

/** 训练卡片生命周期状态枚举 */
enum class PlanStatus {
    /** 无计划 / 休息日 */
    NO_PLAN,
    /** 未开始 (进度 0%) */
    NOT_STARTED,
    /** 进行中 (进度 1%~99%) */
    IN_PROGRESS,
    /** 已完成 (进度 100%) */
    COMPLETED,
}

data class UiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)