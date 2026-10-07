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

enum class DiaryDetailTab { ORIGINAL, ANALYSIS }

/** Immutable route identity keeps old reads isolated from another diary or vault. */
class DiaryDetailViewModel(
    val route: FitLogRoute.DiaryDetail,
    private val documents: MarkdownDocuments,
    private val analysis: DiaryAnalysisReader,
    private val savedState: SavedStateHandle,
    private val parseDiary: suspend () -> StoredDiaryParse,
    private val hashingDispatcher: CoroutineDispatcher = Dispatchers.Default,
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
    val confirmationStatus get() = confirmationFreshness(confirmed?.diary, contentVersion)
    val candidateStatus get() = parses.latestCandidate?.let {
        resultFreshness(DiaryContentVersion(it.analysis.parseKey.contentHash, it.analysis.parseKey.hashVersion), contentVersion)
    }
    val canEdit get() = !sourceLoading && original?.file?.writable == true

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
                analysis.observeConfirmed(sourceKey).collect {
                    currentCoroutineContext().ensureActive()
                    if (generation == analysisGeneration) { confirmed = it; confirmationLoading = false }
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
        if (parsing || sourceLoading) return
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
