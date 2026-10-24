@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.example.fitlog.feature.log

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fitlog.R
import com.example.fitlog.data.analysis.DiaryVaultAnalysisReader
import com.example.fitlog.data.index.SourceIndex
import com.example.fitlog.data.index.file
import com.example.fitlog.data.record.TrainingRecords
import com.example.fitlog.data.settings.LogSettingsStore
import com.example.fitlog.data.settings.LogSortOrder
import com.example.fitlog.data.vault.VaultConfigState
import com.example.fitlog.domain.analysis.DiaryAnalysisRunner
import com.example.fitlog.model.analysis.AnalysisSource
import com.example.fitlog.model.analysis.DiaryAnalysisRun
import com.example.fitlog.model.analysis.DiaryAnalysisSummary
import com.example.fitlog.model.record.RecordOrigin
import com.example.fitlog.model.record.TrainingRecordDetails
import com.example.fitlog.model.source.IndexedScan
import com.example.fitlog.model.source.SourceIndexSnapshot
import com.example.fitlog.model.source.SourceKey
import java.time.LocalDate
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

internal class LogViewModel(
    private val savedState: SavedStateHandle,
    private val config: Flow<VaultConfigState>,
    private val index: SourceIndex,
    private val settings: LogSettingsStore,
    private val analysis: DiaryVaultAnalysisReader? = null,
    private val controller: DiaryAnalysisRunner? = null,
    private val records: TrainingRecords,
    private val computationDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val localDate: () -> LocalDate = LocalDate::now,
) : ViewModel() {
    private data class Controls(
        val sort: LogSortOrder = LogSortOrder.Descending,
        val sortBusy: Boolean = true,
        val sortError: Int? = null,
        val dismissedRun: DiaryAnalysisRun? = null,
    )
    private data class Filters(val query: String, val missing: Boolean, val status: LogStatusFilter)
    private data class RecordRead(val rows: List<TrainingRecordDetails> = emptyList(), val failed: Boolean = false)
    private data class IndexRead(val snapshot: SourceIndexSnapshot? = null, val failed: Boolean = false)
    private data class SummaryRead(val values: List<DiaryAnalysisSummary> = emptyList(), val failed: Boolean = false)
    private data class VaultRead(val config: VaultConfigState, val index: IndexRead = IndexRead(), val summaries: SummaryRead = SummaryRead())

    private val controls = MutableStateFlow(Controls())
    private val recordsRetry = MutableStateFlow(0)
    private val configRetry = MutableStateFlow(0)
    private val displayRetry = MutableStateFlow(0)
    // The editor owns text, selection and IME composition synchronously. Result
    // computation observes it, but must never drive the editor's value back.
    val searchFieldState = TextFieldState(savedState.get<String>("query").orEmpty())
    private val query = snapshotFlow { searchFieldState.text.toString() }
        .onEach { savedState["query"] = it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, searchFieldState.text.toString())
    private val showMissing = savedState.getStateFlow("showMissing", false)
    private val statusFilter = savedState.getStateFlow("statusFilter", LogStatusFilter.ALL.name)
    private val filters = combine(query, showMissing, statusFilter) { text, missing, status ->
        Filters(text, missing, LogStatusFilter.entries.firstOrNull { it.name == status } ?: LogStatusFilter.ALL)
    }
    private val recordRead = recordsRetry.flatMapLatest {
        flow { emitAll(records.observeRecords()) }.map { rows -> RecordRead(rows.filter { it.diary.origin == RecordOrigin.NATIVE }) }
            .catch { e -> if (e is CancellationException) throw e; emit(RecordRead(failed = true)) }
    }
    private val vaultRead = configRetry.flatMapLatest {
        config.filter { it !is VaultConfigState.Loading }.distinctUntilChanged().flatMapLatest { value ->
            val id = (value as? VaultConfigState.Configured)?.vaultId
            if (id == null) flowOf(VaultRead(value)) else {
                val snapshots = displayRetry.flatMapLatest {
                    flow { emitAll(index.observe(id)) }.map { IndexRead(it) }
                        .catch { e -> if (e is CancellationException) throw e; emit(IndexRead(failed = true)) }
                }
                val summaries = displayRetry.flatMapLatest {
                    if (analysis == null) flowOf(SummaryRead()) else
                        flow { emitAll(analysis.observeSummaries(id)) }.map { SummaryRead(it) }
                            .catch { e -> if (e is CancellationException) throw e; emit(SummaryRead(failed = true)) }
                }
                combine(snapshots, summaries) { snapshot, summary -> VaultRead(value, snapshot, summary) }
            }
        }
    }.catch { e -> if (e is CancellationException) throw e; emit(VaultRead(VaultConfigState.Failed(e))) }

    val uiState: StateFlow<LogUiState> = combine(
        filters, controls, recordRead, vaultRead, controller?.state ?: flowOf(DiaryAnalysisRun()),
    ) { filter, control, records, vault, activeRun ->
        val current = vault.config as? VaultConfigState.Configured
        val snapshot = vault.index.snapshot
        val run = activeRun.takeIf { it.vaultId == current?.vaultId } ?: DiaryAnalysisRun()
        LogUiState(
            query = filter.query, showMissing = filter.missing, statusFilter = filter.status,
            sort = control.sort, sortBusy = control.sortBusy, sortError = control.sortError,
            nativeRecords = records.rows, recordsLoading = false, recordsReadFailed = records.failed,
            vault = current?.uri?.toString(), vaultId = current?.vaultId,
            sources = snapshot?.sources.orEmpty(), files = snapshot?.sources.orEmpty().map { it.file() },
            scanStatus = snapshot?.scan?.status, partial = snapshot?.scan?.status == IndexedScan.PARTIAL,
            refreshing = snapshot?.refreshing == true,
            loading = snapshot?.let { it.sources.isEmpty() && it.refreshing && it.scan == null } == true,
            error = when {
                vault.config is VaultConfigState.Failed -> R.string.vault_error_load_config_failed
                vault.index.failed -> R.string.index_read_failed
                snapshot?.refreshFailed == true || snapshot?.scan?.status == IndexedScan.FAILED -> R.string.log_failed
                else -> null
            },
            summaries = vault.summaries.values.associateBy { it.sourceKey.relPath },
            summariesReadFailed = vault.summaries.failed, summariesLoading = false, run = run,
            dismissedRunItems = if (run === control.dismissedRun) run.items.toSet() else emptySet(),
        ).withDerivedValues()
    }.flowOn(computationDispatcher).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000),
        LogUiState(query = query.value, showMissing = showMissing.value,
            statusFilter = LogStatusFilter.entries.firstOrNull { it.name == statusFilter.value } ?: LogStatusFilter.ALL,
            loading = true, sortBusy = true, summariesLoading = analysis != null))

    init {
        viewModelScope.launch {
            try { val order = settings.readSort(); controls.update { it.copy(sort = order) } }
            catch (e: Exception) {
                if (e is CancellationException) throw e
                controls.update { it.copy(sortError = R.string.log_sort_failed) }
            } finally { controls.update { it.copy(sortBusy = false) } }
        }
    }

    fun retryRecords() { recordsRetry.update { it + 1 } }
    fun filter(value: LogStatusFilter) { savedState["statusFilter"] = value.name }
    fun toggleMissing() { savedState["showMissing"] = !showMissing.value }
    fun startAnalysis(range: LogAnalysisRange, reanalyze: Boolean) {
        val state = uiState.value
        if (state.analysisBusy || state.refreshing || state.summariesLoading || state.summariesReadFailed) return
        val matched = matchAnalysisSources(state.sources, state.summaries, range, localDate())
        controller?.startBatch(matched.map { AnalysisSource(SourceKey(it.vaultId, it.path), it.uri) }, reanalyze)
    }
    fun cancelAnalysis() { controller?.cancel() }
    fun changeSort(order: LogSortOrder) {
        if (controls.value.sortBusy) return
        controls.update { it.copy(sortBusy = true, sortError = null) }
        viewModelScope.launch {
            try { settings.saveSort(order); controls.update { it.copy(sort = order) } }
            catch (e: Exception) {
                if (e is CancellationException) throw e
                controls.update { it.copy(sortError = R.string.log_sort_failed) }
            } finally { controls.update { it.copy(sortBusy = false) } }
        }
    }
    fun refresh() {
        val state = uiState.value
        controls.update { it.copy(dismissedRun = state.run) }
        displayRetry.update { it + 1 }
        state.vaultId?.let(index::refresh)
        if (state.error == R.string.vault_error_load_config_failed) {
            configRetry.update { it + 1 }
            viewModelScope.launch {
                val value = config.catch { e -> if (e is CancellationException) throw e; emit(VaultConfigState.Failed(e)) }
                    .first { it !is VaultConfigState.Loading }
                (value as? VaultConfigState.Configured)?.vaultId?.let(index::refresh)
            }
        }
    }
}
