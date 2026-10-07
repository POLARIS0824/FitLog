package com.example.fitlog.data.analysis.adapter

import com.example.fitlog.data.analysis.DiaryParseFailure
import io.ktor.client.HttpClient
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

private val modelListJson = Json { ignoreUnknownKeys = true }

internal sealed interface AiModelsResult {
    data class Success(val modelIds: List<String>) : AiModelsResult
    data class Failure(val reason: DiaryParseFailure) : AiModelsResult
}

/** Explicit discovery only. Never persists credentials, changes selection or logs response bodies. */
internal suspend fun fetchOpenAiModels(client: HttpClient, baseUrl: String, apiKey: String): AiModelsResult {
    val endpoint = openAiEndpoint(baseUrl, "models")
    require(apiKey.isNotEmpty() && apiKey.all { it in '!'..'~' })
    return try {
        val response = client.get(endpoint) { bearerAuth(apiKey) }
        if (response.status.value !in 200..299) {
            AiModelsResult.Failure(when (response.status.value) {
                401 -> DiaryParseFailure.AUTHENTICATION_ERROR
                403 -> DiaryParseFailure.PERMISSION_DENIED
                429 -> DiaryParseFailure.RATE_LIMITED
                in 500..599 -> DiaryParseFailure.SERVICE_UNAVAILABLE
                else -> DiaryParseFailure.REQUEST_REJECTED
            })
        } else {
            val models = try { modelListJson.decodeFromString<ModelList>(response.bodyAsText()) }
                catch (_: SerializationException) { return AiModelsResult.Failure(DiaryParseFailure.INVALID_RESPONSE) }
            if (models.data.any { it.id.isBlank() || it.id != it.id.trim() }) {
                AiModelsResult.Failure(DiaryParseFailure.INVALID_RESPONSE)
            } else AiModelsResult.Success(models.data.map { it.id }.distinct())
        }
    } catch (e: CancellationException) { throw e
    } catch (_: HttpRequestTimeoutException) { AiModelsResult.Failure(DiaryParseFailure.TIMEOUT)
    } catch (_: ConnectTimeoutException) { AiModelsResult.Failure(DiaryParseFailure.TIMEOUT)
    } catch (_: SocketTimeoutException) { AiModelsResult.Failure(DiaryParseFailure.TIMEOUT)
    } catch (_: IOException) { AiModelsResult.Failure(DiaryParseFailure.NETWORK_ERROR) }
}

/** Exercises the same non-streaming completion path with fixed, non-diary test data. */
internal suspend fun testOpenAiConnection(client: HttpClient, baseUrl: String, modelId: String, apiKey: String): DiaryParseFailure? =
    when (val response = OpenAiDiaryModelSource(client, OpenAiModelConfig(baseUrl, modelId), apiKey)
        .request("Connection test. No training is recorded.")) {
        is DiaryModelResponse.Json -> null
        is DiaryModelResponse.Failure -> response.reason
    }

@Serializable private data class ModelList(val data: List<ModelId>)
@Serializable private data class ModelId(val id: String)
