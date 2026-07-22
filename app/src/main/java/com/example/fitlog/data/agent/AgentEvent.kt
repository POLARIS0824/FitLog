package com.example.fitlog.data.agent

import com.example.fitlog.model.ai.ChatMessage
import com.example.fitlog.model.ai.ToolCall

/**
 * Agent 执行过程中的中间事件，用于驱动 UI 状态（tool 指示条等）。
 */
sealed interface AgentEvent {

    /**
     * assistant 消息要求执行工具，UI 应追加 tool 指示条（RUNNING）。
     */
    data class ToolCallStarted(val call: ToolCall) : AgentEvent

    /**
     * 工具执行完毕。
     *
     * @param success false 表示执行异常（错误内容已回喂模型自我纠正）
     */
    data class ToolCallFinished(val call: ToolCall, val success: Boolean) : AgentEvent

    /**
     * 达到最大工具调用轮次，循环被熔断。
     */
    data object MaxRoundsReached : AgentEvent
}

/**
 * 一轮完整 agent 对话的产出。
 *
 * @param newMessages 本轮新增的全部消息（assistant tool_calls 消息 + tool 结果消息
 * + 最终 assistant 消息），按序。调用方应整体追加进对话历史——
 * 否则下一轮对话会丢失 tool 上下文
 * @param finalReply 最终可展示的 assistant 文本消息（= newMessages 的最后一条）
 */
data class AgentTurn(
    val newMessages: List<ChatMessage>,
    val finalReply: ChatMessage,
)
