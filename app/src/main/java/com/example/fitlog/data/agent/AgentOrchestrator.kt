package com.example.fitlog.data.agent

import com.example.fitlog.model.ai.ChatMessage
import com.example.fitlog.model.ai.ChatRole
import com.example.fitlog.model.ai.ToolCall
import com.example.fitlog.model.ai.toDefinition
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Agent 对话编排器：驱动「请求 LLM → 执行 tool → 回喂结果 → 重新请求」的循环，
 * 直到模型给出最终文本回复。
 *
 * 设计要点：
 * - 以「assistant 消息 toolCalls 非空」为工具调用判定（不只看 finishReason），
 * 兼容部分 provider finish_reason 不准的情况
 * - 单个 tool 失败（未知工具、参数非法 JSON、执行异常）统一转为 {"error": ...}
 * 的 tool 消息回喂模型，由模型自我纠正，代码不写死重试
 * - [MAX_TOOL_ROUNDS] 硬熔断防 tool 死循环
 */
@Singleton
class AgentOrchestrator @Inject constructor(
    private val chatClient: ChatCompletionClient,
    private val toolRegistry: AgentToolRegistry,
    private val promptBuilder: AgentPromptBuilder,
    @param:Named("ai") private val json: Json,
) {

    private companion object {
        /** 单轮对话允许的最大 LLM 请求次数（防 tool 死循环的硬熔断） */
        const val MAX_TOOL_ROUNDS = 5
    }

    /**
     * 执行一轮完整的 agent 对话。
     *
     * @param conversation 不含 system prompt 的对话历史（调用方已追加本轮用户消息）
     * @param onEvent 中间事件回调（tool 开始/结束/熔断），驱动 UI 指示条
     * @return [Result.success] 包含本轮全部新增消息与最终回复；
     * [Result.failure] 为网络错误等不可恢复失败
     */
    suspend fun run(
        conversation: List<ChatMessage>,
        onEvent: suspend (AgentEvent) -> Unit,
    ): Result<AgentTurn> {
        val systemPrompt = promptBuilder.build()
        val messages = (listOf(systemPrompt) + conversation).toMutableList()
        val newMessages = mutableListOf<ChatMessage>()
        val tools = toolRegistry.all().map { it.toDefinition() }

        repeat(MAX_TOOL_ROUNDS) {
            val completion = chatClient.chatCompletion(messages, tools)
                .getOrElse { return Result.failure(it) }
            val assistant = completion.message
            messages += assistant
            newMessages += assistant

            if (assistant.toolCalls.isEmpty()) {
                // 模型给出最终文本回复，循环正常结束
                return Result.success(AgentTurn(newMessages.toList(), assistant))
            }

            // 逐个串行执行 tool（M1：顺序稳定、事件简单；M2/M3 可改并发）
            for (call in assistant.toolCalls) {
                onEvent(AgentEvent.ToolCallStarted(call))
                val (toolMessage, success) = executeCall(call)
                onEvent(AgentEvent.ToolCallFinished(call, success))
                messages += toolMessage
                newMessages += toolMessage
            }
            // 带着 tool 结果重新请求 LLM
        }

        // 硬熔断：不让 loop 裸失败，给用户可读回复
        onEvent(AgentEvent.MaxRoundsReached)
        val fallback = ChatMessage(
            role = ChatRole.ASSISTANT,
            content = "这个问题需要查询的次数太多了，我先到这里。你可以换个问法，或者把问题拆小一点再问我。",
        )
        newMessages += fallback
        return Result.success(AgentTurn(newMessages.toList(), fallback))
    }

    /**
     * 执行单次工具调用。
     *
     * 任何失败（未知工具、参数非法 JSON、执行异常）都转为 {"error": ...} 的
     * tool 消息回喂模型自我纠正；[CancellationException] 必须上抛不被吞掉。
     *
     * @return tool 结果消息 与 是否执行成功（供 UI 指示条状态）
     */
    private suspend fun executeCall(call: ToolCall): Pair<ChatMessage, Boolean> {
        val tool = toolRegistry.find(call.name)
        val (resultJson, success) = when {
            tool == null -> """{"error": "unknown tool: ${call.name}"}""" to false
            else -> try {
                val args = json.parseToJsonElement(call.argumentsJson).jsonObject
                tool.execute(args) to true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = e.message?.replace("\"", "'") ?: "tool execution failed"
                """{"error": "$message"}""" to false
            }
        }
        return ChatMessage(
            role = ChatRole.TOOL,
            content = resultJson,
            toolCallId = call.id,
            name = call.name,
        ) to success
    }
}
