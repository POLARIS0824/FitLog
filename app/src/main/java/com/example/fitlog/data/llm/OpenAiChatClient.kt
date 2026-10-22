package com.example.fitlog.data.llm

import io.ktor.client.HttpClient

/**
 * One chat-completions request.
 *
 * [modelId] and [jsonOutput] stay per call because they differ per feature and per model, while the
 * base URL and credential are bound once by [OpenAiChatClientFactory].
 */
internal data class ChatSpec(
    val modelId: String,
    val messages: List<ChatMessage>,
    val jsonOutput: Boolean = false,
    val stream: Boolean = false,
)

internal sealed interface ChatOutcome {
    data class Success(val content: String) : ChatOutcome

    /** Only model answer text may be retained, never an HTTP error body or credentials. */
    data class Failure(val reason: AiRequestFailure, val raw: String? = null) : ChatOutcome
}

internal sealed interface AiModelsResult {
    data class Success(val modelIds: List<String>) : AiModelsResult
    data class Failure(val reason: AiRequestFailure) : AiModelsResult
}

/**
 * The only component that speaks the OpenAI-compatible protocol.
 *
 * An instance binds one base URL and one credential, so a later settings edit cannot change a
 * request that is already running. It never owns or closes the shared [HttpClient]: the client is
 * process-scoped and survives Activity recreation.
 */
internal interface OpenAiChatClient {
    suspend fun chat(spec: ChatSpec, onDelta: suspend (String) -> Unit = {}): ChatOutcome

    suspend fun models(): AiModelsResult

    /** Cheapest possible completion: proves URL, credential and model without sending feature text. */
    suspend fun ping(modelId: String): AiRequestFailure?
}

/** Binds the provider-scoped half of a request; the shared [HttpClient] is supplied once. */
internal fun interface OpenAiChatClientFactory {
    fun bind(baseUrl: String, apiKey: String): OpenAiChatClient
}

internal fun openAiChatClientFactory(http: HttpClient): OpenAiChatClientFactory =
    OpenAiChatClientFactory { baseUrl, apiKey -> OpenAiHttpChatClient(http, baseUrl, apiKey) }
