package com.example.fitlog.data.analysis.adapter

import com.example.fitlog.data.analysis.DiaryParseFailure
import io.ktor.client.HttpClient
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.URLBuilder
import io.ktor.http.encodedPath
import io.ktor.http.content.TextContent
import java.io.IOException
import java.net.URI
import java.net.URISyntaxException
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** One immutable selection. Credentials deliberately live outside this printable value. */
internal data class OpenAiModelConfig(
    val baseUrl: String,
    val modelId: String,
    val jsonOutput: Boolean = false,
)

/**
 * OpenAI-compatible transport at the existing model seam. Does not own or close the shared client.
 * One instance captures one selection and credential, so later settings edits cannot change it.
 */
internal class OpenAiDiaryModelSource(
    private val client: HttpClient,
    private val config: OpenAiModelConfig,
    private val apiKey: String,
) : DiaryModelSource {
    private val endpoint = openAiEndpoint(config.baseUrl, "chat/completions")

    init {
        require(config.modelId.isNotBlank()) { "A model ID is required" }
        require(apiKey.isNotEmpty() && apiKey.all { it in '!'..'~' }) {
            "A valid API key is required"
        }
    }

    override suspend fun request(text: String): DiaryModelResponse = try {
        val body = ChatRequest(
            model = config.modelId,
            messages = listOf(
                ChatMessage("system", DiaryExtractionPrompt.instructions),
                ChatMessage("user", text),
            ),
            responseFormat = if (config.jsonOutput) ResponseFormat("json_object") else null,
        )
        val response = client.post(endpoint) {
            bearerAuth(apiKey)
            setBody(TextContent(wireJson.encodeToString(body), ContentType.Application.Json))
        }
        when (response.status.value) {
            in 200..299 -> decodeAnswer(response.bodyAsText())
            401 -> failure(DiaryParseFailure.AUTHENTICATION_ERROR)
            403 -> failure(DiaryParseFailure.PERMISSION_DENIED)
            429 -> failure(DiaryParseFailure.RATE_LIMITED)
            in 500..599 -> failure(DiaryParseFailure.SERVICE_UNAVAILABLE)
            else -> failure(DiaryParseFailure.REQUEST_REJECTED)
        }
    } catch (e: CancellationException) {
        // Cancellation is control flow; it does not become a failed model answer.
        throw e
    } catch (_: HttpRequestTimeoutException) {
        failure(DiaryParseFailure.TIMEOUT)
    } catch (_: ConnectTimeoutException) {
        failure(DiaryParseFailure.TIMEOUT)
    } catch (_: SocketTimeoutException) {
        failure(DiaryParseFailure.TIMEOUT)
    } catch (_: IOException) {
        failure(DiaryParseFailure.NETWORK_ERROR)
    }

    private fun decodeAnswer(body: String): DiaryModelResponse {
        if (body.isBlank()) return failure(DiaryParseFailure.EMPTY_RESPONSE)
        val completion = try {
            wireJson.decodeFromString<ChatResponse>(body)
        } catch (_: SerializationException) {
            return failure(DiaryParseFailure.INVALID_RESPONSE)
        }
        // We requested one answer, no tools and no streaming. Never guess a missing answer.
        val choice = completion.choices.singleOrNull()
            ?: return failure(DiaryParseFailure.INVALID_RESPONSE)
        val message = choice.message ?: return failure(DiaryParseFailure.INVALID_RESPONSE)
        if (!message.refusal.isNullOrBlank() || choice.finishReason == "content_filter") {
            return failure(DiaryParseFailure.MODEL_REFUSAL, message.content ?: message.refusal)
        }
        if (choice.finishReason == "length") {
            return failure(DiaryParseFailure.TRUNCATED_RESPONSE, message.content)
        }
        if (choice.finishReason != "stop") {
            return failure(DiaryParseFailure.INVALID_RESPONSE, message.content)
        }
        val answer = message.content
        if (answer.isNullOrBlank()) return failure(DiaryParseFailure.EMPTY_RESPONSE, answer)
        // Preserve exact model text. The existing codec and validator judge the diary JSON.
        return DiaryModelResponse.Json(answer)
    }

    private fun failure(reason: DiaryParseFailure, rawText: String? = null) =
        DiaryModelResponse.Failure(reason, rawText)

    private companion object {
        val wireJson = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = false
        }
    }
}

/** Shared validation and path handling for completion and model discovery requests. */
internal fun openAiEndpoint(baseUrl: String, path: String): String {
    val uri = try { URI(baseUrl.trim()) } catch (_: URISyntaxException) {
        throw IllegalArgumentException("Invalid AI base URL")
    }
    require(uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank() &&
        uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null) {
        "AI base URL must be HTTPS without credentials, query or fragment"
    }
    return URLBuilder(uri.toASCIIString()).apply {
        encodedPath = encodedPath.trimEnd('/') + "/" + path
    }.buildString()
}

/** Only values affecting extraction participate; credentials and connection display names never do. */
internal fun OpenAiModelConfig.extractorVersion(): String {
    val identity = Json.encodeToString(ExtractionIdentity(openAiEndpoint(baseUrl, "chat/completions"),
        modelId, jsonOutput, DiaryExtractionPrompt.VERSION, DiaryCandidateValidator.VERSION))
    return "openai-compatible:" + MessageDigest.getInstance("SHA-256")
        .digest(identity.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}

@Serializable
private data class ExtractionIdentity(val endpoint: String, val model: String, val jsonOutput: Boolean,
    val prompt: String, val postprocessor: String)

// Wire types remain private to this adapter; callers only see DiaryModelResponse.
@Serializable
private data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val stream: Boolean = false,
    @SerialName("response_format") val responseFormat: ResponseFormat? = null,
)

@Serializable
private data class ChatMessage(val role: String, val content: String)

@Serializable
private data class ResponseFormat(val type: String)

@Serializable
private data class ChatResponse(val choices: List<ChatChoice>)

@Serializable
private data class ChatChoice(
    val message: AssistantMessage? = null,
    @SerialName("finish_reason") val finishReason: String? = null,
)

@Serializable
private data class AssistantMessage(val content: String? = null, val refusal: String? = null)
