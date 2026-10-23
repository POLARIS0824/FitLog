package com.example.fitlog.log

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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

internal class BatchReviewViewModel internal constructor(
    val route: FitLogRoute.BatchReview,
    private val savedState: SavedStateHandle,
    private val index: Flow<SourceIndexSnapshot>,
    private val analysis: DiaryVaultAnalysisReader,
    private val config: Flow<VaultConfigState>,
    private val confirm: suspend (IndexedSource, DiaryConfirmation) -> DiaryConfirmationResult,
    private val refreshIndex: () -> Unit,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        BatchReviewUiState()
    )
    val uiState: StateFlow<BatchReviewUiState> = _uiState.asStateFlow()

    private inline fun updateState(transform: (BatchReviewUiState) -> BatchReviewUiState) {
        _uiState.update { transform(it) }
    }

    private val items get() = _uiState.value.items
    private val selected get() = _uiState.value.selected
    private val loading get() = _uiState.value.loading
    private val readFailed get() = _uiState.value.readFailed
    private val activeVault get() = _uiState.value.activeVault
    private val saving get() = _uiState.value.saving
    private val message get() = _uiState.value.message
    private val progress get() = _uiState.value.progress
    private val indexWarning get() = _uiState.value.indexWarning
    private var initialized = savedState.get<Boolean>("initialized") ?: false
    private var observation: Job? = null
    private var submission: Job? = null

    init { observe() }

    private fun observe() {
        observation?.cancel(); updateState { current -> current.copy(loading = true, readFailed = false) }
        observation = viewModelScope.launch {
            try {
                combine(index, analysis.observeSummaries(route.vaultId), config) { snapshot, summaries, value ->
                    Triple(snapshot, summaries, value)
                }.collect { (snapshot, summaries, value) ->
                    if (value !is VaultConfigState.Loading) {
                        updateState { current -> current.copy(activeVault = (value as? VaultConfigState.Configured)?.vaultId == route.vaultId) }
                        if (!activeVault) cancelSubmission()
                    }
                    // An empty initial index is still loading, not proof that every source is missing.
                    if (snapshot.sources.isEmpty() && snapshot.refreshing && snapshot.scan == null) {
                        updateState { current -> current.copy(loading = true) }
                        return@collect
                    }
                    val sources = snapshot.sources.associateBy { it.path }
                    updateState { current -> current.copy(items = summaries.filter { summary -> summary.candidateReadFailed ||
                        (summary.candidate != null && summary.confirmed?.parseRunId != summary.candidate.attempt.id)
                    }.map { summary ->
                        val source = sources[summary.sourceKey.relPath]
                        BatchReviewItem(source, summary, assessBatchReview(summary, source?.let(::indexedVersion),
                            source?.status == IndexedSource.AVAILABLE))
                    }.sortedByDescending { it.summary.sourceKey.relPath }) }
                    val eligible = items.filter { it.selectable }.mapTo(mutableSetOf()) { it.id }
                    val retained = selected.intersect(eligible)
                    if (retained != selected && !saving) updateState { current -> current.copy(message = R.string.batch_review_selection_changed) }
                    storeSelection(retained)
                    if (!initialized && !(snapshot.sources.isEmpty() && snapshot.refreshing)) {
                        storeSelection(items.filter { it.assessment.eligibility == BatchReviewEligibility.READY }.mapTo(mutableSetOf()) { it.id })
                        initialized = true; savedState["initialized"] = true
                    }
                    updateState { current -> current.copy(loading = value is VaultConfigState.Loading) }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                updateState { current -> current.copy(readFailed = true, loading = false) }
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

    private fun storeSelection(value: Set<String>) { updateState { current -> current.copy(selected = value) } }

    fun submit() {
        if (saving || loading || readFailed || !activeVault) return
        val chosen = items.filter { it.id in selected && it.selectable }.toList()
        if (chosen.isEmpty()) return
        updateState { current -> current.copy(saving = true, message = null, progress = BatchConfirmationProgress(total = chosen.size)) }
        submission = viewModelScope.launch {
            try {
                for (item in chosen) {
                    currentCoroutineContext().ensureActive()
                    val current = items.firstOrNull { it.id == item.id && it.selectable }
                    if (current == null || item.id !in selected) {
                        updateState { current -> current.copy(progress = progress.copy(skipped = progress.skipped + 1)) }; continue
                    }
                    try {
                        val candidate = requireNotNull(item.summary.candidate)
                        val request = DiaryConfirmation.fromCandidate(candidate.attempt.id, candidate.analysis,
                            requireNotNull(item.assessment.date)).copy(requireLatestCandidate = true)
                        when (val result = confirm(requireNotNull(item.source), request)) {
                            is DiaryConfirmationResult.Confirmed -> {
                                updateState { current -> current.copy(progress = progress.copy(confirmed = progress.confirmed + 1)) }
                                storeSelection(selected - item.id)
                            }
                            is DiaryConfirmationResult.Invalid -> {
                                updateState { current -> current.copy(progress = progress.copy(skipped = progress.skipped + 1)) }
                                storeSelection(selected - item.id)
                                updateState { current -> current.copy(message = confirmationFailureMessage(result.reason)) }
                            }
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        updateState { current -> current.copy(progress = progress.copy(failed = progress.failed + 1, stopped = true), message = R.string.detail_review_save_failed) }
                        break
                    }
                }
            } catch (e: CancellationException) {
                updateState { current -> current.copy(progress = progress.copy(stopped = true)) }
                throw e
            } finally { updateState { current -> current.copy(saving = false) } }
        }
    }

    fun cancelSubmission() { submission?.cancel() }
    fun reportIndexWarning() { updateState { current -> current.copy(indexWarning = true) } }
    fun onStopped(changingConfiguration: Boolean) { if (!changingConfiguration) cancelSubmission() }
}

internal fun confirmationFailureMessage(reason: ConfirmationFailure) = when (reason) {
    ConfirmationFailure.SOURCE_CHANGED -> R.string.batch_review_source_changed
    ConfirmationFailure.SOURCE_UNAVAILABLE -> R.string.detail_source_failed
    ConfirmationFailure.CONFIRMATION_CHANGED -> R.string.detail_review_changed
    ConfirmationFailure.CANDIDATE_CHANGED -> R.string.batch_review_candidate_changed
    else -> R.string.detail_review_save_failed
}
