package com.example.fitlog.log

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fitlog.R
import com.example.fitlog.data.vault.*
import com.example.fitlog.data.index.*
import com.example.fitlog.data.analysis.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.util.Locale
import java.time.LocalDate

internal fun filterAndSortFiles(files: List<MarkdownFile>, query: String, order: LogSortOrder): List<MarkdownFile> {
    val term = query.trim()
    val comparator = compareBy<MarkdownFile> { it.name.lowercase(Locale.ROOT) }
        .thenBy { it.path.lowercase(Locale.ROOT) }.thenBy { it.path }.thenBy { it.uri }
    return files.filter { it.name.contains(term, ignoreCase = true) || it.path.contains(term, ignoreCase = true) }
        .sortedWith(if (order == LogSortOrder.Ascending) comparator else comparator.reversed())
}

class LogViewModel(
    private val savedState: SavedStateHandle,
    private val config: Flow<VaultConfigState>,
    private val index: SourceIndexRepository,
    private val settings: LogSettingsStore,
    private val analysis: DiaryVaultAnalysisReader? = null,
    private val controller: DiaryAnalysisController? = null,
) : ViewModel() {
    var vault by mutableStateOf<String?>(null); private set
    var vaultId by mutableStateOf<String?>(null); private set
    var files by mutableStateOf<List<MarkdownFile>>(emptyList()); private set
    var query by mutableStateOf(savedState.get<String>("query") ?: ""); private set
    var sort by mutableStateOf(LogSortOrder.Descending); private set
    var sortBusy by mutableStateOf(true); private set
    var sortError by mutableStateOf<Int?>(null); private set
    var loading by mutableStateOf(true); private set
    var refreshing by mutableStateOf(false); private set
    var partial by mutableStateOf(false); private set
    var error by mutableStateOf<Int?>(null); private set
    var scanStatus by mutableStateOf<String?>(null); private set
    var showMissing by mutableStateOf(savedState.get<Boolean>("showMissing") ?: false); private set
    var sources by mutableStateOf<List<IndexedSource>>(emptyList()); private set
    var summaries by mutableStateOf<Map<String, DiaryAnalysisSummary>>(emptyMap()); private set
    var summariesLoading by mutableStateOf(analysis != null); private set
    var summariesReadFailed by mutableStateOf(false); private set
    var run by mutableStateOf(DiaryAnalysisRun()); private set
    var analysisBusy by mutableStateOf(false); private set
    private var dismissedRunItems by mutableStateOf<Set<AnalysisItemResult>>(emptySet())
    var statusFilter by mutableStateOf(LogStatusFilter.entries.firstOrNull {
        it.name == savedState.get<String>("statusFilter")
    } ?: LogStatusFilter.ALL); private set
    val statuses by derivedStateOf {
        sources.associate { source -> source.path to diaryWorkStatus(source, summaries[source.path],
            running = run.active && run.current == SourceKey(source.vaultId, source.path),
            unavailable = summariesReadFailed || summariesLoading,
            readFailed = run.items.any { it !in dismissedRunItems && it.source.key.relPath == source.path && it.issue?.problem == AnalysisProblem.SOURCE },
            operationFailed = run.items.any { it !in dismissedRunItems && it.source.key.relPath == source.path && it.outcome == AnalysisItemOutcome.FAILED },
            changedDuringAnalysis = run.items.any { it !in dismissedRunItems && it.source.key.relPath == source.path && it.issue?.problem == AnalysisProblem.SOURCE_CHANGED }) }
    }
    val pendingCount by derivedStateOf { sources.count { source ->
        val summary = summaries[source.path]
        summary != null && (summary.candidateReadFailed ||
            (summary.candidate != null && summary.confirmed?.parseRunId != summary.candidate.attempt.id))
    } }
    val visibleFiles by derivedStateOf {
        filterAndSortFiles(sources.filter { (showMissing || it.status != IndexedSource.MISSING) &&
            statusFilter.matches(statuses[it.path] ?: LogDiaryStatus.UNPARSED) }.map { it.file() }, query, sort)
    }
    fun toggleMissing() { showMissing = !showMissing; savedState["showMissing"] = showMissing }
    private var currentConfig: VaultConfigState = VaultConfigState.Loading
    private var configJob: Job? = null
    private var indexJob: Job? = null
    private var summaryJob: Job? = null

    init {
        viewModelScope.launch {
            try { sort = settings.readSort() }
            catch (e: Exception) { if (e is CancellationException) throw e; sortError = R.string.log_sort_failed }
            finally { sortBusy = false }
        }
        observeConfig()
        controller?.let { runner -> viewModelScope.launch { runner.state.collect { value ->
            if (value.active && !analysisBusy) dismissedRunItems = emptySet()
            analysisBusy = value.active
            run = value.takeIf { it.vaultId == vaultId } ?: DiaryAnalysisRun()
        } } }
    }

    private fun observeConfig(refreshAfterRead: Boolean = false) {
        configJob?.cancel()
        configJob = viewModelScope.launch {
            config.distinctUntilChanged().collect { value ->
                currentConfig = value
                val nextVault = (value as? VaultConfigState.Configured)?.uri?.toString()
                val nextId = (value as? VaultConfigState.Configured)?.vaultId
                if (vaultId != nextId) {
                    indexJob?.cancel()
                    summaryJob?.cancel(); summaryJob = null
                    summaries = emptyMap(); summariesLoading = analysis != null && nextId != null; summariesReadFailed = false
                    run = controller?.state?.value?.takeIf { it.vaultId == nextId } ?: DiaryAnalysisRun()
                    files = emptyList(); sources = emptyList(); partial = false; scanStatus = null; refreshing = false
                }
                vault = nextVault
                vaultId = nextId
                error = if (value is VaultConfigState.Failed) R.string.vault_error_load_config_failed else null
                loading = value is VaultConfigState.Loading || nextVault != null
                nextId?.let {
                    observeIndex(it)
                    observeSummaries(it)
                    if (refreshAfterRead) index.refresh(it)
                }
            }
        }
    }

    fun search(value: String) { query = value; savedState["query"] = value }
    fun filter(value: LogStatusFilter) { statusFilter = value; savedState["statusFilter"] = value.name }

    private fun observeSummaries(id: String) {
        val reader = analysis ?: return
        if (summaryJob?.isActive == true) return
        summariesLoading = true; summariesReadFailed = false
        summaryJob = viewModelScope.launch {
            try { reader.observeSummaries(id).collect { value ->
                if (vaultId == id) { summaries = value.associateBy { it.sourceKey.relPath }; summariesLoading = false }
            } } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (vaultId == id) { summariesReadFailed = true; summariesLoading = false }
            }
        }
    }

    fun startAnalysis(range: LogAnalysisRange, reanalyze: Boolean) {
        if (analysisBusy || refreshing || summariesLoading || summariesReadFailed) return
        val matched = matchAnalysisSources(sources, summaries, range, LocalDate.now())
        controller?.startBatch(matched.map { AnalysisSource(SourceKey(it.vaultId, it.path), it.uri) }, reanalyze)
    }
    fun cancelAnalysis() { controller?.cancel() }

    private fun observeIndex(source: String) {
        if (indexJob?.isActive == true) return
        indexJob = viewModelScope.launch {
            try {
                index.observe(source).collect { snapshot ->
                    if (vaultId != source) return@collect
                    sources = snapshot.sources
                    files = sources.map { it.file() }
                    scanStatus = snapshot.scan?.status
                    partial = scanStatus == IndexedScan.PARTIAL
                    refreshing = snapshot.refreshing
                    loading = sources.isEmpty() && refreshing && snapshot.scan == null
                    error = if (snapshot.refreshFailed || scanStatus == IndexedScan.FAILED) R.string.log_failed else null
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (vaultId == source) { error = R.string.index_read_failed; loading = false; refreshing = false }
            }
        }
    }

    fun changeSort(order: LogSortOrder) {
        if (sortBusy) return
        sortBusy = true
        sortError = null
        viewModelScope.launch {
            try { settings.saveSort(order); sort = order }
            catch (e: Exception) { if (e is CancellationException) throw e; sortError = R.string.log_sort_failed }
            finally { sortBusy = false }
        }
    }

    fun refresh() {
        dismissedRunItems = run.items.toSet()
        vaultId?.let(::observeIndex)
        vaultId?.let(::observeSummaries)
        if (currentConfig is VaultConfigState.Failed) observeConfig(refreshAfterRead = true)
        else vaultId?.let(index::refresh)
    }
}
