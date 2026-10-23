@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.example.fitlog.insight

import com.example.fitlog.domain.insight.*
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import androidx.lifecycle.viewModelScope
import com.example.fitlog.R
import com.example.fitlog.data.analysis.*
import com.example.fitlog.data.index.IndexedSource
import com.example.fitlog.data.index.SourceIndexSnapshot
import com.example.fitlog.data.insight.*
import com.example.fitlog.data.vault.VaultConfigState
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

internal class InsightViewModel(
    private val configFlow: Flow<VaultConfigState>,
    private val observeIndex: (String) -> Flow<SourceIndexSnapshot>,
    private val reader: TrainingRecords,
    private val aliasStore: ExerciseAliasStore,
    private val savedState: SavedStateHandle,
    private val refreshIndex: (String) -> Unit,
    initialRange: String = InsightRange.NINETY.name,
    private val localDate: () -> LocalDate = LocalDate::now,
    private val preferences: InsightPreferences? = null,
    private val nameKey: String? = null,
    private val computationDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        InsightUiState(
            range = InsightRange.entries.firstOrNull {
                it.name == (savedState.get<String>("insightRange") ?: initialRange)
            } ?: InsightRange.NINETY,
        )
    )
    val uiState: StateFlow<InsightUiState> = flow {
        observe()
        try { emitAll(_uiState.mapLatest { value -> withContext(computationDispatcher) {
            value.copy(presentation = value.data?.let { prepareInsight(it, value.range, value.focusedKeys, nameKey) })
        } }) } finally { observer?.cancel() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), _uiState.value)

    private inline fun updateState(transform: (InsightUiState) -> InsightUiState) {
        _uiState.update { transform(it) }
    }

    private val config get() = _uiState.value.config
    private val data get() = _uiState.value.data
    private val index get() = _uiState.value.index
    private val loading get() = _uiState.value.loading
    private val error get() = _uiState.value.error
    private val message get() = _uiState.value.message
    private val aliasSaving get() = _uiState.value.aliasSaving
    private val aliasError get() = _uiState.value.aliasError
    private val focusedKeys get() = _uiState.value.focusedKeys
    private val focusSaving get() = _uiState.value.focusSaving
    private val focusError get() = _uiState.value.focusError
    private val rangeSaving get() = _uiState.value.rangeSaving
    private val range get() = _uiState.value.range
    private var observer: Job? = null
    private var input: Input? = null
    private data class Input(val vaultId: String?, val index: SourceIndexSnapshot?,
        val records: List<TrainingRecordDetails>, val aliases: ExerciseAliases)

    private fun observe(refreshAfterRead: Boolean = false) {
        observer?.cancel()
        observer = viewModelScope.launch {
            var shouldRefresh = refreshAfterRead
            try {
                configFlow.distinctUntilChanged().collectLatest { value ->
                    updateState { current -> current.copy(config = value, loading = true, error = null) }
                    val vault = value as? VaultConfigState.Configured
                    updateState { current -> current.copy(focusError = null, focusedKeys = null) }
                    if (shouldRefresh && vault != null) { shouldRefresh = false; refreshIndex(vault.vaultId) }
                    val snapshots = vault?.let {
                        observeIndex(it.vaultId).map<SourceIndexSnapshot, SourceIndexSnapshot?> { snapshot -> snapshot }
                            .catch { e -> if (e is CancellationException) throw e
                                updateState { current -> current.copy(message = R.string.insight_read_failed) }; emit(null) }
                    } ?: flowOf(null)
                    combine(snapshots, reader.observeRecords(), aliasStore.observe(),
                        preferences?.observe(vault?.vaultId) ?: flowOf(null),
                        preferences?.observeRange() ?: flowOf(null)) { snapshot, records, aliases, focused, selectedRange ->
                        Triple(Input(vault?.vaultId, snapshot, records, aliases), focused, selectedRange)
                    }.collectLatest { (next, focused, selectedRange) ->
                        InsightRange.entries.firstOrNull { it.name == selectedRange }?.let {
                            updateState { current -> current.copy(range = it) }
                            savedState["insightRange"] = it.name
                        }
                        updateState { current -> current.copy(focusedKeys = focused) }
                        val prepared = withContext(computationDispatcher) {
                            buildInsightData(next.vaultId, next.index, next.records, next.aliases, localDate())
                        }
                        input = next; updateState { current -> current.copy(index = next.index) }
                        updateState { current -> current.copy(data = prepared, loading = false) }
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                updateState { current -> current.copy(data = null) }; input = null; updateState { current -> current.copy(loading = false, error = R.string.insight_read_failed) }
            }
        }
    }
    fun selectRange(value: InsightRange) {
        if (rangeSaving || loading || error != null) return
        updateState { current -> current.copy(rangeSaving = true, message = null) }
        viewModelScope.launch {
            try {
                preferences?.saveRange(value.name)
                updateState { current -> current.copy(range = value) }
                savedState["insightRange"] = value.name
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                updateState { current -> current.copy(message = R.string.insight_range_save_failed) }
            } finally { updateState { current -> current.copy(rangeSaving = false) } }
        }
    }
    fun refresh() { if (index?.refreshing != true && !aliasSaving) { updateState { current -> current.copy(message = null) }; observe(true) } }
    fun resumed() {
        if (data != null && data?.today != localDate()) observe()
    }
    fun recordDestination(id: String): InsightRecordDestination? {
        val record = input?.records?.firstOrNull { it.diary.id == id }?.diary ?: return null
        if (record.origin == RecordOrigin.NATIVE) return InsightRecordDestination.Native(id)
        val current = config as? VaultConfigState.Configured
        val source = index?.sources?.filter { it.vaultId == record.vaultId && it.path == record.relPath && it.status == IndexedSource.AVAILABLE }?.singleOrNull()
        if (current == null || current.vaultId != record.vaultId || source == null) {
            updateState { current -> current.copy(message = R.string.insight_source_unavailable) }; return null
        }
        val uri = current.uri.toString()
        return InsightRecordDestination.Markdown(uri, current.vaultId, source.uri, source.path,
            source.directory ?: uri, source.name)
    }
    fun saveAliases(previousPrimaryKey: String?, replacement: ExerciseAliasGroup?) {
        if (aliasSaving) return
        updateState { current -> current.copy(aliasSaving = true, aliasError = null, aliasSaved = false) }
        viewModelScope.launch {
            try { aliasStore.save(previousPrimaryKey, replacement); updateState { it.copy(aliasSaved = true) } }
            catch (e: Exception) { if (e is CancellationException) throw e; updateState { current -> current.copy(aliasError = R.string.insight_alias_save_failed) } }
            finally { updateState { current -> current.copy(aliasSaving = false) } }
        }
    }
    fun aliasSaveHandled() { updateState { it.copy(aliasSaved = false) } }
    fun focusSaveHandled() { updateState { it.copy(focusSaved = false) } }
    fun clearAliasError() { if (!aliasSaving) updateState { current -> current.copy(aliasError = null, aliasSaved = false) } }
    fun saveFocus(keys: Set<String>) {
        val store = preferences ?: return
        val scope = data?.vaultId
        if (focusSaving || loading || error != null) return
        updateState { current -> current.copy(focusSaving = true, focusError = null, focusSaved = false) }
        viewModelScope.launch {
            try {
                store.save(scope, keys)
                if (data?.vaultId == scope) updateState { it.copy(focusSaved = true) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (data?.vaultId == scope) updateState { current -> current.copy(focusError = R.string.insight_focus_save_failed) }
            } finally { updateState { current -> current.copy(focusSaving = false) } }
        }
    }
    fun clearFocusError() { if (!focusSaving) updateState { current -> current.copy(focusError = null, focusSaved = false) } }
}
