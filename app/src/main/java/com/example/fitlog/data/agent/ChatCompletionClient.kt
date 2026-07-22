package com.example.fitlog.data.agent

import com.example.fitlog.data.repository.AICompletion
import com.example.fitlog.model.ai.ChatMessage
import com.example.fitlog.model.ai.ToolDefinition

/**
 * 向 LLM 发起对话补全请求的能力抽象（agent loop 的端口）。
 *
 * 由 [com.example.fitlog.data.repository.AIChatRepository] 实现。
 * 抽接口是为了让 [AgentOrchestrator] 在单测中可注入脚本化 fake。
 */
interface ChatCompletionClient {

    /**
     * 发起一次对话补全请求。
     *
     * @param messages 完整对话上下文（含 system prompt）
     * @param tools 可供模型调用的工具声明；null 时不启用 function calling
     * @return [Result.success] 含回复消息与 finishReason；[Result.failure] 描述错误原因
     */
    suspend fun chatCompletion(
        messages: List<ChatMessage>,
        tools: List<ToolDefinition>? = null,
    ): Result<AICompletion>
}
