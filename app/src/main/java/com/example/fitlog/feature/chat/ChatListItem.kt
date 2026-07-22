package com.example.fitlog.feature.chat

import java.util.UUID

/**
 * Chat 消息列表的展示模型。
 *
 * 与发给 LLM 的 [com.example.fitlog.model.ai.ChatMessage] 分离：
 * role=tool 的消息不进 UI 列表（其结果由后续 assistant 消息转述），
 * tool 调用过程以 [ToolCallItem] 指示条呈现。
 */
sealed interface ChatListItem {

    /** LazyColumn key 用的稳定 ID。 */
    val id: String

    /**
     * 用户消息。
     */
    data class UserMessage(
        override val id: String = UUID.randomUUID().toString(),
        val text: String,
    ) : ChatListItem

    /**
     * AI 文本回复。
     */
    data class AssistantMessage(
        override val id: String = UUID.randomUUID().toString(),
        val text: String,
    ) : ChatListItem

    /**
     * tool 调用指示条。
     *
     * @param id 与 [com.example.fitlog.model.ai.ToolCall.id] 一致，便于状态更新定位
     * @param toolName 工具名（snake_case），展示时经 [toolDisplayName] 转中文
     * @param status 执行状态（执行中/成功/失败）
     */
    data class ToolCallItem(
        override val id: String,
        val toolName: String,
        val status: Status,
    ) : ChatListItem {
        /**
         * tool 执行状态。
         */
        enum class Status { RUNNING, SUCCESS, FAILED }
    }

    /**
     * 错误提示条（保留在列表中，与一次性 snackbar 互补）。
     */
    data class ErrorItem(
        override val id: String = UUID.randomUUID().toString(),
        val message: String,
    ) : ChatListItem
}

/**
 * tool 名 → 中文显示名映射（tool 指示条文案）。
 */
fun toolDisplayName(toolName: String): String = when (toolName) {
    "get_user_profile" -> "查询个人资料"
    "list_recent_workouts" -> "查询最近训练"
    "get_workout_detail" -> "查询训练明细"
    "get_exercise_history" -> "查询动作历史"
    "search_exercises" -> "搜索动作库"
    "list_workout_plans" -> "查询训练计划"
    "get_next_planned_session" -> "查询下次训练"
    else -> toolName
}
