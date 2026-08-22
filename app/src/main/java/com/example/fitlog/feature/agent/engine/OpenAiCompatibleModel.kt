package com.example.fitlog.feature.agent.engine

import com.example.fitlog.data.remote.AIApi
import com.example.fitlog.data.remote.dto.ChatCompletionRequestDto
import com.example.fitlog.data.remote.dto.UsageDto
import com.example.fitlog.model.ai.AIProviderConfig
import com.example.fitlog.util.AiErrorMessages
import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.models.LlmResponse
import com.google.adk.kt.models.Model
import com.google.adk.kt.types.FinishReason
import com.google.adk.kt.types.UsageMetadata
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.coroutines.cancellation.CancellationException

/**
 * 把 [Model] 适配到现有 OpenAI 兼容服务商链路的实现。
 *
 * ## 职责
 *
 * 只做两件事：类型翻译（委托 [OpenAiAdapters]）与 HTTP 发送（复用 [AIApi]），
 * 让 ADK 的 agent 管线（LlmAgent → 工具执行 → 会话）无需改动即可驱动用户
 * 在 AI 设置里配置的任何 OpenAI 兼容服务商（OpenAI / Moonshot / DeepSeek /
 * SiliconFlow / Azure / Custom）。
 *
 * ## 请求装配
 *
 * - 地址与鉴权：由 [AIProviderConfig] 经
 *   `ProviderType.buildUrl` / `buildHeaders` 生成（与
 *   [com.example.fitlog.data.repository.AIChatRepository] 同款）
 * - 模型名：ADK 的 [LlmRequest.model] 指向另一个 Model 实例（name 即配置模型名），
 *   这里直接取 [config.model]，避免对内部实例做 identity 假设
 * - 参数超集容错：只序列化 OpenAI 兼容字段（model/messages/temperature/max_tokens/tools），
 *   忽略 Gemini 特有配置，任何服务商都不会收到未知字段
 *
 * ## 流式
 *
 * v1 统一按非流式处理：即使 [stream] 为 true 也单次 emit 完整 [LlmResponse]。
 * ADK 的 LlmAgent 会在每轮结束时把最终文本聚合成一条回复，UI 端无需打字机即可工作；
 * 未来如需流式，改由 [AIApi] 的 SSE 通道按 chunk 拆分 [LlmResponse.partial] 即可，
 * 调用方（LlmAgentTurn）已按 partial 语义消费。
 *
 * ## 错误语义
 *
 * 与 ADK 约定一致：**可恢复的服务商错误以 [LlmResponse.errorMessage] 表达**，
 * 不抛异常（LlmAgentTurn 会把 errorMessage 包装成模型事件）；仅 [CancellationException]
 * 向上传播。网络层异常（无网、超时、5xx）也归入 errorMessage，UI 侧给出统一文案。
 */
class OpenAiCompatibleModel(
    private val config: AIProviderConfig,
    private val aiApi: AIApi,
) : Model {

    override val name: String get() = config.model

    override fun generateContent(request: LlmRequest, stream: Boolean): Flow<LlmResponse> = flow {
        val dto = ChatCompletionRequestDto(
            model = config.model,
            messages = OpenAiAdapters.toOpenAiMessages(
                systemInstruction = request.config.systemInstruction,
                contents = request.contents,
            ),
            temperature = request.config.temperature?.toDouble(),
            maxTokens = request.config.maxOutputTokens,
            tools = OpenAiAdapters.toOpenAiTools(request.config.tools),
        )
        // stream 参数 v1 忽略（见类注释）

        try {
            val response = aiApi.chatCompletions(
                url = config.type.buildUrl(config),
                headers = config.type.buildHeaders(config.apiKey),
                request = dto,
            )
            val choice = response.choices.firstOrNull()
            if (choice == null) {
                emit(
                    LlmResponse(
                        errorMessage = "AI 服务商未返回任何回复",
                        errorCode = "EMPTY_CHOICES",
                    ),
                )
                return@flow
            }

            val message = choice.message
            emit(
                LlmResponse(
                    content = OpenAiAdapters.toAdkContent(
                        content = message.content,
                        toolCalls = message.toolCalls,
                    ),
                    usageMetadata = response.usage?.toAdkUsageMetadata(),
                    finishReason = choice.finishReason?.toAdkFinishReason(),
                ),
            )
        } catch (e: CancellationException) {
            // 取消必须向上传播，不得吞掉（ADK 依赖它中断在途请求）
            throw e
        } catch (e: Exception) {
            // 网络层/解析异常：以 errorMessage 交给 LlmAgentTurn 包装成模型事件
            emit(
                LlmResponse(
                    errorMessage = AiErrorMessages.toUserFacingMessage(e),
                    errorCode = "HTTP_ERROR",
                ),
            )
        }
    }

    private fun String.toAdkFinishReason(): FinishReason? = when (lowercase()) {
        "stop", "stop_sequence" -> FinishReason.STOP
        "length", "max_tokens" -> FinishReason.MAX_TOKENS
        "content_filter" -> FinishReason.SAFETY
        "tool_calls", "function_call" -> FinishReason.STOP
        else -> FinishReason.OTHER
    }

    private fun UsageDto.toAdkUsageMetadata(): UsageMetadata = UsageMetadata(
        promptTokenCount = promptTokens,
        candidatesTokenCount = completionTokens,
        totalTokenCount = totalTokens,
    )
}
