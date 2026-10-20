package com.example.fitlog.benchmark

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.withContext
import com.example.fitlog.data.analysis.DiaryParseFailure
import com.example.fitlog.data.analysis.adapter.DiaryModelResponse
import com.example.fitlog.data.analysis.adapter.OpenAiDiaryModelSource
import com.example.fitlog.data.analysis.adapter.OpenAiModelConfig
import io.ktor.client.plugins.timeout
import io.ktor.client.request.HttpRequestPipeline
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BenchmarkSafetyTest {
    private fun case(index: Int, delay: Long = 20, fault: String? = null, status: Int = 200): BenchmarkCase {
        val text = "exercise$index"
        val hash = sha256(text.toByteArray())
        return BenchmarkCase("case$index", "2026-10-${index + 1}.md", text, hash, hash,
            """{"schemaVersion":1,"sessions":[{"exercises":[{"rawName":"$text"}]}]}""",
            delay, fault, status, checkpoints = listOf(Checkpoint("$.sessions[0].exercises[0].rawName", "\"$text\"")), audited = true)
    }
    private suspend fun run(cases: List<BenchmarkCase>, profiles: List<BenchmarkProfile>, budget: Int = 50): List<JsonObject> {
        val app = RuntimeEnvironment.getApplication()
        val output = File(app.cacheDir, "bench-${UUID.randomUUID()}")
        val spec = BenchmarkSpec(outputDir = output.absolutePath, datasetFingerprint = "test", vaultId = UUID.randomUUID().toString(),
            sourceRevision = "test", cases = cases, profiles = profiles, maxRequests = budget)
        BenchmarkRunner(app).run(spec)
        return File(output, "events.jsonl").readLines().map { benchJson.parseToJsonElement(it).jsonObject }
    }
    private fun List<JsonObject>.of(type: String) = filter { it["type"]?.jsonPrimitive?.content == type }
    private fun JsonObject.value(key: String) = getValue(key).jsonPrimitive.content

    @Test fun realBaselineAndAdkProduceIdenticalRequestsAndPersistedCandidates() = runBlocking {
        val events = run((0..3).map { case(it, delay = (4-it)*20L) }, listOf(
            BenchmarkProfile("serial", "baseline", 1), BenchmarkProfile("parallel", concurrency = 2),
            BenchmarkProfile("adk", "adk", 2)))
        val ends = events.of("case_terminal")
        assertEquals(12, ends.size)
        assertTrue(ends.all { it.value("usable") == "true" && it.value("checkpoint_failures") == "0" })
        ends.groupBy { it.value("case_id") }.values.forEach { observations ->
            assertEquals(1, observations.map { it.value("request_hash") }.distinct().size)
        }
        assertEquals(12, events.of("candidate_persisted").size)
        assertTrue(events.of("profile_end").all { it.value("completed") == "true" })
        assertTrue(events.of("profile_end").all { it.value("max_inflight_http").toInt() <= 2 })
        assertTrue(ends.filter { it.value("profile") == "adk" }.all { it.value("http_requests") == "1" && it.value("runtime_events").toInt() > 0 })
    }

    @Test fun budgetCountsAllProfilesAndNeverSendsBeyondLimit() = runBlocking {
        val events = run((0..7).map { case(it) }, listOf(BenchmarkProfile("limited", concurrency = 4), BenchmarkProfile("later")), 3)
        assertEquals(3, events.of("http_start").size)
        assertEquals("3", events.last().value("requests_used"))
        assertTrue(events.of("case_terminal").any { it.value("outcome") == "UNEXECUTED" })
    }

    @Test fun httpFailuresStopQueuedRequestsWithoutRetry() = runBlocking {
        for (status in listOf(429, 503)) {
            val events = run(listOf(case(0, status = status)) + (1..3).map { case(it) }, listOf(BenchmarkProfile("failure", arrivalIntervalMs = 200)))
            assertEquals(1, events.of("http_start").size)
            assertEquals(1, events.of("case_terminal").count { it.value("outcome") == "FAILED" })
            assertEquals(3, events.of("case_terminal").count { it.value("outcome") == "UNEXECUTED" })
        }
    }

    @Test fun timeoutAndNetworkErrorsStopQueuedRequests() = runBlocking {
        for (fault in listOf("timeout", "network")) {
            val events = run(listOf(case(0, fault = fault), case(1)), listOf(BenchmarkProfile("failure", arrivalIntervalMs = 200)))
            assertEquals(1, events.of("http_start").size)
            assertEquals("FAILED", events.of("case_terminal").single { it.value("case_id") == "case0" }.value("outcome"))
        }
    }

    @Test fun quotaQueueDoesNotConsumeEachRequestsHttpTimeout() = runBlocking {
        val cases = (0..2).map { case(it, delay = 120) }
        val spec = BenchmarkSpec(outputDir = "unused", datasetFingerprint = "test",
            vaultId = UUID.randomUUID().toString(), sourceRevision = "test", cases = cases,
            profiles = listOf(BenchmarkProfile("queue-timeout", concurrency = 1)))
        val file = File(RuntimeEnvironment.getApplication().cacheDir, "timeout-${UUID.randomUUID()}.jsonl")
        val client = benchmarkClient(spec, cases.associateBy { it.id })
        // Use the real Ktor request timeout, rather than throwing a synthetic timeout fault.
        client.requestPipeline.intercept(HttpRequestPipeline.Before) {
            context.timeout { requestTimeoutMillis = 200 }
            proceed()
        }
        try {
            BenchmarkTrace(file).use { trace ->
                val budget = RequestBudget(3)
                val gate = RequestGate(1, budget)
                val source = OpenAiDiaryModelSource(client, OpenAiModelConfig("https://benchmark.invalid/v1", "replay"), "fixture-key")
                val observations = cases.map { CaseObservation("queue-timeout", it.id, trace, gate) }
                val answers = cases.mapIndexed { index, c -> async(start = CoroutineStart.UNDISPATCHED) {
                    runCatching { withContext(observations[index]) { source.request(c.text) } }
                } }.map { it.await() }
                assertTrue("Queue time must not shorten a later request's HTTP timeout: $answers",
                    answers.all { it.getOrNull() is DiaryModelResponse.Json })
                assertTrue("The last request must actually wait longer than its HTTP timeout",
                    observations.last().queueMs > 200)
                assertEquals(3, budget.used)
                assertEquals(1, gate.maximumInflight.get())
                assertTrue(observations.all { it.httpCalls == 1 })
            }
        } finally { client.close() }
    }

    @Test fun actualHttpTimeoutStillStopsTheRequestWithoutRetry() = runBlocking {
        val c = case(0, delay = 400)
        val queued = case(1, delay = 20)
        val spec = BenchmarkSpec(outputDir = "unused", datasetFingerprint = "test",
            vaultId = UUID.randomUUID().toString(), sourceRevision = "test", cases = listOf(c, queued),
            profiles = listOf(BenchmarkProfile("http-timeout")))
        val client = benchmarkClient(spec, listOf(c, queued).associateBy { it.id })
        client.requestPipeline.intercept(HttpRequestPipeline.Before) {
            context.timeout { requestTimeoutMillis = 100 }
            proceed()
        }
        val file = File(RuntimeEnvironment.getApplication().cacheDir, "http-timeout-${UUID.randomUUID()}.jsonl")
        try {
            BenchmarkTrace(file).use { trace ->
                val budget = RequestBudget(1)
                val gate = RequestGate(1, budget)
                val observation = CaseObservation("http-timeout", c.id, trace, gate)
                val queuedObservation = CaseObservation("http-timeout", queued.id, trace, gate)
                val source = OpenAiDiaryModelSource(client, OpenAiModelConfig("https://benchmark.invalid/v1", "replay"), "fixture-key")
                val active = async(start = CoroutineStart.UNDISPATCHED) {
                    withContext(observation) { source.request(c.text) }
                }
                val waiting = async(start = CoroutineStart.UNDISPATCHED) {
                    runCatching { withContext(queuedObservation) { source.request(queued.text) } }
                }
                val answer = active.await()
                assertEquals(DiaryParseFailure.TIMEOUT, (answer as DiaryModelResponse.Failure).reason)
                assertEquals("TIMEOUT", gate.reason)
                assertEquals("TIMEOUT", (waiting.await().exceptionOrNull() as BenchmarkStopped).reason)
                assertEquals(1, budget.used)
                assertEquals(1, observation.httpCalls)
                assertEquals(0, queuedObservation.httpCalls)
            }
        } finally { client.close() }
    }

    @Test fun truncationRefusalAndBrokenJsonRemainFailuresWithOneRequest() = runBlocking {
        val broken = case(2).copy(responseJson = "{")
        val events = run(listOf(case(0, fault = "truncated"), case(1, fault = "refusal"), broken),
            listOf(BenchmarkProfile("invalid", concurrency = 3)))
        assertEquals(3, events.of("http_start").size)
        assertTrue(events.of("case_terminal").all { it.value("usable") == "false" && it.value("outcome") == "FAILED" })
    }

    @Test fun cancellationNeverDispatchesAfterCancelAndDoesNotPersistInFlightResults() = runBlocking {
        val events = run((0..7).map { case(it, delay = 1000) }, listOf(BenchmarkProfile("cancel", concurrency = 2, cancelAfterMs = 100)))
        val cancel = events.of("cancel_requested").single().getValue("sequence").jsonPrimitive.int
        assertTrue(events.of("http_start").all { it.getValue("sequence").jsonPrimitive.int < cancel })
        assertEquals(0, events.of("candidate_persisted").size)
        assertTrue(events.of("http_start").size <= 2)
        assertTrue(events.of("case_terminal").all { it.value("outcome") == "CANCELLED" })
    }

    @Test fun adkCancellationClosesSessionWithoutNewDispatch() = runBlocking {
        val events = run((0..3).map { case(it, delay = 1000) }, listOf(BenchmarkProfile("cancel-adk", "adk", 1, cancelAfterMs = 100)))
        assertTrue(events.of("http_start").size <= 1)
        assertTrue(events.of("case_terminal").all { it.value("outcome") == "CANCELLED" })
    }

    @Test fun queuedRequestStoppedByCancellationIsCancelledBeforeParentJobCancellation() = runBlocking {
        val file = File(RuntimeEnvironment.getApplication().cacheDir, "gate-${UUID.randomUUID()}.jsonl")
        BenchmarkTrace(file).use { trace ->
            val budget = RequestBudget(10)
            val gate = RequestGate(1, budget)
            val release = CompletableDeferred<Unit>()
            val active = async(start = CoroutineStart.UNDISPATCHED) {
                gate.dispatch(CaseObservation("cancel", "active", trace, gate)) { release.await() }
            }
            var dispatched = false
            val queued = async(start = CoroutineStart.UNDISPATCHED) {
                try {
                    gate.dispatch(CaseObservation("cancel", "queued", trace, gate)) { dispatched = true }
                    "SUCCEEDED"
                } catch (stopped: BenchmarkStopped) { classify(stopped) }
            }
            // Reproduce a waiter resuming after gate.stop, before the parent job is cancelled.
            gate.stop("CANCELLED")
            release.complete(Unit)
            active.await()
            assertEquals("CANCELLED", queued.await())
            assertFalse(dispatched)
            assertEquals(1, budget.used)
        }
    }

    @Test fun sourceChangesInvalidateUsabilityButKeepCandidateProvenance() = runBlocking {
        val events = run(listOf(case(0, fault = "source_changed")), listOf(BenchmarkProfile("changed")))
        assertEquals(1, events.of("candidate_persisted").size)
        assertEquals("SOURCE_CHANGED", events.of("case_terminal").single().value("outcome"))
        assertEquals("false", events.of("case_terminal").single().value("usable"))
    }

    @Test fun actualRoomWriteFailureStopsWithoutRetryOrUsableCandidate() = runBlocking {
        val events = run(listOf(case(0, fault = "storage"), case(1)), listOf(BenchmarkProfile("storage", arrivalIntervalMs = 200)))
        assertEquals(1, events.of("http_start").size)
        assertEquals(0, events.of("candidate_persisted").size)
        assertEquals("STORAGE_ERROR", events.of("case_terminal").first().value("outcome"))
    }

    @Test fun scheduledArrivalLatencyIncludesQueueButExcludesPreArrivalWait() = runBlocking {
        val events = run((0..3).map { case(it, delay = 60) }, listOf(BenchmarkProfile("arrival", concurrency = 1, arrivalIntervalMs = 10)))
        assertEquals("fixed_arrival", events.of("profile_start").single().value("load_model"))
        assertEquals(4, events.of("case_terminal").size)
        assertTrue(events.of("case_terminal").all { it.getValue("latency_ms").jsonPrimitive.double >= 0 })
        assertTrue(events.of("case_terminal").last().getValue("queue_ms").jsonPrimitive.double > 30)
    }

    @Test fun emptyResponseCannotPassNonemptyDiaryCheckpoints() = runBlocking {
        val events = run(listOf(case(0).copy(responseJson = """{"schemaVersion":1,"sessions":[]}""")), listOf(BenchmarkProfile("empty")))
        assertEquals("QUALITY_FAILED", events.of("case_terminal").single().value("outcome"))
        assertEquals("false", events.of("case_terminal").single().value("usable"))
    }

    @Test fun checkpointPathsRejectIgnoredTrailingSyntax() {
        assertThrows(IllegalArgumentException::class.java) { atPath(JsonNull, "$.sessions!!!") }
        assertTrue(jsonEquivalent(JsonPrimitive(2), JsonPrimitive(2.0)))
        assertFalse(jsonEquivalent(JsonPrimitive("2"), JsonPrimitive(2)))
    }

    @Test fun emptyDiaryUsesTheSameSingleRequestAcrossAllRuntimes() = runBlocking {
        val hash = sha256(byteArrayOf())
        val empty = BenchmarkCase("empty", "2026-10-01.md", "", hash, hash,
            responseJson = """{"schemaVersion":1,"sessions":[]}""", checkpoints = listOf(Checkpoint("$.sessions", "[]")), audited = true)
        val events = run(listOf(empty), listOf(BenchmarkProfile("baseline-empty", "baseline"),
            BenchmarkProfile("light-empty"), BenchmarkProfile("adk-empty", "adk")))
        assertEquals(events.toString(), 3, events.of("http_start").size)
        assertTrue(events.toString(), events.of("case_terminal").all { it.value("usable") == "true" })
        assertEquals(1, events.of("case_terminal").map { it.value("request_hash") }.distinct().size)
    }

    @Test fun qualityChecksAcceptEquivalentGroupAndRepsListRepresentations() = runBlocking {
        val text = "bench 10kg 6+8"
        val hash = sha256(text.toByteArray())
        val points = listOf(Checkpoint("$.validated.sessions[0].exercises[0].sets[1].reps.value", "8"),
            Checkpoint("$.validated.sessions[0].exercises[0].sets[1].weight.value", "10"))
        val list = """{"schemaVersion":1,"sessions":[{"exercises":[{"rawName":"bench","groups":[{"weight":10,"unit":"KG","count":2,"repsList":[6,8]}]}]}]}"""
        val groups = """{"schemaVersion":1,"sessions":[{"exercises":[{"rawName":"bench","groups":[{"weight":10,"unit":"KG","count":1,"reps":6},{"weight":10,"unit":"KG","count":1,"reps":8}]}]}]}"""
        val cases = listOf(list, groups).mapIndexed { index, response -> BenchmarkCase("repr$index", "2026-10-${index+1}.md",
            text, hash, hash, responseJson = response, checkpoints = points, audited = true) }
        val events = run(cases, listOf(BenchmarkProfile("representations")))
        assertTrue(events.toString(), events.of("case_terminal").all { it.value("usable") == "true" })
    }

    @Test fun ambiguousMissingCountKeepsReportedFactsWithoutInventingSets() = runBlocking {
        val c = case(0).copy(responseJson = """{"schemaVersion":1,"sessions":[{"exercises":[{"rawName":"exercise0","groups":[{"count":null,"reps":6},{"count":null,"reps":8}]}]}]}""",
            checkpoints = listOf(Checkpoint("$.metrics.exercises[0].reportedReps", "[6,8]"),
                Checkpoint("$.validated.sessions[0].exercises[0].sets.length", "0")))
        val events = run(listOf(c), listOf(BenchmarkProfile("unknown-count")))
        val end = events.of("case_terminal").single()
        assertEquals("true", end.value("usable"))
        assertTrue(end.getValue("review_notices").jsonPrimitive.int > 0)
    }
}
