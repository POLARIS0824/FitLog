package com.example.fitlog.feature.today

data class TodayUiState(
    val coachInsight: CoachInsightState,
    val weekProgress: WeekProgressState,
    val todayPlan: TodayPlanState,
    val uiState: UiState,
)

/** 顶部 AI Coach Insight */
data class CoachInsightState(
    val userName: String = "",
    val greeting: String = "Hello",
    val summary: String = "",
    val recommendation: String = "",
    // 降级策略
    val isAvailable: Boolean = false,
)

/** 本周训练进度 */
data class WeekProgressState(
    val completedWorkouts: Int = 0,
    val targetWorkouts: Int = 4,
    /** 当前选中的展示模式，默认为力量分化日 */
    val displayMode: WeekProgressDisplayMode = WeekProgressDisplayMode.SPLIT,
    /** 根据 displayMode 动态计算生成的渲染项列表 */
    val items: List<ProgressItemState> = emptyList(),
    val statusText: String = "Great job!",
)

enum class WeekProgressDisplayMode(val label: String) {
    SPLIT("分化日"),
    MUSCLE_SETS("肌肉组数量"),
    VOLUME_PR("容量与突破"),
    CATEGORY("训练类别"),
}

/**
 * 统一渲染项模型（通用抽象模型）
 * 无论切换到什么模式，UI 只需要循环渲染这个 List，无需写死字段！
 */
data class ProgressItemState(
    val id: String,
    val title: String,        // 例如 "Push (推)", "上肢肌群", "周总容量", "Strength"
    val subtitle: String,     // 例如 "1 session", "24 组", "18.5 吨", "2 sessions"
    val badgeIconType: String? = null, // 可选图标类型
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