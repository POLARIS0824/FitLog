package com.example.fitlog.benchmark

import com.example.fitlog.data.analysis.adapter.createAiHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.plugin
import io.ktor.client.request.HttpRequestPipeline
import io.ktor.client.call.save
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.http.content.TextContent
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import io.ktor.util.AttributeKey

private val observationAttribute = AttributeKey<CaseObservation>("benchmark-observation")

internal class RequestGate(concurrency: Int, private val budget: RequestBudget) {
    private val permits = Semaphore(concurrency)
    private val stopped = AtomicBoolean()
    private val inflight = AtomicInteger()
    val maximumInflight = AtomicInteger()
    @Volatile var reason: String? = null; private set
    fun stop(reason: String) { this.reason = reason; stopped.set(true) }
    suspend fun <T> dispatch(observation: CaseObservation, action: suspend () -> T): T {
      val queued = System.nanoTime()
      return permits.withPermit {
        currentCoroutineContext().ensureActive()
        if (stopped.get()) throw BenchmarkStopped(reason ?: "STOPPED")
        val requestNumber = try { budget.claim() } catch (e: BenchmarkStopped) { stop(e.reason); throw e }
        observation.queueMs = (System.nanoTime() - queued) / 1e6
        observation.httpCalls++
        val active = inflight.incrementAndGet()
        maximumInflight.updateAndGet { maxOf(it, active) }
        observation.emit("http_start", "inflight" to active, "request_number" to requestNumber)
        val start = System.nanoTime()
        try { action() } finally {
            observation.httpMs += (System.nanoTime() - start) / 1e6
            inflight.decrementAndGet()
            observation.emit("http_end", "http_ms" to observation.httpMs)
        }
      }
    }
}

/** Quota admission precedes Ktor's request timer; sends include the complete response body. */
internal fun benchmarkClient(spec: BenchmarkSpec, cases: Map<String, BenchmarkCase>): HttpClient {
    val client = if (spec.mode == "live") createAiHttpClient() else createAiHttpClient(MockEngine { request ->
        val observation = request.attributes[observationAttribute]
        val case = cases.getValue(observation.caseId)
        delay(case.delayMs)
        if (case.fault == "timeout") throw io.ktor.client.plugins.HttpRequestTimeoutException("https://benchmark.invalid", 120_000)
        if (case.fault == "network") throw java.io.IOException("Injected network failure")
        val message = buildJsonObject {
            put("content", case.responseJson)
            if (case.fault == "refusal") put("refusal", "fixture refusal")
        }
        val envelope = buildJsonObject {
            putJsonArray("choices") { add(buildJsonObject {
                put("message", message)
                put("finish_reason", if (case.fault == "truncated") "length" else case.finishReason)
            }) }
        }
        respond(envelope.toString(), HttpStatusCode.fromValue(case.httpStatus),
            headersOf(HttpHeaders.ContentType, "application/json"))
    })
    // HttpTimeout starts in Send. Waiting in HttpSend would consume a later request's
    // timeout before it reaches the engine, especially in serial whole-folder runs.
    client.requestPipeline.intercept(HttpRequestPipeline.Before) {
        val observation = requireNotNull(currentCoroutineContext()[CaseObservation])
        observation.gate.dispatch(observation) {
            try { proceed() }
            catch (e: CancellationException) {
                // Ktor may wrap the timeout in nested cancellation exceptions. Stop admission
                // before releasing the permit, while preserving ordinary user cancellation.
                if (generateSequence<Throwable>(e) { it.cause }.any {
                        it is io.ktor.client.plugins.HttpRequestTimeoutException
                    }) observation.gate.stop("TIMEOUT")
                throw e
            }
            catch (e: io.ktor.client.plugins.HttpRequestTimeoutException) {
                observation.gate.stop("TIMEOUT")
                throw e
            }
        }
    }
    client.plugin(HttpSend).intercept { request ->
        val observation = requireNotNull(currentCoroutineContext()[CaseObservation])
        request.attributes.put(observationAttribute, observation)
        observation.requestHash = sha256((request.body as TextContent).text.toByteArray(Charsets.UTF_8))
        val call = try { execute(request).save() }
        catch (e: CancellationException) { throw e }
        catch (e: io.ktor.client.plugins.HttpRequestTimeoutException) { observation.gate.stop("TIMEOUT"); throw e }
        catch (e: java.io.IOException) {
            observation.emit("transport_error", "exception_class" to e.javaClass.name,
                "cause_class" to e.cause?.javaClass?.name)
            observation.gate.stop("NETWORK_ERROR"); throw e
        }
        val status = call.response.status.value
        if (status == 429 || status == 401 || status == 403 || status >= 500)
            observation.gate.stop("HTTP_$status")
        // Only successful provider metadata is inspected. Error bodies never enter artifacts.
        if (status in 200..299) {
            val text = call.response.bodyAsText()
            observation.responseHash = sha256(text.toByteArray(Charsets.UTF_8))
            val usage = runCatching { benchJson.parseToJsonElement(text).jsonObject["usage"]?.jsonObject }.getOrNull()
            observation.promptTokens = (usage?.get("prompt_tokens") as? JsonPrimitive)?.intOrNull
            observation.outputTokens = (usage?.get("completion_tokens") as? JsonPrimitive)?.intOrNull
            observation.cachedTokens = ((usage?.get("prompt_tokens_details") as? JsonObject)?.get("cached_tokens") as? JsonPrimitive)?.intOrNull
        }
        observation.emit("http_response", "status" to status, "request_hash" to observation.requestHash,
            "response_hash" to observation.responseHash, "prompt_tokens" to observation.promptTokens,
            "output_tokens" to observation.outputTokens, "cached_tokens" to observation.cachedTokens)
        call
    }
    return client
}
