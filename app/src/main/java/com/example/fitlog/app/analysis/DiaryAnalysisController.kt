package com.example.fitlog.app.analysis

import com.example.fitlog.data.ai.AiConfigurationException
import com.example.fitlog.data.analysis.DiaryAnalysisSession
import com.example.fitlog.data.analysis.DiaryAnalysisStorageException
import com.example.fitlog.data.analysis.StoredDiaryParse
import com.example.fitlog.data.analysis.adapter.DiaryOriginalReadException
import com.example.fitlog.domain.analysis.AnalyzeDiary
import com.example.fitlog.domain.analysis.DiaryAnalysisBusyException
import com.example.fitlog.domain.analysis.DiaryAnalysisRunner
import com.example.fitlog.model.analysis.AnalysisIssue
import com.example.fitlog.model.analysis.AnalysisItemOutcome
import com.example.fitlog.model.analysis.AnalysisItemResult
import com.example.fitlog.model.analysis.AnalysisProblem
import com.example.fitlog.model.analysis.AnalysisSource
import com.example.fitlog.model.analysis.DiaryAnalysisRun
import com.example.fitlog.model.analysis.DiaryAnalysisSummary
import com.example.fitlog.model.source.SourceKey
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** Index-write failures are reported by the caller, so the controller never references itself. */
class IndexWarning {
    private val _reported = MutableStateFlow(false)
    val reported: StateFlow<Boolean> = _reported.asStateFlow()
    fun report() { _reported.value = true }
    fun reset() { _reported.value = false }
}

/** One in-memory request slot. Navigation does not own batches; no operation waits for the slot. */
class DiaryAnalysisController internal constructor(
    private val scope: CoroutineScope,
    private val prepare: suspend () -> DiaryAnalysisSession,
    private val readSummary: suspend (SourceKey) -> DiaryAnalysisSummary,
    private val indexWarning: IndexWarning = IndexWarning(),
) : DiaryAnalysisRunner {
    private val mutableState = MutableStateFlow(DiaryAnalysisRun())
    override val state: StateFlow<DiaryAnalysisRun> = combine(mutableState, indexWarning.reported) { run, warned ->
        run.copy(indexWarning = warned)
    }.stateIn(scope, SharingStarted.Eagerly, mutableState.value)
    private var owner: Job? = null
    private var activeVault: String? = null

    fun activate(vaultId: String?) {
        if (activeVault != vaultId) {
            cancel()
            indexWarning.reset()
        }
        activeVault = vaultId
    }

    override fun cancel() {
        if (owner == null) return
        mutableState.value = mutableState.value.copy(stopped = true)
        owner?.cancel()
    }

    override fun startBatch(sources: List<AnalysisSource>, reanalyze: Boolean): Boolean {
        if (owner != null || sources.isEmpty() || sources.any { it.key.vaultId != activeVault }) return false
        indexWarning.reset()
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
        indexWarning.reset()
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

    fun reportIndexWarning() { indexWarning.report() }

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
