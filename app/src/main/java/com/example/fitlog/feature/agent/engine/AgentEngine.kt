package com.example.fitlog.feature.agent.engine

import com.example.fitlog.model.ai.ChatMessage
import com.google.adk.kt.events.Event
import kotlinx.coroutines.flow.Flow

/**
 * Agent 引擎对 UI 层的契约：ChatViewModel 面向本接口编程，便于测试替身替换。
 *
 * 实现见 [AgentEngineImpl]（Hilt 绑定于 `di/AgentEngineModule`）。
 */
interface AgentEngine {

    /**
     * 向指定会话发送一条用户消息，返回 ADK 事件流。
     *
     * @param sessionId 会话 id（调用方负责跨重启保持稳定）
     * @param text 用户输入
     * @return [Result.success] 含事件流（逐条消费：最终文本 / 工具调用 / 确认请求）；
     *         [Result.failure] 未配置服务商或引擎构建失败
     */
    suspend fun sendMessage(sessionId: String, text: String): Result<Flow<Event>>

    /**
     * 回复一次工具确认请求（弹窗同意/拒绝后调用）。
     *
     * @param sessionId 会话 id（与 [sendMessage] 同一命名空间）
     * @param confirmationCallId 确认请求的合成调用 id（来自事件流）
     * @param confirmed 用户是否同意执行
     * @return [Result.success] 含后续事件流（工具执行结果 + 模型最终回复）；
     *         [Result.failure] 未配置服务商或引擎构建失败
     */
    suspend fun respondToConfirmation(
        sessionId: String,
        confirmationCallId: String,
        confirmed: Boolean,
    ): Result<Flow<Event>>

    /**
     * 删除指定会话的全部持久化历史（会话重置阀）。
     *
     * 协议层一旦落进坏历史（如悬空 tool_call），后续每条消息都会被服务商拒绝，
     * 这是唯一的自愈手段；也用于用户主动"清空对话"。
     *
     * @param sessionId 会话 id
     * @return 成功；会话不存在或删除失败时 [Result.failure]
     */
    suspend fun clearSession(sessionId: String): Result<Unit>

    /**
     * 回放指定会话的持久化历史，映射为 UI 消息列表（进程重启后恢复聊天界面）。
     *
     * 用户消息与模型最终文本回复按原顺序映射；工具调用/确认等中间事件折叠为
     * 占位文本消息（或不产生消息），保证模型上下文与 UI 展示一致。
     *
     * @param sessionId 会话 id
     * @return 按时间顺序的 UI 消息列表；会话不存在返回空列表
     */
    suspend fun replayHistory(sessionId: String): List<ChatMessage>
}
