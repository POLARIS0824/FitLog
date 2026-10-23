@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.example.fitlog.feature.log

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fitlog.R
import com.example.fitlog.app.analysis.DiaryAnalysisController
import com.example.fitlog.app.analysis.DiaryAnalysisRun
import com.example.fitlog.data.analysis.AnalysisSource
import com.example.fitlog.data.analysis.DiaryVaultAnalysisReader
import com.example.fitlog.data.index.SourceIndex
import com.example.fitlog.data.index.file
import com.example.fitlog.data.record.TrainingRecords
import com.example.fitlog.data.settings.LogSettingsStore
import com.example.fitlog.data.settings.LogSortOrder
import com.example.fitlog.data.vault.VaultConfigState
import com.example.fitlog.model.record.RecordOrigin
import com.example.fitlog.model.source.IndexedScan
import com.example.fitlog.model.source.SourceKey
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class LogViewModel(
    private val savedState: SavedStateHandle,
    private val config: Flow<VaultConfigState>,
    private val index: SourceIndex,
    private val settings: LogSettingsStore,
    private val analysis: DiaryVaultAnalysisReader? = null,
    private val controller: DiaryAnalysisController? = null,
    private val records: TrainingRecords,
    private val computationDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val localDate: () -> LocalDate = LocalDate::now,
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

    /**
     * Display state exists only while the screen is collecting it. Collectors are children of the
     * sharing scope, so leaving the page cancels them instead of leaking into viewModelScope.
     */
    val uiState: StateFlow<LogUiState> = flow {
        coroutineScope {
            launch { collectRecords() }
            launch { collectConfig() }
            controller?.let { runner -> launch { runner.state.collect(::applyRun) } }
            emitAll(_uiState.mapLatest { value ->
                withContext(computationDispatcher) { value.withDerivedValues() }
            })
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), _uiState.value)

    private val recordsRetry = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val configRetry = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
    private val displayRetry = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private var currentConfig: VaultConfigState = VaultConfigState.Loading

    init {
        viewModelScope.launch {
            try {
                val order = settings.readSort()
                updateState { it.copy(sort = order) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                updateState { it.copy(sortError = R.string.log_sort_failed) }
            } finally {
                updateState { it.copy(sortBusy = false) }
            }
        }
    }

    private inline fun updateState(transform: (LogUiState) -> LogUiState) {
        _uiState.update(transform)
    }

    private suspend fun collectRecords() {
        recordsRetry.onStart { emit(Unit) }.collectLatest {
            updateState { it.copy(recordsLoading = true, recordsReadFailed = false) }
            try {
                records.observeRecords().collect { rows ->
                    updateState {
                        it.copy(
                            nativeRecords = rows.filter { row -> row.diary.origin == RecordOrigin.NATIVE },
                            recordsLoading = false,
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                updateState { it.copy(recordsLoading = false, recordsReadFailed = true) }
            }
        }
    }

    private suspend fun collectConfig() {
        configRetry.onStart { emit(false) }.collectLatest { refreshAfterRead ->
            // collectLatest, not collect: a new config value must cancel the old vault's collectors.
            config.distinctUntilChanged().collectLatest { value ->
                applyConfig(value)
                val id = (value as? VaultConfigState.Configured)?.vaultId ?: return@collectLatest
                coroutineScope {
                    launch { displayRetry.onStart { emit(Unit) }.collectLatest { collectIndex(id) } }
                    launch { displayRetry.onStart { emit(Unit) }.collectLatest { collectSummaries(id) } }
                    if (refreshAfterRead) index.refresh(id)
                }
            }
        }
    }

    private fun applyConfig(value: VaultConfigState) {
        currentConfig = value
        val nextVault = (value as? VaultConfigState.Configured)?.uri?.toString()
        val nextId = (value as? VaultConfigState.Configured)?.vaultId
        val changedVault = _uiState.value.vaultId != nextId
        updateState { current ->
            val next = if (changedVault) current.copy(
                summaries = emptyMap(),
                summariesLoading = analysis != null && nextId != null,
                summariesReadFailed = false,
                run = controller?.state?.value?.takeIf { it.vaultId == nextId } ?: DiaryAnalysisRun(),
                files = emptyList(),
                sources = emptyList(),
                partial = false,
                scanStatus = null,
                refreshing = false,
            ) else current
            next.copy(
                vault = nextVault,
                vaultId = nextId,
                error = if (value is VaultConfigState.Failed) R.string.vault_error_load_config_failed else null,
                // Only a real vault switch (or the initial read) starts a full-page load.
                loading = value is VaultConfigState.Loading || (nextVault != null && changedVault),
            )
        }
    }

    private suspend fun collectSummaries(id: String) {
        val reader = analysis ?: return
        updateState { it.copy(summariesLoading = true, summariesReadFailed = false) }
        try {
            reader.observeSummaries(id).collect { value ->
                if (_uiState.value.vaultId == id) {
                    updateState { it.copy(summaries = value.associateBy { it.sourceKey.relPath }, summariesLoading = false) }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (_uiState.value.vaultId == id) {
                updateState { it.copy(summariesReadFailed = true, summariesLoading = false) }
            }
        }
    }

    private suspend fun collectIndex(id: String) {
        try {
            index.observe(id).collect { snapshot ->
                if (_uiState.value.vaultId != id) return@collect
                val status = snapshot.scan?.status
                updateState {
                    it.copy(
                        sources = snapshot.sources,
                        files = snapshot.sources.map { source -> source.file() },
                        scanStatus = status,
                        partial = status == IndexedScan.PARTIAL,
                        refreshing = snapshot.refreshing,
                        loading = snapshot.sources.isEmpty() && snapshot.refreshing && snapshot.scan == null,
                        error = if (snapshot.refreshFailed || status == IndexedScan.FAILED) R.string.log_failed else null,
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (_uiState.value.vaultId == id) {
                updateState { it.copy(error = R.string.index_read_failed, loading = false, refreshing = false) }
            }
        }
    }

    private fun applyRun(value: DiaryAnalysisRun) {
        updateState { current ->
            current.copy(
                dismissedRunItems = if (value.active && !current.run.active) emptySet() else current.dismissedRunItems,
                run = value.takeIf { it.vaultId == current.vaultId } ?: DiaryAnalysisRun(),
            )
        }
    }

    fun retryRecords() {
        recordsRetry.tryEmit(Unit)
    }

    fun search(value: String) {
        updateState { it.copy(query = value) }
        savedState["query"] = value
    }

    fun filter(value: LogStatusFilter) {
        updateState { it.copy(statusFilter = value) }
        savedState["statusFilter"] = value.name
    }

    fun toggleMissing() {
        updateState { it.copy(showMissing = !it.showMissing) }
        savedState["showMissing"] = _uiState.value.showMissing
    }

    fun startAnalysis(range: LogAnalysisRange, reanalyze: Boolean) {
        val state = _uiState.value
        if (state.analysisBusy || state.refreshing || state.summariesLoading || state.summariesReadFailed) return
        val matched = matchAnalysisSources(state.sources, state.summaries, range, localDate())
        controller?.startBatch(matched.map { AnalysisSource(SourceKey(it.vaultId, it.path), it.uri) }, reanalyze)
    }

    fun cancelAnalysis() {
        controller?.cancel()
    }

    fun changeSort(order: LogSortOrder) {
        val state = _uiState.value
        if (state.sortBusy) return
        updateState { it.copy(sortBusy = true, sortError = null) }
        viewModelScope.launch {
            try {
                settings.saveSort(order)
                updateState { it.copy(sort = order) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                updateState { it.copy(sortError = R.string.log_sort_failed) }
            } finally {
                updateState { it.copy(sortBusy = false) }
            }
        }
    }

    fun refresh() {
        updateState { it.copy(dismissedRunItems = it.run.items.toSet()) }
        val id = _uiState.value.vaultId
        if (currentConfig is VaultConfigState.Failed) {
            configRetry.tryEmit(true)
        } else {
            // Restart only the display collectors; re-reading config would flash a Loading reset.
            displayRetry.tryEmit(Unit)
            id?.let(index::refresh)
        }
    }
}
