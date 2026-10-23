package com.example.fitlog.log

import com.example.fitlog.data.analysis.*
import com.example.fitlog.domain.AnalyzeDiary
import com.example.fitlog.domain.stopsBatch

import com.example.fitlog.data.ai.AiConfigurationException
import com.example.fitlog.data.ai.AiConfigurationFailure
import com.example.fitlog.data.analysis.adapter.DiaryOriginalReadException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class DiaryAnalysisBusyException : IllegalStateException("An explicit analysis is already running")

/** One in-memory request slot. Navigation does not own batches; no operation waits for the slot. */
class DiaryAnalysisController internal constructor(
    private val scope: CoroutineScope,
    private val prepare: suspend () -> DiaryAnalysisSession,
    private val readSummary: suspend (SourceKey) -> DiaryAnalysisSummary,
) {
    private val mutableState = MutableStateFlow(DiaryAnalysisRun())
    val state = mutableState.asStateFlow()
    private var owner: Job? = null
    private var activeVault: String? = null

    fun activate(vaultId: String?) {
        if (activeVault != vaultId) cancel()
        activeVault = vaultId
    }

    fun cancel() {
        if (owner == null) return
        mutableState.value = mutableState.value.copy(stopped = true)
        owner?.cancel()
    }

    fun startBatch(sources: List<AnalysisSource>, reanalyze: Boolean): Boolean {
        if (owner != null || sources.isEmpty() || sources.any { it.key.vaultId != activeVault }) return false
        val task = scope.launch(start = CoroutineStart.LAZY) { process(sources.toList(), reanalyze, batch = true) }
        owner = task
        task.invokeOnCompletion {
            if (owner === task) {
                mutableState.value = mutableState.value.copy(active = false, current = null)
                owner = null
            }
        }
        mutableState.value = DiaryAnalysisRun(sources.first().key.vaultId, active = true, batch = true, total = sources.size)
        task.start()
        return true
    }

    suspend fun runSingle(source: AnalysisSource): StoredDiaryParse {
        if (owner != null) throw DiaryAnalysisBusyException()
        check(activeVault == source.key.vaultId) { "The diary vault is no longer active" }
        val task = scope.async(start = CoroutineStart.LAZY) {
            requireNotNull(process(listOf(source), reanalyze = true, batch = false))
        }
        owner = task
        task.invokeOnCompletion {
            if (owner === task) {
                mutableState.value = mutableState.value.copy(active = false, current = null)
                owner = null
            }
        }
        mutableState.value = DiaryAnalysisRun(source.key.vaultId, active = true, total = 1)
        task.start()
        return task.await()
    }

    fun reportIndexWarning() { mutableState.value = mutableState.value.copy(indexWarning = true) }

    private suspend fun process(sources: List<AnalysisSource>, reanalyze: Boolean, batch: Boolean): StoredDiaryParse? {
        var singleResult: StoredDiaryParse? = null
        try {
            val session = prepare()
            currentCoroutineContext().ensureActive()
            for (source in sources) {
                currentCoroutineContext().ensureActive()
                mutableState.value = mutableState.value.copy(current = source.key)
                try {
                    val result = AnalyzeDiary(readSummary)(session, source, reanalyze)
                    currentCoroutineContext().ensureActive()
                    singleResult = result.stored
                    append(result.item)
                    if (result.stopBatch) {
                        mutableState.value = mutableState.value.copy(issue = result.item.issue)
                        break
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    currentCoroutineContext().ensureActive()
                    val issue = issueFor(e)
                    append(AnalysisItemResult(source, AnalysisItemOutcome.FAILED, issue))
                    if (!batch) throw e
                    if (issue.problem != AnalysisProblem.SOURCE) {
                        mutableState.value = mutableState.value.copy(issue = issue)
                        break
                    }
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) {
                mutableState.value = mutableState.value.copy(stopped = true)
                throw e
            }
            mutableState.value = mutableState.value.copy(issue = issueFor(e))
            if (!batch) throw e
        } finally {
            mutableState.value = mutableState.value.copy(active = false, current = null)
            owner = null
        }
        return singleResult
    }

    private fun append(item: AnalysisItemResult) {
        mutableState.value = mutableState.value.copy(items = mutableState.value.items + item)
    }
}

private fun issueFor(error: Exception) = when (error) {
    is DiaryOriginalReadException -> AnalysisIssue(AnalysisProblem.SOURCE)
    is AiConfigurationException -> AnalysisIssue(AnalysisProblem.CONFIGURATION, configurationFailure = error.reason)
    is DiaryAnalysisStorageException -> AnalysisIssue(AnalysisProblem.STORAGE)
    else -> AnalysisIssue(AnalysisProblem.REQUEST)
}
