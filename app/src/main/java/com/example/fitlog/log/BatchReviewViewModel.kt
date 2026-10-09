package com.example.fitlog.log

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fitlog.R
import com.example.fitlog.data.analysis.*
import com.example.fitlog.data.index.*
import com.example.fitlog.data.vault.VaultConfigState
import com.example.fitlog.navigation.FitLogRoute
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

internal data class BatchReviewItem(val source: IndexedSource?, val summary: DiaryAnalysisSummary,
    val assessment: BatchReviewAssessment) {
    val id get() = summary.candidate?.attempt?.id ?: summary.sourceKey.relPath
    val selectable get() = assessment.eligibility in setOf(BatchReviewEligibility.READY, BatchReviewEligibility.WITH_NOTICES)
}

internal data class BatchConfirmationProgress(val total: Int = 0, val confirmed: Int = 0, val skipped: Int = 0,
    val failed: Int = 0, val stopped: Boolean = false) {
    val remaining get() = total - confirmed - skipped - failed
}

class BatchReviewViewModel internal constructor(
    val route: FitLogRoute.BatchReview,
    private val savedState: SavedStateHandle,
    private val index: Flow<SourceIndexSnapshot>,
    private val analysis: DiaryVaultAnalysisReader,
    private val config: Flow<VaultConfigState>,
    private val confirm: suspend (IndexedSource, DiaryConfirmation) -> DiaryConfirmationResult,
    private val refreshIndex: () -> Unit,
) : ViewModel() {
    internal var items by mutableStateOf<List<BatchReviewItem>>(emptyList()); private set
    internal var selected by mutableStateOf(savedState.get<ArrayList<String>>("selected")?.toSet().orEmpty()); private set
    var loading by mutableStateOf(true); private set
    var readFailed by mutableStateOf(false); private set
    var activeVault by mutableStateOf(false); private set
    var saving by mutableStateOf(false); private set
    var message by mutableStateOf<Int?>(null); private set
    internal var progress by mutableStateOf(BatchConfirmationProgress()); private set
    var indexWarning by mutableStateOf(false); private set
    private var initialized = savedState.get<Boolean>("initialized") ?: false
    private var observation: Job? = null
    private var submission: Job? = null

    init { observe() }

    private fun observe() {
        observation?.cancel(); loading = true; readFailed = false
        observation = viewModelScope.launch {
            try {
                combine(index, analysis.observeSummaries(route.vaultId), config) { snapshot, summaries, value ->
                    Triple(snapshot, summaries, value)
                }.collect { (snapshot, summaries, value) ->
                    if (value !is VaultConfigState.Loading) {
                        activeVault = (value as? VaultConfigState.Configured)?.vaultId == route.vaultId
                        if (!activeVault) cancelSubmission()
                    }
                    val sources = snapshot.sources.associateBy { it.path }
                    items = summaries.filter { summary -> summary.candidateReadFailed ||
                        (summary.candidate != null && summary.confirmed?.parseRunId != summary.candidate.attempt.id)
                    }.map { summary ->
                        val source = sources[summary.sourceKey.relPath]
                        BatchReviewItem(source, summary, assessBatchReview(summary, source?.let(::indexedVersion),
                            source?.status == IndexedSource.AVAILABLE))
                    }.sortedByDescending { it.summary.sourceKey.relPath }
                    val eligible = items.filter { it.selectable }.mapTo(mutableSetOf()) { it.id }
                    val retained = selected.intersect(eligible)
                    if (retained != selected && !saving) message = R.string.batch_review_selection_changed
                    storeSelection(retained)
                    if (!initialized && !(snapshot.sources.isEmpty() && snapshot.refreshing)) {
                        storeSelection(items.filter { it.assessment.eligibility == BatchReviewEligibility.READY }.mapTo(mutableSetOf()) { it.id })
                        initialized = true; savedState["initialized"] = true
                    }
                    loading = value is VaultConfigState.Loading
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                readFailed = true; loading = false
            }
        }
    }

    fun refresh() { if (!saving) { refreshIndex(); observe() } }

    internal fun toggle(item: BatchReviewItem) {
        if (saving || !activeVault || readFailed || !item.selectable) return
        storeSelection(if (item.id in selected) selected - item.id else selected + item.id)
    }

    fun toggleReady() {
        if (saving || !activeVault || readFailed) return
        val ready = items.filter { it.assessment.eligibility == BatchReviewEligibility.READY }.mapTo(mutableSetOf()) { it.id }
        storeSelection(if (ready.isNotEmpty() && selected.containsAll(ready)) selected - ready else selected + ready)
    }

    private fun storeSelection(value: Set<String>) { selected = value; savedState["selected"] = ArrayList(value) }

    fun submit() {
        if (saving || loading || readFailed || !activeVault) return
        val chosen = items.filter { it.id in selected && it.selectable }.toList()
        if (chosen.isEmpty()) return
        saving = true; message = null; progress = BatchConfirmationProgress(total = chosen.size)
        submission = viewModelScope.launch {
            try {
                for (item in chosen) {
                    currentCoroutineContext().ensureActive()
                    val current = items.firstOrNull { it.id == item.id && it.selectable }
                    if (current == null || item.id !in selected) {
                        progress = progress.copy(skipped = progress.skipped + 1); continue
                    }
                    try {
                        val candidate = requireNotNull(item.summary.candidate)
                        val request = DiaryConfirmation.fromCandidate(candidate.attempt.id, candidate.analysis,
                            requireNotNull(item.assessment.date)).copy(requireLatestCandidate = true)
                        when (val result = confirm(requireNotNull(item.source), request)) {
                            is DiaryConfirmationResult.Confirmed -> {
                                progress = progress.copy(confirmed = progress.confirmed + 1)
                                storeSelection(selected - item.id)
                            }
                            is DiaryConfirmationResult.Invalid -> {
                                progress = progress.copy(skipped = progress.skipped + 1)
                                storeSelection(selected - item.id)
                                message = confirmationFailureMessage(result.reason)
                            }
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        progress = progress.copy(failed = progress.failed + 1, stopped = true)
                        message = R.string.detail_review_save_failed
                        break
                    }
                }
            } catch (e: CancellationException) {
                progress = progress.copy(stopped = true)
                throw e
            } finally { saving = false }
        }
    }

    fun cancelSubmission() { submission?.cancel() }
    fun reportIndexWarning() { indexWarning = true }
    fun requestLeave(action: () -> Unit) { if (!saving) action() }
}

internal fun confirmationFailureMessage(reason: ConfirmationFailure) = when (reason) {
    ConfirmationFailure.SOURCE_CHANGED -> R.string.batch_review_source_changed
    ConfirmationFailure.SOURCE_UNAVAILABLE -> R.string.detail_source_failed
    ConfirmationFailure.CONFIRMATION_CHANGED -> R.string.detail_review_changed
    ConfirmationFailure.CANDIDATE_CHANGED -> R.string.batch_review_candidate_changed
    else -> R.string.detail_review_save_failed
}
