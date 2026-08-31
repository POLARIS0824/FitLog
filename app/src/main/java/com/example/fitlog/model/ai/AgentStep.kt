package com.example.fitlog.model.ai

/**
 * Agent 过程时间线单步的类型。
 *
 * 与持久化存储值（agent_steps.type："thinking"/"tool_call"/"confirm_request"）
 * 一一对应；解析容错见 [fromStorageValue]。
 */
enum class AgentStepType {
    /** 模型在工具调用间隙输出的中间说明文本（"我帮你查一下…"）。 */
    THINKING,

    /** 一次工具调用（含参数摘要）。 */
    TOOL_CALL,

    /** 写操作的确认请求（等待用户允许/拒绝）。 */
    CONFIRM_REQUEST,
    ;

    companion object {
        /**
         * 从存储值解析类型；未知值（未来版本新增类型而旧代码回放）降级为 [THINKING]。
         */
        fun fromStorageValue(value: String): AgentStepType =
            entries.firstOrNull { it.storageValue == value } ?: THINKING
    }

    /** 持久化存储值。 */
    val storageValue: String
        get() = when (this) {
            THINKING -> "thinking"
            TOOL_CALL -> "tool_call"
            CONFIRM_REQUEST -> "confirm_request"
        }
}

/**
 * Agent 过程时间线的单步（领域模型，UI 直接渲染）。
 *
 * @property id 唯一标识（落库自增 id，LazyColumn/动画 key）
 * @property type 步骤类型（决定图标）
 * @property toolKey 工具函数名（如 getRecentWorkouts；thinking 步骤为 null）
 * @property label 展示主文本（思考原文或工具中文名）
 * @property detail 展示副文本（参数摘要，如"最近 5 次"）
 * @property elapsedMs 相对本轮运行开始的活跃耗时（毫秒）
 */
data class AgentStep(
    val id: Long,
    val type: AgentStepType,
    val toolKey: String? = null,
    val label: String,
    val detail: String? = null,
    val elapsedMs: Long,
)
