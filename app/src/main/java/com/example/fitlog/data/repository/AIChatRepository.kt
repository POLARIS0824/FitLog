package com.example.fitlog.data.repository

import com.example.fitlog.data.agent.ChatCompletionClient
import com.example.fitlog.data.mapper.toDto
import com.example.fitlog.data.mapper.toModel
import com.example.fitlog.data.remote.AIApi
import com.example.fitlog.data.remote.dto.ChatCompletionRequestDto
import com.example.fitlog.model.ai.AIProviderConfig
import com.example.fitlog.model.ai.ChatMessage
import com.example.fitlog.model.ai.ToolDefinition
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * [chatCompletion] 的返回：消息 + 结束原因，供 agent loop 判定是否继续执行 tool。
 *
 * @param message AI 回复的消息（可能携带 toolCalls）
 * @param finishReason 生成结束原因（如 "stop"、"tool_calls"、"length"），原样透传
 */
data class AICompletion(
    val message: ChatMessage,
    val finishReason: String?,
)

/**
 * AI 对话网络仓库。
 *
 * ## 职责
 *
 * 本仓库是"发消息给 AI"这一操作的唯一入口，负责把各个零件装配成一次完整的请求：
 *
 * 1. 从 [AIProviderConfigRepository] 获取当前激活的服务商配置（URL、API Key、模型）
 * 2. 将 [ChatMessage] 领域模型转换为 [MessageDto] 网络 DTO
 * 3. 组装 HTTP 请求（URL + Headers + Body）
 * 4. 通过 [AIApi] 发送请求
 * 5. 将响应 DTO 转回 [ChatMessage] 领域模型返回
 *
 * ## 错误处理策略
 *
 * 网络请求的不确定性很多（无网络、服务器 500、API Key 过期等），
 * 因此使用 [Result] 包裹返回值，让调用方（ViewModel）自行决定如何处理错误：
 * - [Result.success] → AI 回复了一条消息
 * - [Result.failure] → 出错，exception.message 包含用户可读的错误描述
 *
 * ## 数据流
 *
 * ```
 * ViewModel → List<ChatMessage> (对话历史) → chat()
 *   → getActiveProvider() → 拿到 config
 *   → messages.toDto() → 转 DTO
 *   → config.type.buildUrl() + buildHeaders() → 组装请求
 *   → AIApi.chatCompletions() → 网络请求
 *   → 响应 DTO → .toModel() → ChatMessage → Result.success
 * ```
 */
class AIChatRepository @Inject constructor(
    private val aiApi: AIApi,
    private val providerConfigRepo: AIProviderConfigRepository,
) : ChatCompletionClient {

    /**
     * 发送对话消息并获取 AI 回复。
     *
     * @param messages 完整的对话上下文，包括 system prompt、历史消息和新用户消息。
     *                 调用方负责维护消息列表的顺序和内容。
     * @return [Result.success] 包含 AI 的回复消息；[Result.failure] 描述错误原因
     */
    suspend fun chat(messages: List<ChatMessage>): Result<ChatMessage> {
        return chatCompletion(messages, null).map { it.message }
    }

    /**
     * 使用指定配置发送对话消息并获取 AI 回复。
     *
     * 与 [chat] 不同，配置由调用方直接传入——设置页"测试连接"等场景
     * 可以用表单中尚未保存的凭据发请求，不需要先落库或激活。
     *
     * @param config 用于鉴权和寻址的配置
     * @param messages 完整的对话上下文
     * @return [Result.success] 包含 AI 的回复消息；[Result.failure] 描述错误原因
     */
    suspend fun chat(config: AIProviderConfig, messages: List<ChatMessage>): Result<ChatMessage> {
        return chatCompletion(config, messages).map { it.message }
    }

    /**
     * 支持 function calling 的对话请求（取当前激活配置）。
     *
     * @param messages 完整的对话上下文
     * @param tools 可供模型调用的工具声明；null 时不启用 function calling
     * @return [Result.success] 包含回复消息与 finishReason；[Result.failure] 描述错误原因
     */
    override suspend fun chatCompletion(
        messages: List<ChatMessage>,
        tools: List<ToolDefinition>?,
    ): Result<AICompletion> {
        // ── 步骤 1: 获取当前激活的 AI 配置 ──
        val config = providerConfigRepo.activeProvider.first()
            ?: return Result.failure(
                IllegalStateException("未设置 AI 服务商，请先在设置中配置 API Key")
            )
        return chatCompletion(config, messages, tools)
    }

    /**
     * 使用指定配置、支持 function calling 的对话请求。
     *
     * 与 [chatCompletion] 的单参版本不同，配置由调用方直接传入。
     *
     * @param config 用于鉴权和寻址的配置
     * @param messages 完整的对话上下文
     * @param tools 可供模型调用的工具声明；null 时不启用 function calling
     * @return [Result.success] 包含回复消息与 finishReason；[Result.failure] 描述错误原因
     */
    suspend fun chatCompletion(
        config: AIProviderConfig,
        messages: List<ChatMessage>,
        tools: List<ToolDefinition>? = null,
    ): Result<AICompletion> {
        return try {
            // ── 步骤 2: 构建请求参数 ──
            val url = config.type.buildUrl(config)
            val headers = config.type.buildHeaders(config.apiKey)
            val body = ChatCompletionRequestDto(
                model = config.model,
                messages = messages.map { it.toDto() },
                tools = tools?.map { it.toDto() },
                // "auto" 由模型自行决定是否调用工具
                toolChoice = tools?.let { "auto" },
            )

            // ── 步骤 3: 发送网络请求 ──
            val response = aiApi.chatCompletions(
                url = url,
                headers = headers,
                request = body,
            )

            // ── 步骤 4: 提取第一条回复 ──
            val choice = response.choices.firstOrNull()
                ?: return Result.failure(
                    IllegalStateException("AI 未返回任何回复")
                )

            // ── 步骤 5: DTO 转领域模型并返回（保留 finishReason 供 agent loop 判定） ──
            Result.success(AICompletion(choice.message.toModel(), choice.finishReason))

        } catch (e: Exception) {
            // 网络异常、超时、JSON 解析失败等，统一包装
            Result.failure(e)
        }
    }

    /**
     * 连通性测试：用指定配置发一条最小消息，验证 URL + 凭据 + 模型全链路可用。
     *
     * @param config 待验证的配置（不需要已保存）
     * @return [Result.success] 表示全链路可用；[Result.failure] 描述失败原因
     */
    suspend fun testConnection(config: AIProviderConfig): Result<ChatMessage> {
        return chat(config, listOf(ChatMessage(role = "user", content = "Hi")))
    }

    /**
     * 拉取指定配置下的可用模型列表。
     *
     * 与 [chat] 不同，这里直接使用调用方传入的 [config]——设置页表单中
     * 尚未保存的凭据也可以拿来拉取，不需要先落库。
     *
     * @param config 用于鉴权和寻址的配置（不需要已保存）
     * @return [Result.success] 包含模型 id 列表；[Result.failure] 描述错误原因
     */
    suspend fun fetchModels(config: AIProviderConfig): Result<List<String>> {
        return try {
            val url = config.type.buildModelsUrl(config)
            val headers = config.type.buildHeaders(config.apiKey)
            Result.success(aiApi.models(url, headers).data.map { it.id })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
