package com.example.fitlog.diary

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import androidx.lifecycle.viewModelScope
import com.example.fitlog.R
import com.example.fitlog.ai.aiConfigurationMessage
import com.example.fitlog.ai.aiRequestMessage
import com.example.fitlog.data.ai.AiConfigurationException
import com.example.fitlog.data.analysis.*
import com.example.fitlog.data.analysis.adapter.DiaryOriginalReadException
import com.example.fitlog.data.hash.ContentTextSnapshot
import com.example.fitlog.data.vault.MarkdownDocuments
import com.example.fitlog.data.vault.MarkdownSnapshot
import com.example.fitlog.navigation.FitLogRoute
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import com.example.fitlog.log.confirmationFailureMessage
import kotlinx.coroutines.withContext

enum class DiaryDetailTab { ORIGINAL, ANALYSIS }

/** Immutable route identity keeps old reads isolated from another diary or vault. */
internal class DiaryDetailViewModel(
    val route: FitLogRoute.DiaryDetail,
    private val documents: MarkdownDocuments,
    private val analysis: DiaryAnalysisReader,
    private val savedState: SavedStateHandle,
    private val parseDiary: suspend () -> StoredDiaryParse,
    private val hashingDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val confirmDiary: (suspend (DiaryConfirmation) -> DiaryConfirmationResult)? = null,
    private val analysisState: StateFlow<DiaryAnalysisRun>? = null,
    private val cancelAnalysis: () -> Unit = {},
    private val deleteConfirmation: suspend () -> Unit = {},
    private val onSourceRead: suspend (MarkdownSnapshot) -> Unit = {},
) : ViewModel() {
    val sourceKey = SourceKey(route.vaultId, route.relPath)
    private val _uiState = MutableStateFlow(
        DiaryDetailUiState(
            route = route,
            reviewAvailable = confirmDiary != null,
            tab = DiaryDetailTab.entries.firstOrNull { it.name == savedState.get<String>("detailTab") }
                ?: if (route.openAnalysis || route.openCandidate) DiaryDetailTab.ANALYSIS else DiaryDetailTab.ORIGINAL,
            parsesLoading = true,
            confirmationLoading = true,
            reviewMessage = if (savedState.get<Boolean>("reviewPending") == true) R.string.detail_review_restore_failed else null,
            showCandidate = savedState.get<Boolean>("showCandidate") ?: route.openCandidate,
        ).withDerivedValues()
    )
    val uiState: StateFlow<DiaryDetailUiState> = _uiState.asStateFlow()

    private inline fun updateState(transform: (DiaryDetailUiState) -> DiaryDetailUiState) {
        _uiState.update { transform(it).withDerivedValues() }
    }

    private val tab get() = _uiState.value.tab
    private val original get() = _uiState.value.original
    private val sourceLoading get() = _uiState.value.sourceLoading
    private val sourceReadFailed get() = _uiState.value.sourceReadFailed
    private val contentVersion get() = _uiState.value.contentVersion
    private val parses get() = _uiState.value.parses
    private val parsesLoading get() = _uiState.value.parsesLoading
    private val parsesReadFailed get() = _uiState.value.parsesReadFailed
    private val confirmed get() = _uiState.value.confirmed
    private val confirmationLoading get() = _uiState.value.confirmationLoading
    private val confirmationReadFailed get() = _uiState.value.confirmationReadFailed
    private val parsing get() = _uiState.value.parsing
    private val analysisBusy get() = _uiState.value.analysisBusy
    private val analysisIndexWarning get() = _uiState.value.analysisIndexWarning
    private val parseMessage get() = _uiState.value.parseMessage
    private val review get() = _uiState.value.review
    private val reviewSaving get() = _uiState.value.reviewSaving
    private val reviewMessage get() = _uiState.value.reviewMessage
    private val leaveRequested get() = _uiState.value.leaveRequested
    private val showCandidate get() = _uiState.value.showCandidate
    private var confirmationOriginal: StoredDiaryCandidate? = null
    private val selectedConfirmedOriginal get() = confirmationOriginal?.takeIf { it.attempt.id == confirmed?.diary?.parseRunId }
        ?: parses.latestCandidate?.takeIf { it.attempt.id == confirmed?.diary?.parseRunId }
    private val canReview get() = _uiState.value.canReview

    private var sourceJob: Job? = null
    private var parsesJob: Job? = null
    private var confirmationJob: Job? = null
    private var parseJob: Job? = null
    private var sourceGeneration = 0L
    private var analysisGeneration = 0L
    private var started = false

    init {
        reloadOriginal(); retryAnalysis()
        analysisState?.let { state -> viewModelScope.launch { state.collect { value ->
            updateState { current -> current.copy(analysisBusy = value.active, analysisIndexWarning = value.indexWarning) }
            if (value.vaultId == sourceKey.vaultId && !value.active && value.stopped && parsing) {
                updateState { current -> current.copy(parsing = false, parseMessage = R.string.ai_parse_cancelled) }
            }
        } } }
    }

    fun selectTab(tab: DiaryDetailTab) {
        updateState { current -> current.copy(tab = tab) }
        savedState["detailTab"] = tab.name
    }

    /** Start checks external edits; predictive-back STARTED/RESUMED changes must not refresh. */
    fun onStart() {
        if (started) refresh() else started = true
    }

    /** Re-read the external source on return or explicit refresh. */
    fun refresh() {
        reloadOriginal()
        if (parsesReadFailed || confirmationReadFailed || parses.candidateReadFailed) retryAnalysis()
    }

    fun reloadOriginal() {
        if (sourceJob?.isActive == true) return
        val generation = ++sourceGeneration
        updateState { current -> current.copy(sourceLoading = true, sourceReadFailed = false) }
        sourceJob = viewModelScope.launch {
            try {
                val snapshot = documents.read(route.document)
                val version = withContext(hashingDispatcher) {
                    val normalized = ContentTextSnapshot.fromRawText((if (snapshot.bom) "\uFEFF" else "") + snapshot.text)
                    DiaryContentVersion(normalized.hash, normalized.hashVersion)
                }
                currentCoroutineContext().ensureActive()
                if (generation != sourceGeneration) return@launch
                updateState { current -> current.copy(original = snapshot, contentVersion = version) }
                try { onSourceRead(snapshot) }
                catch (e: Exception) { if (e is CancellationException) throw e; updateState { current -> current.copy(analysisIndexWarning = true) } }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                currentCoroutineContext().ensureActive()
                if (generation == sourceGeneration) {
                    updateState { current -> current.copy(original = null, contentVersion = null, sourceReadFailed = true) }
                }
            } finally {
                if (generation == sourceGeneration) updateState { current -> current.copy(sourceLoading = false) }
            }
        }
    }

    fun retryAnalysis() {
        val generation = ++analysisGeneration
        parsesJob?.cancel()
        confirmationJob?.cancel()
        updateState { current -> current.copy(parsesLoading = true, confirmationLoading = true, parsesReadFailed = false, confirmationReadFailed = false) }
        // Separate collectors keep confirmation available if candidate decoding fails.
        parsesJob = viewModelScope.launch {
            try {
                analysis.observeParses(sourceKey).collect {
                    currentCoroutineContext().ensureActive()
                    if (generation == analysisGeneration) { updateState { current -> current.copy(parses = it, parsesLoading = false) } }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                currentCoroutineContext().ensureActive()
                if (generation == analysisGeneration) { updateState { current -> current.copy(parsesReadFailed = true, parsesLoading = false) } }
            }
        }
        confirmationJob = viewModelScope.launch {
            try {
                analysis.observeConfirmed(sourceKey).collect { record ->
                    val targetRun = if (confirmDiary != null) review?.parseRunId ?: record?.diary?.parseRunId else null
                    try {
                        val original = try { targetRun?.let { analysis.readCandidate(sourceKey, it) } }
                        catch (e: Exception) { if (e is CancellationException) throw e; null }
                        currentCoroutineContext().ensureActive()
                        if (generation == analysisGeneration) {
                            updateState { current -> current.copy(confirmed = record) }
                            confirmationOriginal = original?.takeIf { it.attempt.id == record?.diary?.parseRunId }
                            review?.let { draft ->
                                if (original != null) {
                                    val restored = draft.withOriginalValues(original)
                                    updateState { current -> current.copy(review = restored) }
                                }
                            }
                            updateState { current -> current.copy(confirmationReadFailed = false, confirmationLoading = false) }
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        currentCoroutineContext().ensureActive()
                        if (generation == analysisGeneration) {
                            // A damaged parse must not hide the independently stored confirmation.
                            updateState { current -> current.copy(confirmed = record) }; confirmationOriginal = null
                            updateState { current -> current.copy(confirmationReadFailed = false, confirmationLoading = false) }
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                currentCoroutineContext().ensureActive()
                if (generation == analysisGeneration) { updateState { current -> current.copy(confirmationReadFailed = true, confirmationLoading = false) } }
            }
        }
    }

    /** The route observes its request; the shared controller owns its lifetime across navigation. */
    fun parse() {
        if (parsing || analysisBusy || sourceLoading || reviewSaving || review != null) return
        updateState { current -> current.copy(parsing = true, parseMessage = null) }
        selectTab(DiaryDetailTab.ANALYSIS)
        parseJob = viewModelScope.launch {
            try {
                val stored = parseDiary()
                currentCoroutineContext().ensureActive()
                updateState { current -> current.copy(parseMessage = when (val result = stored.result) {
                    is DiaryParseResult.Success -> R.string.ai_parse_saved
                    is DiaryParseResult.Failure ->
                        result.reason.toAiRequestFailure()?.let(::aiRequestMessage)
                            ?: R.string.ai_request_failed
                }) }
                // The file may have changed during inference. Compare against a fresh read.
                reloadOriginal()
                if (parsesReadFailed || confirmationReadFailed || parses.candidateReadFailed) retryAnalysis()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                currentCoroutineContext().ensureActive()
                if (e is DiaryOriginalReadException) {
                    updateState { it.copy(sourceReadFailed = true, contentVersion = null, original = null) }
                }
                val message = when (e) {
                    is AiConfigurationException -> aiConfigurationMessage(e.reason)
                    is DiaryOriginalReadException -> R.string.detail_source_failed
                    is DiaryAnalysisStorageException -> R.string.ai_parse_save_failed
                    is com.example.fitlog.log.DiaryAnalysisBusyException -> R.string.log_analysis_busy
                    else -> R.string.ai_request_failed
                }
                updateState { it.copy(parseMessage = message) }
            } finally { if (currentCoroutineContext().isActive) updateState { current -> current.copy(parsing = false) } }
        }
    }

    fun cancelParse() {
        if (!parsing) return
        cancelAnalysis()
        parseJob?.cancel(); updateState { current -> current.copy(parsing = false, parseMessage = R.string.ai_parse_cancelled) }
    }

    fun selectResult(candidate: Boolean) {
        if (review != null || reviewSaving) return
        updateState { current -> current.copy(showCandidate = candidate) }
        savedState["showCandidate"] = candidate
        updateState { current -> current.copy(reviewMessage = null) }
    }

    internal fun beginReview(): Boolean {
        if (!canReview) return false
        if (review == null) {
            val draft = selectedReviewDraft() ?: return false
            storeReview(draft)
        }
        return true
    }

    internal fun cancelReviewConfirmation() {
        if (!reviewSaving && review != null && review == selectedReviewDraft()) storeReview(null)
    }

    internal fun updateWeight(address: DiarySetAddress, value: Double?, unit: WeightUnit?, converted: Boolean = false,
        basis: WeightBasis? = null) {
        if (value != null && (!value.isFinite() || value < 0)) return
        val draft = draftForReview() ?: return
        val current = draft.sessions.getOrNull(address.session)?.exercises?.getOrNull(address.exercise)?.sets?.getOrNull(address.set) ?: return
        val original = draft.originalValues.firstOrNull { it.address == address }
        val updatedBasis = basis ?: current.basis
        val weight = DiaryWeightValue(value, unit, converted, updatedBasis)
            .restoringConversionRoundoff(original?.let { DiaryWeightValue(it.weight, it.unit, basis = it.basis) }).weight
        if (current.weight == weight && current.unit == unit && current.basis == updatedBasis) return
        if (beginReview()) storeEditedReview(requireNotNull(review).withWeight(address, weight, unit, updatedBasis))
    }

    internal fun updateReps(address: DiarySetAddress, value: Int?) {
        if (value != null && value <= 0) return
        val current = draftForReview()?.sessions?.getOrNull(address.session)?.exercises?.getOrNull(address.exercise)?.sets?.getOrNull(address.set)
        if (current == null || current.reps == value) return
        if (beginReview()) storeEditedReview(requireNotNull(review).withReps(address, value))
    }

    internal fun updateBasis(address: DiarySetAddress, basis: WeightBasis, applyToExercise: Boolean) {
        val draft = draftForReview() ?: return
        val updated = draft.withBasis(address, basis, applyToExercise)
        if (updated != draft && beginReview()) storeEditedReview(updated)
    }

    internal fun updateExercise(session: Int, exercise: Int, name: String, setCount: Int, fragmentCounts: Map<Int, Int>) {
        if (name.isBlank() || setCount !in 0..1000 || fragmentCounts.values.any { it !in 0..1000 }) return
        val draft = draftForReview() ?: return
        val updated = draft.withExercise(session, exercise, name.trim(), setCount, fragmentCounts)
        if (updated != draft && beginReview()) storeEditedReview(updated)
    }

    internal fun addExercise(session: Int, name: String, setCount: Int) {
        if (name.isBlank() || setCount !in 0..1000) return
        val draft = draftForReview() ?: return
        val updated = draft.addExercise(session, name.trim(), setCount)
        if (updated != draft && beginReview()) storeEditedReview(updated)
    }

    internal fun removeExercise(session: Int, exercise: Int) {
        val draft = draftForReview() ?: return
        val updated = draft.removeExercise(session, exercise)
        if (updated != draft && beginReview()) storeEditedReview(updated)
    }

    internal fun removeSession(session: Int) {
        val draft = draftForReview() ?: return
        val updated = draft.removeSession(session)
        if (updated != draft && beginReview()) storeEditedReview(updated)
    }

    internal fun clearTraining() {
        val draft = draftForReview() ?: return
        val updated = draft.clearTraining()
        if (updated != draft && beginReview()) storeEditedReview(updated)
    }

    /** Read-only snapshot for editors; opening a sheet must not create pending corrections. */
    internal fun draftForReview(): DiaryReviewDraft? = review ?: selectedReviewDraft()

    private fun selectedReviewDraft(): DiaryReviewDraft? = if (!showCandidate && confirmed != null)
        DiaryReviewDraft.fromConfirmed(requireNotNull(confirmed), selectedConfirmedOriginal)
        else parses.latestCandidate?.let { DiaryReviewDraft.fromCandidate(it, confirmed) }

    private fun storeEditedReview(draft: DiaryReviewDraft) = storeReview(draft.takeUnless { it == selectedReviewDraft() })

    private fun storeReview(value: DiaryReviewDraft?) {
        updateState { current -> current.copy(review = value) }
        savedState["reviewPending"] = value != null
        updateState { current -> current.copy(reviewMessage = null) }
    }

    fun saveReview(dateText: String, acceptPartial: Boolean) {
        if (!canReview || !beginReview()) return
        val draft = requireNotNull(review).copy(date = dateText, acceptedPartial = acceptPartial)
        storeReview(draft)
        val date = reviewDate(dateText)
        if (date == null) { updateState { current -> current.copy(reviewMessage = R.string.detail_review_date_invalid) }; return }
        if (draft.partial && !acceptPartial) { updateState { current -> current.copy(reviewMessage = R.string.detail_review_partial_required) }; return }
        if (confirmed?.diary?.confirmedAt != draft.expectedConfirmedAt) {
            updateState { current -> current.copy(reviewMessage = R.string.detail_review_changed) }; return
        }
        updateState { current -> current.copy(reviewSaving = true) }
        viewModelScope.launch {
            try {
                val request = DiaryConfirmation(sourceKey, draft.parseRunId, date, draft.sessions, acceptPartial,
                    expectedConfirmedAt = draft.expectedConfirmedAt, editConfirmed = !draft.fromCandidate,
                    expectedContentVersion = draft.sourceVersion)
                when (val result = requireNotNull(confirmDiary).invoke(request)) {
                    is DiaryConfirmationResult.Confirmed -> {
                        updateState { current -> current.copy(confirmed = result.diary) }
                        storeReview(null)
                        updateState { current -> current.copy(showCandidate = false) }; savedState["showCandidate"] = false
                        updateState { current -> current.copy(reviewMessage = R.string.detail_review_saved) }
                    }
                    is DiaryConfirmationResult.Invalid -> {
                        updateState { current -> current.copy(reviewMessage = confirmationFailureMessage(result.reason)) }
                        if (result.reason == ConfirmationFailure.SOURCE_CHANGED) reloadOriginal()
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                updateState { current -> current.copy(reviewMessage = R.string.detail_review_save_failed) }
            } finally { if (currentCoroutineContext().isActive) updateState { current -> current.copy(reviewSaving = false) } }
        }
    }

    /** All ways out share this guard; in-memory corrections survive configuration changes. */
    fun requestLeave(target: DiaryLeaveTarget = DiaryLeaveTarget.BACK) {
        if (reviewSaving) return
        updateState { it.copy(leaveTarget = target, leaveRequested = review != null, leaveReady = review == null) }
    }
    fun leaveHandled() { updateState { it.copy(leaveTarget = null, leaveReady = false) } }
    fun cancelLeave() { updateState { it.copy(leaveTarget = null, leaveRequested = false, leaveReady = false) } }
    fun discardAndLeave() {
        if (reviewSaving) return
        storeReview(null)
        cancelParse()
        updateState { it.copy(leaveRequested = false, leaveReady = true) }
    }

    fun revokeConfirmation() {
        if (reviewSaving || confirmed == null || review != null) return
        updateState { current -> current.copy(reviewSaving = true) }
        viewModelScope.launch {
            try { deleteConfirmation(); updateState { current -> current.copy(confirmed = null) }; confirmationOriginal = null; updateState { current -> current.copy(showCandidate = true) } }
            catch (e: Exception) { if (e is CancellationException) throw e; updateState { current -> current.copy(reviewMessage = R.string.record_delete_failed) } }
            finally { updateState { current -> current.copy(reviewSaving = false) } }
        }
    }

    override fun onCleared() {
        sourceGeneration++
        analysisGeneration++
        super.onCleared()
    }
}
