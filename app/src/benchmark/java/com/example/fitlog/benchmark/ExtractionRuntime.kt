package com.example.fitlog.benchmark

import com.example.fitlog.data.analysis.DiaryParseInput
import com.example.fitlog.data.analysis.adapter.*
import com.google.adk.kt.agents.Instruction
import com.google.adk.kt.agents.LlmAgent
import com.google.adk.kt.agents.RunConfig
import com.google.adk.kt.models.Model
import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.models.LlmResponse
import com.google.adk.kt.runners.InMemoryRunner
import com.google.adk.kt.sessions.SessionKey
import com.google.adk.kt.types.Content
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.util.UUID

internal interface ExtractionRuntime {
    suspend fun extract(input: DiaryParseInput, observer: CaseObservation): DiaryParseExecution
}

internal class DirectExtractionRuntime(private val source: DiaryModelSource) : ExtractionRuntime {
    override suspend fun extract(input: DiaryParseInput, observer: CaseObservation): DiaryParseExecution {
        val start = System.nanoTime()
        var modelDone = start
        val result = JsonDiaryParser(DiaryModelSource { text -> source.request(text).also { modelDone = System.nanoTime() } }).execute(input)
        observer.parseValidateMs = (System.nanoTime() - modelDone) / 1e6
        observer.runtimeMs = ((modelDone - start) / 1e6 - observer.httpMs - observer.queueMs).coerceAtLeast(0.0)
        return result
    }
}

private class ModelFailed(val response: DiaryModelResponse.Failure) : RuntimeException()

/** The real ADK runner and fresh session execute; its model bridge uses the canonical FitLog request. */
internal class AdkExtractionRuntime(private val source: DiaryModelSource, private val modelId: String) : ExtractionRuntime {
    override suspend fun extract(input: DiaryParseInput, observer: CaseObservation): DiaryParseExecution {
        val init = System.nanoTime()
        var calls = 0
        val model = object : Model {
            override val name = modelId
            override fun generateContent(request: LlmRequest, stream: Boolean) = flow {
                check(!stream && ++calls == 1) { "ADK attempted more than one extraction request" }
                val users = request.contents.filter { it.role == "user" }
                // ADK's history rewriter discards blank turns. Restore the frozen blank input at
                // the canonical-model seam; never substitute a training example or add paid context.
                val text = if (users.isEmpty() && input.text.isBlank()) input.text
                    else users.singleOrNull()?.text()
                check(text == input.text) { "ADK changed diary input" }
                // Canonical instructions deliberately avoid framework routing boilerplate in the paid request.
                when (val answer = source.request(requireNotNull(text))) {
                    is DiaryModelResponse.Failure -> throw ModelFailed(answer)
                    is DiaryModelResponse.Json -> emit(LlmResponse(content = Content.fromText("model", answer.text)))
                }
            }
        }
        val runner = InMemoryRunner(LlmAgent(name = "diary_extractor", model = model,
            instruction = Instruction(DiaryExtractionPrompt.instructions), includeContents = LlmAgent.IncludeContents.NONE),
            appName = "fitlog_benchmark", artifactService = null, memoryService = null)
        val key = SessionKey(appName = "fitlog_benchmark", userId = "benchmark", id = UUID.randomUUID().toString())
        runner.sessionService.createSession(key)
        observer.runtimeInitMs = (System.nanoTime() - init) / 1e6
        val modelStart = System.nanoTime()
        val response: DiaryModelResponse = try {
            var answer: String? = null
            runner.runAsync(userId = "benchmark", sessionId = requireNotNull(key.id),
                newMessage = Content.fromText("user", input.text), runConfig = RunConfig(maxLlmCalls = 1))
                .collect { event ->
                    observer.events++
                    observer.emit("runtime_event", "event_count" to observer.events)
                    event.content?.text()?.takeIf { it.isNotEmpty() }?.let { answer = it }
                }
            check(calls == 1)
            answer?.let { DiaryModelResponse.Json(it) }
                ?: DiaryModelResponse.Failure(com.example.fitlog.data.analysis.DiaryParseFailure.EMPTY_RESPONSE)
        } catch (e: ModelFailed) { e.response }
        finally { withContext(NonCancellable) { runner.sessionService.deleteSession(key); runner.close() } }
        observer.runtimeMs = ((System.nanoTime() - modelStart) / 1e6 - observer.httpMs - observer.queueMs).coerceAtLeast(0.0)
        val parseStart = System.nanoTime()
        val result = JsonDiaryParser(DiaryModelSource { response }).execute(input)
        observer.parseValidateMs = (System.nanoTime() - parseStart) / 1e6
        return result
    }
}
