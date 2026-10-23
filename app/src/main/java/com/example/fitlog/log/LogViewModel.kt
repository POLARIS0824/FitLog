@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.example.fitlog.log

import com.example.fitlog.log.DiaryAnalysisController
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import androidx.lifecycle.viewModelScope
import com.example.fitlog.R
import com.example.fitlog.data.vault.*
import com.example.fitlog.data.index.*
import com.example.fitlog.data.analysis.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.time.LocalDate

internal class LogViewModel(
    private val savedState: SavedStateHandle,
    private val config: Flow<VaultConfigState>,
    private val index: SourceIndex,
    private val settings: LogSettingsStore,
    private val analysis: DiaryVaultAnalysisReader? = null,
    private val controller: DiaryAnalysisController? = null,
    private val records: TrainingRecords,
    private val computationDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        LogUiState(
            query = savedState.get<String>("query") ?: "",
            sortBusy = true,
            loading = true,
            showMissing = savedState.get<Boolean>("showMissing") ?: false,
            summariesLoading = analysis != null,
            statusFilter = LogStatusFilter.entries.firstOrNull {
                it.name == savedState.get<String>("statusFilter")
            } ?: LogStatusFilter.ALL,
        ).withDerivedValues()
    )
    val uiState: StateFlow<LogUiState> = flow {
        retryRecords()
        observeConfig()
        val runtimeJob = controller?.let { runner -> viewModelScope.launch {
            runner.state.collect { value -> updateState { current -> current.copy(
                dismissedRunItems = if (value.active && !current.analysisBusy) emptySet() else current.dismissedRunItems,
                analysisBusy = value.active,
                run = value.takeIf { it.vaultId == current.vaultId } ?: DiaryAnalysisRun(),
            ) } }
        } }
        try { emitAll(_uiState.mapLatest { withContext(computationDispatcher) { it.withDerivedValues() } }) }
        finally { recordsJob?.cancel(); configJob?.cancel(); indexJob?.cancel(); summaryJob?.cancel(); runtimeJob?.cancel() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), _uiState.value)

    private inline fun updateState(transform: (LogUiState) -> LogUiState) {
        _uiState.update { transform(it) }
    }

    private val vault get() = _uiState.value.vault
    private val vaultId get() = _uiState.value.vaultId
    private val files get() = _uiState.value.files
    private val query get() = _uiState.value.query
    private val sort get() = _uiState.value.sort
    private val sortBusy get() = _uiState.value.sortBusy
    private val sortError get() = _uiState.value.sortError
    private val loading get() = _uiState.value.loading
    private val refreshing get() = _uiState.value.refreshing
    private val partial get() = _uiState.value.partial
    private val error get() = _uiState.value.error
    private val scanStatus get() = _uiState.value.scanStatus
    private val showMissing get() = _uiState.value.showMissing
    private val sources get() = _uiState.value.sources
    private val summaries get() = _uiState.value.summaries
    private val summariesLoading get() = _uiState.value.summariesLoading
    private val summariesReadFailed get() = _uiState.value.summariesReadFailed
    private val run get() = _uiState.value.run
    private val analysisBusy get() = _uiState.value.analysisBusy
    private val dismissedRunItems get() = _uiState.value.dismissedRunItems
    private val statusFilter get() = _uiState.value.statusFilter
    private val statuses get() = _uiState.value.statuses
    private val pendingCount get() = _uiState.value.pendingCount
    private val visibleFiles get() = _uiState.value.visibleFiles
    fun toggleMissing() { updateState { current -> current.copy(showMissing = !showMissing) }; savedState["showMissing"] = showMissing }
    private var currentConfig: VaultConfigState = VaultConfigState.Loading
    private var configJob: Job? = null
    private var indexJob: Job? = null
    private var summaryJob: Job? = null
    private var recordsJob: Job? = null

    init {
        viewModelScope.launch {
            try { val order = settings.readSort(); updateState { it.copy(sort = order) } }
            catch (e: Exception) { if (e is CancellationException) throw e; updateState { current -> current.copy(sortError = R.string.log_sort_failed) } }
            finally { updateState { current -> current.copy(sortBusy = false) } }
        }
    }

    fun retryRecords() {
        recordsJob?.cancel()
        updateState { it.copy(recordsLoading = true, recordsReadFailed = false) }
        recordsJob = viewModelScope.launch {
            try {
                records.observeRecords().collect { rows ->
                    updateState { it.copy(nativeRecords = rows.filter { row -> row.diary.origin == RecordOrigin.NATIVE }, recordsLoading = false) }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                updateState { it.copy(recordsLoading = false, recordsReadFailed = true) }
            }
        }
    }

    private fun observeConfig(refreshAfterRead: Boolean = false) {
        configJob?.cancel()
        configJob = viewModelScope.launch {
            config.distinctUntilChanged().collect { value ->
                currentConfig = value
                val nextVault = (value as? VaultConfigState.Configured)?.uri?.toString()
                val nextId = (value as? VaultConfigState.Configured)?.vaultId
                val changedVault = vaultId != nextId
                if (changedVault) {
                    indexJob?.cancel(); indexJob = null
                    summaryJob?.cancel(); summaryJob = null
                }
                updateState { current ->
                    val next = if (changedVault) current.copy(
                        summaries = emptyMap(), summariesLoading = analysis != null && nextId != null,
                        summariesReadFailed = false,
                        run = controller?.state?.value?.takeIf { it.vaultId == nextId } ?: DiaryAnalysisRun(),
                        files = emptyList(), sources = emptyList(), partial = false, scanStatus = null, refreshing = false,
                    ) else current
                    next.copy(
                        vault = nextVault, vaultId = nextId,
                        error = if (value is VaultConfigState.Failed) R.string.vault_error_load_config_failed else null,
                        loading = value is VaultConfigState.Loading || nextVault != null,
                    )
                }
                nextId?.let {
                    observeIndex(it)
                    observeSummaries(it)
                    if (refreshAfterRead) index.refresh(it)
                }
            }
        }
    }

    fun search(value: String) { updateState { current -> current.copy(query = value) }; savedState["query"] = value }
    fun filter(value: LogStatusFilter) { updateState { current -> current.copy(statusFilter = value) }; savedState["statusFilter"] = value.name }

    private fun observeSummaries(id: String) {
        val reader = analysis ?: return
        if (summaryJob?.isActive == true) return
        updateState { current -> current.copy(summariesLoading = true, summariesReadFailed = false) }
        summaryJob = viewModelScope.launch {
            try { reader.observeSummaries(id).collect { value ->
                if (vaultId == id) { updateState { current -> current.copy(summaries = value.associateBy { it.sourceKey.relPath }, summariesLoading = false) } }
            } } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (vaultId == id) { updateState { current -> current.copy(summariesReadFailed = true, summariesLoading = false) } }
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
                    val status = snapshot.scan?.status
                    updateState { it.copy(
                        sources = snapshot.sources,
                        files = snapshot.sources.map { source -> source.file() },
                        scanStatus = status,
                        partial = status == IndexedScan.PARTIAL,
                        refreshing = snapshot.refreshing,
                        loading = snapshot.sources.isEmpty() && snapshot.refreshing && snapshot.scan == null,
                        error = if (snapshot.refreshFailed || status == IndexedScan.FAILED) R.string.log_failed else null,
                    ) }

                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (vaultId == source) { updateState { current -> current.copy(error = R.string.index_read_failed, loading = false, refreshing = false) } }
            }
        }
    }

    fun changeSort(order: LogSortOrder) {
        if (sortBusy) return
        updateState { current -> current.copy(sortBusy = true, sortError = null) }
        viewModelScope.launch {
            try { settings.saveSort(order); updateState { current -> current.copy(sort = order) } }
            catch (e: Exception) { if (e is CancellationException) throw e; updateState { current -> current.copy(sortError = R.string.log_sort_failed) } }
            finally { updateState { current -> current.copy(sortBusy = false) } }
        }
    }

    fun refresh() {
        updateState { current -> current.copy(dismissedRunItems = run.items.toSet()) }
        vaultId?.let(::observeIndex)
        vaultId?.let(::observeSummaries)
        if (currentConfig is VaultConfigState.Failed) observeConfig(refreshAfterRead = true)
        else vaultId?.let(index::refresh)
    }
}
