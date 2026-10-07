package com.example.fitlog.data.analysis.adapter

import com.example.fitlog.data.analysis.DiaryParseFailure
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class OpenAiProviderOperationsTest {
    @Test fun discoversMultipleIdsWithCustomPrefixAndPerRequestAuthentication() = runTest {
        val keys = mutableListOf<String?>()
        createAiHttpClient(MockEngine { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("https://gateway.example/proxy/v1/models", request.url.toString())
            keys += request.headers[HttpHeaders.Authorization]
            respond("""{"data":[{"id":"model-a","metadata":true},{"id":"model-b"},{"id":"model-a"}]}""")
        }).use { client ->
            repeat(2) { index ->
                assertEquals(AiModelsResult.Success(listOf("model-a", "model-b")),
                    fetchOpenAiModels(client, "https://gateway.example/proxy/v1/", "key-$index"))
            }
        }
        assertEquals(listOf("Bearer key-0", "Bearer key-1"), keys)
    }

    @Test fun invalidResponsesAndHttpErrorsAreTypedWithoutReturningPrivateBodiesOrRetrying() = runTest {
        listOf(HttpStatusCode.Unauthorized to DiaryParseFailure.AUTHENTICATION_ERROR,
            HttpStatusCode.TooManyRequests to DiaryParseFailure.RATE_LIMITED,
            HttpStatusCode.ServiceUnavailable to DiaryParseFailure.SERVICE_UNAVAILABLE,
            HttpStatusCode.OK to DiaryParseFailure.INVALID_RESPONSE).forEach { (status, failure) ->
            var calls = 0
            createAiHttpClient(MockEngine { calls++; respond("private-http-body", status) }).use { client ->
                assertEquals(AiModelsResult.Failure(failure), fetchOpenAiModels(client, "https://api.example", "key"))
            }
            assertEquals(1, calls)
        }
    }

    @Test fun cancellationPropagatesAndCanNeverPublishADiscoveredList() = runTest {
        val started = CompletableDeferred<Unit>()
        createAiHttpClient(MockEngine { started.complete(Unit); awaitCancellation() }).use { client ->
            val pending = async { fetchOpenAiModels(client, "https://api.example", "key") }
            started.await(); pending.cancelAndJoin()
            assertTrue(pending.isCancelled)
        }
    }

    @Test fun connectionTestSendsFixedTextAndChosenModelThroughExistingAdapter() = runTest {
        createAiHttpClient(MockEngine { request ->
            val body = request.body.toByteArray().decodeToString()
            assertTrue(body.contains("model-b"))
            assertTrue(body.contains("Connection test. No training is recorded."))
            assertEquals("Bearer key", request.headers[HttpHeaders.Authorization])
            respond("""{"choices":[{"message":{"content":"ok"},"finish_reason":"stop"}]}""")
        }).use { client ->
            assertNull(testOpenAiConnection(client, "https://api.example", "model-b", "key"))
        }
    }

    @Test fun extractionIdentityChangesWithModelEndpointAndOutputMode() {
        val first = OpenAiModelConfig("https://api.example/v1/", "model-a")
        assertEquals(first.extractorVersion(), first.copy(baseUrl = "https://api.example/v1").extractorVersion())
        assertNotEquals(first.extractorVersion(), first.copy(modelId = "model-b").extractorVersion())
        assertNotEquals(first.extractorVersion(), first.copy(baseUrl = "https://other.example/v1").extractorVersion())
        assertNotEquals(first.extractorVersion(), first.copy(jsonOutput = true).extractorVersion())
    }
}
