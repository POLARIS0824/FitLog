package com.example.fitlog.data.llm

import io.ktor.client.HttpClient
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.timeout
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import io.ktor.utils.io.readLine
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString

private const val SSE_DATA_PREFIX = "data:"
private const val SSE_DONE = "[DONE]"
private const val PING_TEXT = "ping"

/**
 * The single HTTP call site for every OpenAI-compatible feature.
 *
 * Streaming is read from the raw response channel rather than through the Ktor SSE plugin so the
 * status code stays available for explicit mapping and the engine stays replaceable in tests.
 */
internal class OpenAiHttpChatClient(
    private val http: HttpClient,
    baseUrl: String,
    private val apiKey: String,
) : OpenAiChatClient {

    private val chatEndpoint = openAiEndpoint(baseUrl, "chat/completions")
    private val modelsEndpoint = openAiEndpoint(baseUrl, "models")

    init {
        require(apiKey.isNotEmpty() && apiKey.all { it in '!'..'~' }) {
            "A valid API key is required"
        }
    }

    override suspend fun chat(spec: ChatSpec, onDelta: suspend (String) -> Unit): ChatOutcome {
        require(spec.modelId.isNotBlank()) { "A model ID is required" }
        val body = ChatRequest(
            model = spec.modelId,
            messages = spec.messages,
            stream = spec.stream,
            responseFormat = if (spec.jsonOutput) ResponseFormat("json_object") else null,
        )
        return try {
            if (spec.stream) streamChat(body, onDelta) else complete(body)
        } catch (e: CancellationException) {
            // Cancellation is control flow; it does not become a failed model answer.
            throw e
        } catch (_: HttpRequestTimeoutException) {
            ChatOutcome.Failure(AiRequestFailure.TIMEOUT)
        } catch (_: ConnectTimeoutException) {
            ChatOutcome.Failure(AiRequestFailure.TIMEOUT)
        } catch (_: SocketTimeoutException) {
            ChatOutcome.Failure(AiRequestFailure.TIMEOUT)
        } catch (_: IOException) {
            ChatOutcome.Failure(AiRequestFailure.NETWORK_ERROR)
        }
    }

    override suspend fun models(): AiModelsResult = try {
        val response = http.get(modelsEndpoint) { bearerAuth(apiKey) }
        if (response.status.value !in 200..299) {
            AiModelsResult.Failure(statusFailure(response.status.value))
        } else {
            val models = try {
                openAiModelListJson.decodeFromString<ModelList>(response.bodyAsText())
            } catch (_: SerializationException) {
                return AiModelsResult.Failure(AiRequestFailure.INVALID_RESPONSE)
            }
            if (models.data.any { it.id.isBlank() || it.id != it.id.trim() }) {
                AiModelsResult.Failure(AiRequestFailure.INVALID_RESPONSE)
            } else {
                AiModelsResult.Success(models.data.map { it.id }.distinct())
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: HttpRequestTimeoutException) {
        AiModelsResult.Failure(AiRequestFailure.TIMEOUT)
    } catch (_: ConnectTimeoutException) {
        AiModelsResult.Failure(AiRequestFailure.TIMEOUT)
    } catch (_: SocketTimeoutException) {
        AiModelsResult.Failure(AiRequestFailure.TIMEOUT)
    } catch (_: IOException) {
        AiModelsResult.Failure(AiRequestFailure.NETWORK_ERROR)
    }

    override suspend fun ping(modelId: String): AiRequestFailure? =
        when (val outcome = chat(ChatSpec(modelId = modelId, messages = listOf(ChatMessage.user(PING_TEXT))))) {
            is ChatOutcome.Success -> null
            is ChatOutcome.Failure -> outcome.reason
        }

    private suspend fun complete(body: ChatRequest): ChatOutcome {
        val response = http.post(chatEndpoint) {
            bearerAuth(apiKey)
            setBody(TextContent(openAiWireJson.encodeToString(body), ContentType.Application.Json))
        }
        if (response.status.value !in 200..299) {
            return ChatOutcome.Failure(statusFailure(response.status.value))
        }
        return decodeAnswer(response.bodyAsText())
    }

    private suspend fun streamChat(
        body: ChatRequest,
        onDelta: suspend (String) -> Unit,
    ): ChatOutcome {
        val response = http.post(chatEndpoint) {
            bearerAuth(apiKey)
            setBody(TextContent(openAiWireJson.encodeToString(body), ContentType.Application.Json))
            // A streamed answer is bounded by socket inactivity, not by total request duration.
            // Long.MAX_VALUE disables Ktor's total-request timeout for this call only.
            timeout { requestTimeoutMillis = Long.MAX_VALUE }
        }
        if (response.status.value !in 200..299) {
            return ChatOutcome.Failure(statusFailure(response.status.value))
        }

        val channel = response.bodyAsChannel()
        val answer = StringBuilder()
        var finishReason: String? = null
        var refusal: String? = null
        while (true) {
            val line = channel.readLine() ?: break
            if (!line.startsWith(SSE_DATA_PREFIX)) continue
            val payload = line.removePrefix(SSE_DATA_PREFIX).trim()
            if (payload.isEmpty()) continue
            if (payload == SSE_DONE) break
            val chunk = try {
                openAiWireJson.decodeFromString<ChatChunk>(payload)
            } catch (_: SerializationException) {
                // Providers send keep-alive and vendor-specific events on the same stream.
                continue
            }
            val choice = chunk.choices.firstOrNull() ?: continue
            choice.delta.refusal?.takeIf { it.isNotBlank() }?.let { refusal = it }
            choice.delta.content?.takeIf { it.isNotEmpty() }?.let {
                onDelta(it)
                answer.append(it)
            }
            choice.finishReason?.let { finishReason = it }
        }

        val text = answer.toString()
        return when {
            refusal != null || finishReason == FINISH_CONTENT_FILTER ->
                ChatOutcome.Failure(AiRequestFailure.MODEL_REFUSAL, text.ifEmpty { refusal })
            finishReason == FINISH_LENGTH -> ChatOutcome.Failure(AiRequestFailure.TRUNCATED_RESPONSE, text)
            // A stream may end on [DONE] without a final finish_reason chunk; that is still complete.
            finishReason != null && finishReason != FINISH_STOP ->
                ChatOutcome.Failure(AiRequestFailure.INVALID_RESPONSE, text)
            text.isBlank() -> ChatOutcome.Failure(AiRequestFailure.EMPTY_RESPONSE, text)
            else -> ChatOutcome.Success(text)
        }
    }

    private fun decodeAnswer(body: String): ChatOutcome {
        if (body.isBlank()) return ChatOutcome.Failure(AiRequestFailure.EMPTY_RESPONSE)

        val completion = try {
            openAiWireJson.decodeFromString<ChatResponse>(body)
        } catch (_: SerializationException) {
            return ChatOutcome.Failure(AiRequestFailure.INVALID_RESPONSE)
        }

        // We requested one answer, no tools and no streaming. Never guess a missing answer.
        val choice = completion.choices.singleOrNull()
            ?: return ChatOutcome.Failure(AiRequestFailure.INVALID_RESPONSE)
        val message = choice.message
            ?: return ChatOutcome.Failure(AiRequestFailure.INVALID_RESPONSE)

        if (!message.refusal.isNullOrBlank() || choice.finishReason == FINISH_CONTENT_FILTER) {
            return ChatOutcome.Failure(
                AiRequestFailure.MODEL_REFUSAL,
                message.content ?: message.refusal,
            )
        }
        if (choice.finishReason == FINISH_LENGTH) {
            return ChatOutcome.Failure(AiRequestFailure.TRUNCATED_RESPONSE, message.content)
        }
        if (choice.finishReason != FINISH_STOP) {
            return ChatOutcome.Failure(AiRequestFailure.INVALID_RESPONSE, message.content)
        }

        val answer = message.content
        if (answer.isNullOrBlank()) return ChatOutcome.Failure(AiRequestFailure.EMPTY_RESPONSE, answer)

        // Preserve exact model text. The feature judges the content.
        return ChatOutcome.Success(answer)
    }

    private fun statusFailure(code: Int): AiRequestFailure = when (code) {
        401 -> AiRequestFailure.AUTHENTICATION_ERROR
        403 -> AiRequestFailure.PERMISSION_DENIED
        429 -> AiRequestFailure.RATE_LIMITED
        in 500..599 -> AiRequestFailure.SERVICE_UNAVAILABLE
        else -> AiRequestFailure.REQUEST_REJECTED
    }
}
