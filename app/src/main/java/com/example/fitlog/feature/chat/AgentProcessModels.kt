package com.example.fitlog.feature.chat

/**
 * Agent 过程时间线单步的类型。
 *
 * 与 [com.example.fitlog.data.local.entity.chat.AgentStepEntity.type] 的存储值一一对应。
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
 * Agent 过程时间线的单步（UI 展示模型）。
 *
 * @property id 唯一标识（LazyColumn/动画 key）
 * @property type 步骤类型（决定图标）
 * @property toolKey 工具函数名（如 getRecentWorkouts；thinking 步骤为 null）
 * @property label 展示主文本（思考原文或工具中文名）
 * @property detail 展示副文本（参数摘要，如"最近 5 次"）
 * @property elapsedMs 相对本轮运行开始的活跃耗时（毫秒）
 */
data class AgentStepUi(
    val id: Long,
    val type: AgentStepType,
    val toolKey: String? = null,
    val label: String,
    val detail: String? = null,
    val elapsedMs: Long,
)

/**
 * 一条聊天消息的 UI 模型：消息本体 + （assistant 消息）挂载的过程时间线。
 *
 * @property id 消息唯一 id（Room 自增，LazyColumn 稳定 key）
 * @property role 消息角色："user" / "assistant"
 * @property content 消息文本内容
 * @property steps 产生该回答的 Agent 运行步骤（按时间升序；用户消息恒为空）
 * @property durationMs 该轮 Agent 的活跃耗时（毫秒，确认等待不计入）；历史 seed 数据为 null
 */
data class ChatUiMessage(
    val id: Long,
    val role: String,
    val content: String,
    val steps: List<AgentStepUi> = emptyList(),
    val durationMs: Long? = null,
)

/**
 * 正在进行中（或暂停等待确认）的一轮 Agent 运行。
 *
 * 列表尾部据此渲染展开的时间线卡片；运行正常结束时整轮步骤转为
 * [ChatUiMessage.steps] 挂到最终回答上，本对象清空。
 *
 * @property runId 运行 id（UUID，与落库的 agent_steps.runId 一致）
 * @property steps 已发生的步骤（按时间升序）
 * @property activeMs 累计活跃耗时（毫秒；ViewModel 每秒 tick，确认等待期间暂停）
 * @property awaitingConfirmation 是否正暂停等待用户确认写操作
 */
data class ActiveRun(
    val runId: String,
    val steps: List<AgentStepUi> = emptyList(),
    val activeMs: Long = 0L,
    val awaitingConfirmation: Boolean = false,
)
