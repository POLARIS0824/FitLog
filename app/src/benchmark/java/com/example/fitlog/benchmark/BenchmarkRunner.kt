package com.example.fitlog.benchmark

import android.content.Context
import androidx.room.Room
import com.example.fitlog.data.analysis.*
import com.example.fitlog.data.analysis.adapter.*
import com.example.fitlog.data.hash.contentTextHash
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

internal class BenchmarkRunner(private val context: Context) {
    suspend fun run(spec: BenchmarkSpec, credential: String? = null) {
        spec.validate()
        val output = File(spec.outputDir).also { it.mkdirs() }
        val events = File(output, "events.jsonl")
        check(!events.exists()) { "A benchmark run cannot overwrite an existing trace" }
        val budget = RequestBudget(spec.maxRequests)
        // Never persist the credential, and don't resolve it in replay mode.
        val apiKey = if (spec.mode == "live") requireNotNull(credential ?: System.getenv(spec.apiKeyEnv)) { "Missing credential" }
            else "fixture-key"
        val modelConfig = OpenAiModelConfig(spec.baseUrl, spec.modelId)
        val cases = spec.cases.associateBy { it.id }
        BenchmarkTrace(events).use { trace ->
            trace.emit("run_start", fields = arrayOf("mode" to spec.mode, "platform" to spec.platform,
                "dataset_fingerprint" to spec.datasetFingerprint, "source_revision" to spec.sourceRevision,
                "code_fingerprint" to spec.codeFingerprint,
                "prompt_version" to DiaryExtractionPrompt.VERSION, "validator_version" to DiaryCandidateValidator.VERSION,
                "prompt_sha256" to sha256(DiaryExtractionPrompt.instructions.toByteArray()),
                "extractor_version" to modelConfig.extractorVersion(), "model" to spec.modelId,
                "endpoint" to openAiEndpoint(spec.baseUrl, "chat/completions"), "max_requests" to spec.maxRequests,
                "budget_limit" to spec.budgetLimit, "study_stage" to spec.studyStage,
                "previous_requests" to spec.previousRequests,
                "connect_timeout_ms" to 15_000, "request_timeout_ms" to 120_000,
                "timeout_scope" to "HTTP request after benchmark quota admission; engine queue and response body included",
                "java_version" to System.getProperty("java.version"), "seed" to spec.seed))
            trace.emit("environment", fields = arrayOf("started_utc" to java.time.Instant.now().toString(),
                "proxy_url" to spec.proxyUrl, "os" to System.getProperty("os.name"),
                "available_processors" to Runtime.getRuntime().availableProcessors(),
                "device_model" to android.os.Build.MODEL, "sdk_int" to android.os.Build.VERSION.SDK_INT,
                "source_mode" to if (spec.sourceRoot == null) "embedded-frozen-snapshot" else "local-frozen-files",
                "connection_policy" to "one shared canonical Ktor/OkHttp client per run; actual reuse unobserved"))
            val client = benchmarkClient(spec, cases)
            var cancelled = false
            try {
                val source = OpenAiDiaryModelSource(client, modelConfig, apiKey)
                for (profile in spec.profiles) {
                    if (budget.used >= spec.maxRequests) break
                    val stop = runProfile(spec, profile, cases, source, budget, trace)
                    if (stop != null && spec.mode == "live") break
                }
            } catch (e: CancellationException) {
                cancelled = true
                throw e
            } finally {
                client.close()
                trace.emit("run_end", fields = arrayOf("requests_used" to budget.used, "cancelled" to cancelled))
            }
        }
    }

    private suspend fun runProfile(spec: BenchmarkSpec, profile: BenchmarkProfile,
        cases: Map<String, BenchmarkCase>, source: DiaryModelSource, budget: RequestBudget, trace: BenchmarkTrace) = coroutineScope {
        val selected = (profile.caseIds.ifEmpty { spec.cases.map { it.id } }).map(cases::getValue)
        val gate = RequestGate(profile.concurrency, budget)
        val initialization = System.nanoTime()
        val dbName = "b" + sha256((spec.outputDir + profile.id).toByteArray()).take(12)
        val database = Room.databaseBuilder(context, DiaryAnalysisDatabase::class.java, dbName).build()
        database.openHelper.writableDatabase
        val repository = DiaryAnalysisRepository(database)
        val runtime: ExtractionRuntime = if (profile.runtime == "adk") AdkExtractionRuntime(source, spec.modelId)
            else DirectExtractionRuntime(source)
        trace.emit("profile_start", profile.id, fields = arrayOf("runtime" to profile.runtime,
            "runtime_version" to if (profile.runtime == "adk") "1.3.1" else spec.sourceRevision,
            "concurrency" to profile.concurrency, "expected" to selected.size, "round" to profile.round,
            "period" to profile.period, "warmup" to profile.warmup,
            "load_model" to if (profile.arrivalIntervalMs == null) "fixed_work" else "fixed_arrival",
            "arrival_interval_ms" to profile.arrivalIntervalMs, "initialization_ms" to (System.nanoTime() - initialization) / 1e6,
            "storage_backend" to "isolated-room-file", "tool_calls" to 0))
        val started = System.nanoTime()
        val observations = selected.associate { it.id to CaseObservation(profile.id, it.id, trace, gate) }
        observations.values.forEachIndexed { index, observation ->
            observation.arrivedNs = started + (profile.arrivalIntervalMs ?: 0) * index * 1_000_000
        }
        val terminal = java.util.concurrent.ConcurrentHashMap<String, String>()
        val pending = java.util.concurrent.ConcurrentHashMap<String, Pair<DiaryParseInput, StoredDiaryParse>>()
        val reads = java.util.concurrent.ConcurrentHashMap<String, Int>()
        val taskCount = AtomicInteger()
        val maxTasks = AtomicInteger()
        var firstUsable: Double? = null

        fun finish(case: BenchmarkCase, observation: CaseObservation, input: DiaryParseInput?, stored: StoredDiaryParse?,
            fresh: Boolean = true, outcome: String? = null) {
            if (terminal.putIfAbsent(case.id, outcome ?: "DONE") != null) return
            val analysis = (stored?.result as? DiaryParseResult.Success)?.analysis
            val failure = (stored?.result as? DiaryParseResult.Failure)?.reason?.name
            val checkpointFailures = if (analysis != null && case.checkpoints.isNotEmpty()) {
                val candidate = benchJson.encodeToJsonElement(DiaryCandidate.serializer(), DiaryCandidate(
                    DIARY_SCHEMA_VERSION, analysis.sessions.map { session -> SessionCandidate(
                        date = session.date?.toString(), exercises = session.exercises.map { it.candidate }) })).jsonObject
                val encoded = JsonObject(candidate + mapOf(
                    "validated" to benchJson.encodeToJsonElement(DiaryAnalysis.serializer(), analysis),
                    "metrics" to buildJsonObject {
                        put("exerciseCount", analysis.sessions.sumOf { it.exercises.size })
                        put("exercises", buildJsonArray {
                            analysis.sessions.flatMap { it.exercises }.forEach { exercise -> add(buildJsonObject {
                                // Reported facts, without inventing a group count for ambiguous notation.
                                put("reportedReps", buildJsonArray {
                                    exercise.candidate.groups.forEach { group ->
                                        (group.repsList ?: listOfNotNull(group.reps)).forEach { add(JsonPrimitive(it)) }
                                    }
                                })
                            }) }
                        })
                    }))
                case.checkpoints.count { !jsonEquivalent(atPath(encoded, it.path), benchJson.parseToJsonElement(it.expectedJson)) }
            } else 0
            val usable = analysis != null && !analysis.hasErrors && fresh && checkpointFailures == 0
            val notices = analysis?.let { batchReviewNotices(it).size + it.modelIssues.size } ?: 0
            val resultOutcome = outcome ?: if (!fresh) "SOURCE_CHANGED" else if (failure != null) "FAILED"
                else if (checkpointFailures > 0) "QUALITY_FAILED" else if (analysis?.hasErrors == true) "PARTIAL" else "SUCCEEDED"
            terminal[case.id] = resultOutcome
            val elapsed = (System.nanoTime() - started) / 1e6
            synchronized(observations) { if (usable && firstUsable == null) firstUsable = elapsed }
            observation.emit("case_terminal", "outcome" to resultOutcome, "failure" to failure,
                "usable" to usable, "audited" to case.audited, "checkpoint_failures" to checkpointFailures,
                "review_notices" to notices, "latency_ms" to observation.elapsedMs(), "http_ms" to observation.httpMs,
                "queue_ms" to observation.queueMs, "runtime_init_ms" to observation.runtimeInitMs,
                "runtime_ms" to observation.runtimeMs, "parse_validate_ms" to observation.parseValidateMs,
                "storage_ms" to observation.storageMs, "http_requests" to observation.httpCalls,
                "runtime_events" to observation.events, "parse_run_id" to stored?.parseRunId,
                "source_hash" to input?.parseKey?.contentHash, "ttft_ms" to null,
                "prompt_tokens" to observation.promptTokens, "output_tokens" to observation.outputTokens,
                "cached_tokens" to observation.cachedTokens, "request_hash" to observation.requestHash)
        }

        fun read(case: BenchmarkCase): DiaryParseInput {
            val observation = observations.getValue(case.id)
            val count = reads.merge(case.id, 1, Int::plus)!!
            if (count == 1) observation.emit("source_read_start")
            val text = if (spec.sourceRoot != null) {
                val root = File(spec.sourceRoot).canonicalFile
                val file = File(root, case.relPath).canonicalFile
                check(file.toPath().startsWith(root.toPath()))
                val bytes = file.readBytes()
                if (count == 1) check(sha256(bytes) == case.rawSha256) { "Frozen source changed" }
                bytes.toString(Charsets.UTF_8)
            } else if (count > 1 && case.fault == "source_changed") case.text + "\nchanged" else case.text
            val input = DiaryParseInput.fromSnapshot(SourceKey(spec.vaultId, case.relPath), text, OpenAiModelConfig(spec.baseUrl, spec.modelId).extractorVersion())
            if (count == 1) {
                check(input.parseKey.contentHash == case.contentHash)
                observation.sourceDoneNs = System.nanoTime()
                observation.emit("source_read_end", "content_hash" to input.parseKey.contentHash)
            }
            return input
        }

        suspend fun execute(case: BenchmarkCase, input: DiaryParseInput): StoredDiaryParse {
            val observation = observations.getValue(case.id)
            return withContext(observation) {
                var extractionDone = 0L
                val stored = repository.parse(input, RecordingDiaryParser { current ->
                    runtime.extract(current, observation).also {
                        extractionDone = System.nanoTime()
                        if (spec.mode == "live") {
                            val answers = File(spec.outputDir, "private-answers").also { it.mkdirs() }
                            val artifact = buildJsonObject {
                                put("case_id", case.id); put("source_hash", input.parseKey.contentHash)
                                put("raw_model_json", it.rawModelJson?.let(::JsonPrimitive) ?: JsonNull)
                            }
                            File(answers, "${profile.id}-${case.id}.json").writeText(artifact.toString())
                        }
                        if (case.fault == "storage") database.openHelper.writableDatabase.execSQL("DROP TABLE parse_run")
                        val failure = (it.result as? DiaryParseResult.Failure)?.reason
                        if (failure != null && stopsBatch(failure)) gate.stop(failure.name)
                    }
                })
                observation.storageMs = (System.nanoTime() - extractionDone) / 1e6
                observation.emit("candidate_persisted", "parse_run_id" to stored.parseRunId)
                stored
            }
        }

        val work = launch {
            try {
                if (profile.runtime == "baseline") {
                    val byPath = selected.associateBy { it.relPath }
                    val session = object : DiaryAnalysisSession {
                        override suspend fun read(source: AnalysisSource): DiaryParseInput {
                            val case = byPath.getValue(source.key.relPath)
                            val input = try { read(case) } catch (e: Exception) {
                                finish(case, observations.getValue(case.id), null, null, outcome = "SOURCE_ERROR")
                                throw DiaryOriginalReadException(e)
                            }
                            pending.remove(case.id)?.let { (original, stored) ->
                                finish(case, observations.getValue(case.id), original, stored,
                                    DiaryContentVersion.from(original) == DiaryContentVersion.from(input))
                            }
                            return input
                        }
                        override suspend fun execute(input: DiaryParseInput): StoredDiaryParse {
                            val case = byPath.getValue(input.parseKey.sourceKey.relPath)
                            return try { execute(case, input).also { pending[case.id] = input to it } }
                            catch (e: CancellationException) { throw e }
                            catch (e: Exception) {
                                observations.getValue(case.id).emit("exception", "exception_class" to e.javaClass.name)
                                gate.stop(classify(e))
                                finish(case, observations.getValue(case.id), input, null, outcome = classify(e))
                                throw e
                            }
                        }
                    }
                    val controller = DiaryAnalysisController(this, { session }, repository::readSummary)
                    controller.activate(spec.vaultId)
                    check(controller.startBatch(selected.map { AnalysisSource(SourceKey(spec.vaultId, it.relPath), "benchmark://${it.id}") }, true))
                    controller.state.first { !it.active }
                    maxTasks.set(1)
                } else coroutineScope {
                    selected.mapIndexed { index, case -> launch {
                        profile.arrivalIntervalMs?.let { interval ->
                            val remaining = interval * index - (System.nanoTime() - started) / 1_000_000
                            if (remaining > 0) delay(remaining)
                        }
                        val observation = observations.getValue(case.id)
                        val activeTasks = taskCount.incrementAndGet()
                        maxTasks.updateAndGet { maxOf(it, activeTasks) }
                        observation.emit("case_start")
                        try {
                            val input = try { read(case) } catch (e: Exception) { throw DiaryOriginalReadException(e) }
                            val stored = execute(case, input)
                            currentCoroutineContext().ensureActive()
                            val fresh = try { read(case) } catch (e: Exception) { throw DiaryOriginalReadException(e) }
                            finish(case, observation, input, stored, DiaryContentVersion.from(input) == DiaryContentVersion.from(fresh))
                        } catch (e: CancellationException) { finish(case, observation, null, null, outcome = "CANCELLED"); throw e }
                        catch (e: Exception) {
                            observation.emit("exception", "exception_class" to e.javaClass.name)
                            val outcome = classify(e)
                            if (outcome in setOf("STORAGE_ERROR", "RUNTIME_ERROR")) gate.stop(outcome)
                            finish(case, observation, null, null, outcome = outcome)
                        } finally { taskCount.decrementAndGet() }
                    } }.joinAll()
                }
            } finally { database.close() }
        }
        val cancellation = profile.cancelAfterMs?.let { delayMs -> launch {
            delay(delayMs); trace.emit("cancel_requested", profile.id); gate.stop("CANCELLED"); work.cancel()
        } }
        work.join(); cancellation?.cancelAndJoin()
        selected.filter { !terminal.containsKey(it.id) }.forEach {
            finish(it, observations.getValue(it.id), null, null, outcome = if (work.isCancelled) "CANCELLED" else "UNEXECUTED")
        }
        trace.emit("profile_end", profile.id, fields = arrayOf("batch_ms" to (System.nanoTime() - started) / 1e6,
            "first_usable_ms" to firstUsable, "max_inflight_http" to gate.maximumInflight.get(),
            "max_active_tasks" to maxTasks.get(), "requests_used" to budget.used, "stop_reason" to gate.reason,
            "completed" to (terminal.values.none { it == "CANCELLED" || it == "UNEXECUTED" } && gate.reason == null),
            "heap_used_bytes" to Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()))
        gate.reason
    }
}

internal fun classify(error: Exception) = when (error) {
    is BenchmarkStopped -> if (error.reason == "CANCELLED") "CANCELLED" else "UNEXECUTED"
    is DiaryAnalysisStorageException -> "STORAGE_ERROR"
    is DiaryOriginalReadException -> "SOURCE_ERROR"
    else -> "RUNTIME_ERROR"
}

internal fun atPath(root: JsonElement, path: String): JsonElement {
    require(path.startsWith("$"))
    require(Regex("\\$(?:\\.[A-Za-z][A-Za-z0-9_]*|\\[\\d+\\])*").matches(path))
    var current = root
    Regex("\\.([A-Za-z][A-Za-z0-9_]*)|\\[(\\d+)\\]").findAll(path.removePrefix("$")).forEach { match ->
        current = if (match.groupValues[1] == "length" && current is JsonArray) JsonPrimitive(current.size)
            else if (match.groupValues[1].isNotEmpty()) (current as? JsonObject)?.get(match.groupValues[1]) ?: JsonNull
            else (current as? JsonArray)?.getOrNull(match.groupValues[2].toInt()) ?: JsonNull
    }
    return current
}

internal fun jsonEquivalent(a: JsonElement, b: JsonElement): Boolean = if (a is JsonPrimitive && b is JsonPrimitive &&
    !a.isString && !b.isString && a.doubleOrNull != null && b.doubleOrNull != null) a.doubleOrNull == b.doubleOrNull else a == b
