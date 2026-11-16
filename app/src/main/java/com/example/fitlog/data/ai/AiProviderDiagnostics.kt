package com.example.fitlog.data.ai

import com.example.fitlog.data.ai.transport.AiModelsResult
import com.example.fitlog.data.ai.transport.AiRequestFailure
import com.example.fitlog.data.ai.transport.OpenAiChatClientFactory
import kotlinx.coroutines.*

/** Provider diagnostics share endpoint binding and model-id normalization in the data layer. */
internal interface AiOperations {
    suspend fun models(baseUrl: String, apiKey: String): AiModelsResult
    suspend fun test(baseUrl: String, model: String, apiKey: String): AiRequestFailure?
}

internal class AiProviderDiagnostics(private val clients: OpenAiChatClientFactory,
    private val computationDispatcher: CoroutineDispatcher = Dispatchers.Default) : AiOperations {
    override suspend fun models(baseUrl: String, apiKey: String): AiModelsResult = withContext(computationDispatcher) {
        when (val result = clients.bind(baseUrl.trim(), apiKey.trim()).models()) {
            is AiModelsResult.Success -> AiModelsResult.Success(result.modelIds.map(String::trim).filter(String::isNotEmpty).distinct().sorted())
            is AiModelsResult.Failure -> result
        }
    }
    override suspend fun test(baseUrl: String, model: String, apiKey: String) =
        clients.bind(baseUrl.trim(), apiKey.trim()).ping(model.trim())
}
