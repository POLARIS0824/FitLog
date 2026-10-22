package com.example.fitlog.data.llm

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Wire types of the OpenAI chat-completions protocol.
 *
 * They stay inside the protocol layer: features speak [ChatSpec] and [ChatOutcome] instead, so a
 * provider's JSON shape never reaches domain code.
 */
@Serializable
internal data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val stream: Boolean = false,
    @SerialName("response_format") val responseFormat: ResponseFormat? = null,
)

@Serializable
internal data class ChatMessage(val role: String, val content: String) {
    companion object {
        fun system(content: String) = ChatMessage("system", content)
        fun user(content: String) = ChatMessage("user", content)
    }
}

@Serializable
internal data class ResponseFormat(val type: String)

@Serializable
internal data class ChatResponse(val choices: List<ChatChoice>)

@Serializable
internal data class ChatChoice(
    val message: AssistantMessage? = null,
    @SerialName("finish_reason") val finishReason: String? = null,
)

@Serializable
internal data class AssistantMessage(val content: String? = null, val refusal: String? = null)

/** One `data:` event of a `stream = true` completion. */
@Serializable
internal data class ChatChunk(val choices: List<ChatChunkChoice>)

@Serializable
internal data class ChatChunkChoice(
    val index: Int = 0,
    val delta: ChatDelta = ChatDelta(),
    @SerialName("finish_reason") val finishReason: String? = null,
)

@Serializable
internal data class ChatDelta(val content: String? = null, val refusal: String? = null)

@Serializable
internal data class ModelList(val data: List<ModelId>)

@Serializable
internal data class ModelId(val id: String)

internal const val FINISH_STOP = "stop"
internal const val FINISH_LENGTH = "length"
internal const val FINISH_CONTENT_FILTER = "content_filter"

internal val openAiWireJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}

internal val openAiModelListJson = Json { ignoreUnknownKeys = true }
