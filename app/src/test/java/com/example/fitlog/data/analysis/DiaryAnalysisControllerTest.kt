package com.example.fitlog.data.analysis

import com.example.fitlog.data.analysis.adapter.DiaryOriginalReadException
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DiaryAnalysisControllerTest {
    private val sources = listOf("2026-10-09.md", "2026-10-08.md", "2026-10-07.md").map {
        AnalysisSource(SourceKey(BATCH_VAULT, it), "content://$it")
    }

    private class Harness(scope: CoroutineScope) {
        var preparations = 0
        val requested = mutableListOf<DiaryParseInput>()
        val reads = mutableListOf<DiaryParseInput>()
        var text = "squat"
        var executionGate: CompletableDeferred<Unit>? = null
        var readGate: CompletableDeferred<Unit>? = null
        var readFailure = false
        var failure: DiaryParseFailure? = null
        var afterExecution: () -> Unit = {}
        var summary: DiaryAnalysisSummary? = null
        val controller = DiaryAnalysisController(scope, prepare = {
            preparations++
            object : DiaryAnalysisSession {
                override suspend fun read(source: AnalysisSource): DiaryParseInput {
                    readGate?.let { withContext(NonCancellable) { it.await() } }
                    if (readFailure && source.key.relPath == "2026-10-09.md") throw DiaryOriginalReadException(IOException())
                    return DiaryParseInput.fromSnapshot(source.key, text, "test").also { reads += it }
                }
                override suspend fun execute(input: DiaryParseInput): StoredDiaryParse {
                    requested += input; executionGate?.await()
                    afterExecution()
                    return StoredDiaryParse("run-${requested.size}", failure?.let { DiaryParseResult.Failure(it) }
                        ?: DiaryParseResult.Success(DiaryAnalysis(input.parseKey, emptyList(), emptyList(), emptyList())))
                }
            }
        }, readSummary = { key -> summary ?: DiaryAnalysisSummary(key) }).also { it.activate(BATCH_VAULT) }
    }

    @Test fun batchAndSingleShareOneSlotWithoutQueueingAndBatchSurvivesPageIndependentCalls() = runTest {
        val h = Harness(this); h.executionGate = CompletableDeferred()
        assertTrue(h.controller.startBatch(sources, true)); runCurrent()
        assertFalse(h.controller.startBatch(sources, true))
        try { h.controller.runSingle(sources.last()); fail() } catch (_: DiaryAnalysisBusyException) { }
        assertEquals(1, h.requested.size)
        h.executionGate!!.complete(Unit); advanceUntilIdle()
        assertEquals(3, h.requested.size); assertEquals(1, h.preparations)
        assertEquals(3, h.controller.state.value.succeeded); assertFalse(h.controller.state.value.active)
        assertTrue(h.controller.startBatch(listOf(sources.last()), true)); advanceUntilIdle()
        assertEquals(2, h.preparations)
    }

    @Test fun cancellationBeforeLaunchReleasesSlotAndIgnoredReadCancellationCannotMakeAPaidRequest() = runTest {
        val h = Harness(this)
        h.controller.startBatch(sources, true); h.controller.cancel(); advanceUntilIdle()
        assertFalse(h.controller.state.value.active)
        h.readGate = CompletableDeferred()
        assertTrue(h.controller.startBatch(sources, true)); runCurrent()
        h.controller.cancel(); h.readGate!!.complete(Unit); advanceUntilIdle()
        assertTrue(h.requested.isEmpty()); assertTrue(h.controller.state.value.stopped)
        assertFalse(h.controller.state.value.active)
    }

    @Test fun vaultSwitchAndBackgroundStyleCancellationRetainCompletedResults() = runTest {
        val h = Harness(this)
        h.afterExecution = { h.executionGate = CompletableDeferred() }
        h.controller.startBatch(sources, true); runCurrent()
        assertEquals(1, h.controller.state.value.succeeded)
        h.controller.activate("00000000-0000-4000-8000-000000000002"); advanceUntilIdle()
        assertEquals(1, h.controller.state.value.succeeded)
        assertEquals(2, h.controller.state.value.remaining)
        assertFalse(h.controller.startBatch(sources, true))
        assertFalse(h.controller.state.value.active)
    }

    @Test fun sourceAndOutputFailuresContinueWhileRateLimitStopsWithoutRetries() = runTest {
        val sourceFailure = Harness(this).apply { readFailure = true }
        sourceFailure.controller.startBatch(sources, true); advanceUntilIdle()
        assertEquals(2, sourceFailure.requested.size); assertEquals(1, sourceFailure.controller.state.value.failed)
        val outputFailure = Harness(this).apply { failure = DiaryParseFailure.MODEL_REFUSAL }
        outputFailure.controller.startBatch(sources, true); advanceUntilIdle()
        assertEquals(3, outputFailure.requested.size); assertEquals(3, outputFailure.controller.state.value.failed)
        val fatal = Harness(this).apply { failure = DiaryParseFailure.RATE_LIMITED }
        fatal.controller.startBatch(sources, true); advanceUntilIdle()
        assertEquals(1, fatal.requested.size); assertEquals(2, fatal.controller.state.value.remaining)
        assertEquals(DiaryParseFailure.RATE_LIMITED, fatal.controller.state.value.issue?.modelFailure)
    }

    @Test fun freshReadControlsSkippingAndPostInferenceReadDetectsChanges() = runTest {
        val h = Harness(this)
        h.summary = batchSummary(batchCandidate())
        h.controller.startBatch(listOf(sources.first()), false); advanceUntilIdle()
        assertTrue(h.requested.isEmpty()); assertEquals(1, h.controller.state.value.skipped)
        h.text = "changed squat"
        h.afterExecution = { h.text = "edited while inferring" }
        h.controller.startBatch(listOf(sources.first()), false); advanceUntilIdle()
        assertEquals(1, h.requested.size)
        assertNotEquals(h.requested.single().parseKey.contentHash, h.reads.last().parseKey.contentHash)
        assertEquals(1, h.controller.state.value.succeeded)
        assertEquals(AnalysisProblem.SOURCE_CHANGED, h.controller.state.value.items.single().issue?.problem)
    }

    @Test fun sharedSlotAlsoRejectsBatchWhileSingleRunsAndIndexWarningDoesNotFailResult() = runTest {
        val h = Harness(this); h.executionGate = CompletableDeferred()
        val single = async { h.controller.runSingle(sources.first()) }; runCurrent()
        assertFalse(h.controller.startBatch(sources, true))
        h.controller.reportIndexWarning(); h.executionGate!!.complete(Unit)
        assertTrue(single.await().result is DiaryParseResult.Success)
        assertTrue(h.controller.state.value.indexWarning)
        assertEquals(1, h.requested.size)
    }

    @Test fun singleContinuesWhenItsPageStopsAwaitingAndManualCancellationStopsTheSharedRequest() = runTest {
        val h = Harness(this); h.executionGate = CompletableDeferred()
        val page = launch { h.controller.runSingle(sources.first()) }; runCurrent()
        page.cancel(); runCurrent()
        assertTrue(h.controller.state.value.active)
        assertFalse(h.controller.startBatch(sources, true))
        h.executionGate!!.complete(Unit); advanceUntilIdle()
        assertEquals(1, h.controller.state.value.succeeded)
        h.executionGate = CompletableDeferred()
        val nextPage = launch { h.controller.runSingle(sources.last()) }; runCurrent()
        h.controller.cancel(); advanceUntilIdle()
        assertTrue(nextPage.isCancelled)
        assertFalse(h.controller.state.value.active)
        assertTrue(h.controller.state.value.stopped)
    }
}
