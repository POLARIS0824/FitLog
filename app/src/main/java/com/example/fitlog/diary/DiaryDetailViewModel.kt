package com.example.fitlog.diary

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
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
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class DiaryDetailTab { ORIGINAL, ANALYSIS }

/** Immutable route identity keeps old reads isolated from another diary or vault. */
class DiaryDetailViewModel(
    val route: FitLogRoute.DiaryDetail,
    private val documents: MarkdownDocuments,
    private val analysis: DiaryAnalysisReader,
    private val savedState: SavedStateHandle,
    private val parseDiary: suspend () -> StoredDiaryParse,
    private val hashingDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val confirmDiary: (suspend (DiaryConfirmation) -> DiaryConfirmationResult)? = null,
) : ViewModel() {
    val sourceKey = SourceKey(route.vaultId, route.relPath)
    var tab by mutableStateOf(DiaryDetailTab.entries.firstOrNull {
        it.name == savedState.get<String>("detailTab")
    } ?: DiaryDetailTab.ORIGINAL); private set
    var original by mutableStateOf<MarkdownSnapshot?>(null); private set
    var sourceLoading by mutableStateOf(false); private set
    var sourceReadFailed by mutableStateOf(false); private set
    var contentVersion by mutableStateOf<DiaryContentVersion?>(null); private set
    var parses by mutableStateOf(DiaryParseRecords()); private set
    var parsesLoading by mutableStateOf(true); private set
    var parsesReadFailed by mutableStateOf(false); private set
    var confirmed by mutableStateOf<ConfirmedDiaryRecord?>(null); private set
    var confirmationLoading by mutableStateOf(true); private set
    var confirmationReadFailed by mutableStateOf(false); private set
    var parsing by mutableStateOf(false); private set
    var parseMessage by mutableStateOf<Int?>(null); private set
    private val restoredReview = runCatching { savedState.get<String>("reviewDraft")?.let {
        Json.decodeFromString<DiaryReviewDraft>(it)
    } }
    internal var review by mutableStateOf(restoredReview.getOrNull()); private set
    var reviewSaving by mutableStateOf(false); private set
    var reviewMessage by mutableStateOf<Int?>(if (restoredReview.isFailure) R.string.detail_review_restore_failed else null); private set
    var leaveRequested by mutableStateOf(false); private set
    var showCandidate by mutableStateOf(savedState.get<Boolean>("showCandidate") ?: false); private set
    private var pendingLeave: (() -> Unit)? = null
    private var confirmationOriginal: StoredDiaryCandidate? = null
    private val selectedConfirmedOriginal get() = confirmationOriginal?.takeIf { it.attempt.id == confirmed?.diary?.parseRunId }
        ?: parses.latestCandidate?.takeIf { it.attempt.id == confirmed?.diary?.parseRunId }
    val canReview get() = confirmDiary != null && restoredReview.isSuccess && !reviewSaving && !parsing &&
        !confirmationLoading && !parsesLoading && !confirmationReadFailed && (review != null ||
        ((showCandidate || confirmed == null) && parses.latestCandidate != null) ||
        (!showCandidate && confirmed != null && selectedConfirmedOriginal != null))
    val confirmationStatus get() = confirmationFreshness(confirmed?.diary, contentVersion)
    val candidateStatus get() = parses.latestCandidate?.let {
        resultFreshness(DiaryContentVersion(it.analysis.parseKey.contentHash, it.analysis.parseKey.hashVersion), contentVersion)
    }
    val canEdit get() = original?.file?.writable == true

    private var sourceJob: Job? = null
    private var parsesJob: Job? = null
    private var confirmationJob: Job? = null
    private var parseJob: Job? = null
    private var sourceGeneration = 0L
    private var analysisGeneration = 0L

    init { reloadOriginal(); retryAnalysis() }

    fun selectTab(tab: DiaryDetailTab) {
        this.tab = tab
        savedState["detailTab"] = tab.name
    }

    /** Called on resume, after returning from Editor, and by the refresh action. */
    fun refresh() {
        reloadOriginal()
        if (parsesReadFailed || confirmationReadFailed || parses.candidateReadFailed) retryAnalysis()
    }

    fun reloadOriginal() {
        if (sourceJob?.isActive == true) return
        val generation = ++sourceGeneration
        sourceLoading = true
        sourceReadFailed = false
        contentVersion = null
        sourceJob = viewModelScope.launch {
            try {
                val snapshot = documents.read(route.document)
                val version = withContext(hashingDispatcher) {
                    val normalized = ContentTextSnapshot.fromRawText((if (snapshot.bom) "\uFEFF" else "") + snapshot.text)
                    DiaryContentVersion(normalized.hash, normalized.hashVersion)
                }
                currentCoroutineContext().ensureActive()
                if (generation != sourceGeneration) return@launch
                original = snapshot
                contentVersion = version
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                currentCoroutineContext().ensureActive()
                if (generation == sourceGeneration) {
                    original = null
                    contentVersion = null
                    sourceReadFailed = true
                }
            } finally {
                if (generation == sourceGeneration) sourceLoading = false
            }
        }
    }

    fun retryAnalysis() {
        val generation = ++analysisGeneration
        parsesJob?.cancel()
        confirmationJob?.cancel()
        parsesLoading = true
        confirmationLoading = true
        parsesReadFailed = false
        confirmationReadFailed = false
        // Separate collectors keep confirmation available if candidate decoding fails.
        parsesJob = viewModelScope.launch {
            try {
                analysis.observeParses(sourceKey).collect {
                    currentCoroutineContext().ensureActive()
                    if (generation == analysisGeneration) { parses = it; parsesLoading = false }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                currentCoroutineContext().ensureActive()
                if (generation == analysisGeneration) { parsesReadFailed = true; parsesLoading = false }
            }
        }
        confirmationJob = viewModelScope.launch {
            try {
                analysis.observeConfirmed(sourceKey).collect { record ->
                    val targetRun = if (confirmDiary != null) review?.parseRunId ?: record?.diary?.parseRunId else null
                    try {
                        val original = targetRun?.let { requireNotNull(analysis.readCandidate(sourceKey, it)) }
                        currentCoroutineContext().ensureActive()
                        if (generation == analysisGeneration) {
                            confirmed = record
                            confirmationOriginal = original?.takeIf { it.attempt.id == record?.diary?.parseRunId }
                            review?.let { draft ->
                                if (original != null) {
                                    val restored = draft.withOriginalValues(original)
                                    review = restored; savedState["reviewDraft"] = Json.encodeToString(restored)
                                }
                            }
                            confirmationReadFailed = false; confirmationLoading = false
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        currentCoroutineContext().ensureActive()
                        if (generation == analysisGeneration) {
                            // A damaged parse must not hide the independently stored confirmation.
                            confirmed = record; confirmationOriginal = null
                            confirmationReadFailed = true; confirmationLoading = false
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                currentCoroutineContext().ensureActive()
                if (generation == analysisGeneration) { confirmationReadFailed = true; confirmationLoading = false }
            }
        }
    }

    /** Button guard and cancellation belong to this route, not to a global executor. */
    fun parse() {
        if (parsing || sourceLoading || reviewSaving || review != null) return
        parsing = true; parseMessage = null
        selectTab(DiaryDetailTab.ANALYSIS)
        parseJob = viewModelScope.launch {
            try {
                val stored = parseDiary()
                currentCoroutineContext().ensureActive()
                parseMessage = when (val result = stored.result) {
                    is DiaryParseResult.Success -> R.string.ai_parse_saved
                    is DiaryParseResult.Failure -> aiRequestMessage(result.reason)
                }
                // The file may have changed during inference. Compare against a fresh read.
                reloadOriginal()
                if (parsesReadFailed || confirmationReadFailed || parses.candidateReadFailed) retryAnalysis()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                currentCoroutineContext().ensureActive()
                parseMessage = when (e) {
                    is AiConfigurationException -> aiConfigurationMessage(e.reason)
                    is DiaryOriginalReadException -> {
                        sourceReadFailed = true; contentVersion = null; original = null
                        R.string.detail_source_failed
                    }
                    is DiaryAnalysisStorageException -> R.string.ai_parse_save_failed
                    else -> R.string.ai_request_failed
                }
            } finally { if (currentCoroutineContext().isActive) parsing = false }
        }
    }

    fun cancelParse() {
        if (!parsing) return
        parseJob?.cancel(); parsing = false; parseMessage = R.string.ai_parse_cancelled
    }

    fun selectResult(candidate: Boolean) {
        if (review != null || reviewSaving) return
        showCandidate = candidate
        savedState["showCandidate"] = candidate
        reviewMessage = null
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
        val selected = selectedReviewDraft()?.sessions?.getOrNull(address.session)?.exercises?.getOrNull(address.exercise)?.sets?.getOrNull(address.set)
        val original = draft.originalValues.firstOrNull { it.address == address }
        val updatedBasis = basis ?: current.basis
        val weight = DiaryWeightValue(value, unit, converted, updatedBasis)
            .restoringConversionRoundoff(selected?.let { DiaryWeightValue(it.weight, it.unit, basis = it.basis) })
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

    private fun draftForReview(): DiaryReviewDraft? = review ?: selectedReviewDraft()

    private fun selectedReviewDraft(): DiaryReviewDraft? = if (!showCandidate && confirmed != null)
        selectedConfirmedOriginal?.let { DiaryReviewDraft.fromConfirmed(requireNotNull(confirmed), it) }
        else parses.latestCandidate?.let { DiaryReviewDraft.fromCandidate(it, confirmed) }

    private fun storeEditedReview(draft: DiaryReviewDraft) = storeReview(draft.takeUnless { it == selectedReviewDraft() })

    private fun storeReview(value: DiaryReviewDraft?) {
        review = value
        if (value == null) savedState.remove<String>("reviewDraft")
        else savedState["reviewDraft"] = Json.encodeToString(value)
        reviewMessage = null
    }

    fun saveReview(dateText: String, acceptPartial: Boolean) {
        if (!canReview || !beginReview()) return
        val draft = requireNotNull(review).copy(date = dateText, acceptedPartial = acceptPartial)
        storeReview(draft)
        val date = reviewDate(dateText)
        if (date == null) { reviewMessage = R.string.detail_review_date_invalid; return }
        if (draft.partial && !acceptPartial) { reviewMessage = R.string.detail_review_partial_required; return }
        if (confirmed?.diary?.confirmedAt != draft.expectedConfirmedAt) {
            reviewMessage = R.string.detail_review_changed; return
        }
        reviewSaving = true
        viewModelScope.launch {
            try {
                val request = DiaryConfirmation(sourceKey, draft.parseRunId, date, draft.sessions, acceptPartial)
                when (val result = requireNotNull(confirmDiary).invoke(request)) {
                    is DiaryConfirmationResult.Confirmed -> {
                        confirmed = result.diary
                        storeReview(null)
                        showCandidate = false; savedState["showCandidate"] = false
                        reviewMessage = R.string.detail_review_saved
                    }
                    is DiaryConfirmationResult.Invalid -> reviewMessage = R.string.detail_review_save_failed
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                reviewMessage = R.string.detail_review_save_failed
            } finally { if (currentCoroutineContext().isActive) reviewSaving = false }
        }
    }

    /** All ways out share this guard; the draft also survives normal process restoration. */
    fun requestLeave(action: () -> Unit) {
        if (reviewSaving) return
        if (review != null) { pendingLeave = action; leaveRequested = true }
        else { cancelParse(); action() }
    }

    fun cancelLeave() { pendingLeave = null; leaveRequested = false }
    fun discardAndLeave() {
        if (reviewSaving) return
        val action = pendingLeave
        storeReview(null); cancelLeave(); cancelParse(); action?.invoke()
    }

    fun editorRoute(): FitLogRoute.Editor? = if (!canEdit) null else FitLogRoute.Editor(
        vaultUri = route.vaultUri,
        document = route.document,
        directory = original?.file?.directory ?: route.directory,
        fileName = original?.file?.name ?: route.fileName,
        displayPath = route.relPath,
        vaultId = route.vaultId,
    )

    override fun onCleared() {
        sourceGeneration++
        analysisGeneration++
        super.onCleared()
    }
}
