@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.example.fitlog.feature.insight

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fitlog.R
import com.example.fitlog.data.record.TrainingRecords
import com.example.fitlog.data.settings.ExerciseAliasStore
import com.example.fitlog.data.settings.InsightPreferences
import com.example.fitlog.data.vault.VaultConfigState
import com.example.fitlog.domain.insight.InsightRange
import com.example.fitlog.domain.insight.buildInsightData
import com.example.fitlog.model.record.ExerciseAliasGroup
import com.example.fitlog.model.record.ExerciseAliases
import com.example.fitlog.model.record.RecordOrigin
import com.example.fitlog.model.record.TrainingRecordDetails
import com.example.fitlog.model.source.IndexedSource
import com.example.fitlog.model.source.SourceIndexSnapshot
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    /** Query work exists only while the screen collects; no hidden background collection. */
    val uiState: StateFlow<InsightUiState> = flow {
        coroutineScope {
            launch { observe() }
            emitAll(_uiState.mapLatest { value ->
                withContext(computationDispatcher) {
                    value.copy(presentation = value.data?.let {
                        prepareInsight(it, value.range, value.focusedKeys, nameKey)
                    })
                }
            })
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), _uiState.value)

    private val restart = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
    private var input: Input? = null

    private data class Input(
        val vaultId: String?,
        val index: SourceIndexSnapshot?,
        val records: List<TrainingRecordDetails>,
        val aliases: ExerciseAliases,
    )

    private suspend fun observe() {
        restart.onStart { emit(false) }.collectLatest { refreshAfterRead ->
            var shouldRefresh = refreshAfterRead
            try {
                configFlow.distinctUntilChanged().collectLatest { value ->
                    updateState { current -> current.copy(config = value, loading = true, error = null) }
                    val vault = value as? VaultConfigState.Configured
                    updateState { current -> current.copy(focusError = null, focusedKeys = null) }
                    if (shouldRefresh && vault != null) {
                        shouldRefresh = false
                        refreshIndex(vault.vaultId)
                    }
                    val snapshots = vault?.let {
                        observeIndex(it.vaultId)
                            .map<SourceIndexSnapshot, SourceIndexSnapshot?> { snapshot -> snapshot }
                            .catch { e ->
                                if (e is CancellationException) throw e
                                updateState { current -> current.copy(message = R.string.insight_read_failed) }
                                emit(null)
                            }
                    } ?: flowOf(null)
                    combine(
                        snapshots,
                        reader.observeRecords(),
                        aliasStore.observe(),
                        preferences?.observe(vault?.vaultId) ?: flowOf(null),
                        preferences?.observeRange() ?: flowOf(null),
                    ) { snapshot, records, aliases, focused, selectedRange ->
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
                        input = next
                        updateState { current -> current.copy(index = next.index, data = prepared, loading = false) }
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                input = null
                updateState { current ->
                    current.copy(data = null, loading = false, error = R.string.insight_read_failed)
                }
            }
        }
    }

    private inline fun updateState(transform: (InsightUiState) -> InsightUiState) {
        _uiState.update(transform)
    }

    fun selectRange(value: InsightRange) {
        val state = _uiState.value
        if (state.rangeSaving || state.loading || state.error != null) return
        updateState { it.copy(rangeSaving = true, message = null) }
        viewModelScope.launch {
            try {
                preferences?.saveRange(value.name)
                updateState { it.copy(range = value) }
                savedState["insightRange"] = value.name
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                updateState { it.copy(message = R.string.insight_range_save_failed) }
            } finally {
                updateState { it.copy(rangeSaving = false) }
            }
        }
    }

    fun refresh() {
        val state = _uiState.value
        if (state.index?.refreshing == true || state.aliasSaving) return
        updateState { it.copy(message = null) }
        restart.tryEmit(true)
    }

    fun resumed() {
        val data = _uiState.value.data
        if (data != null && data.today != localDate()) restart.tryEmit(false)
    }

    fun recordDestination(id: String): InsightRecordDestination? {
        val record = input?.records?.firstOrNull { it.diary.id == id }?.diary ?: return null
        if (record.origin == RecordOrigin.NATIVE) return InsightRecordDestination.Native(id)
        val state = _uiState.value
        val current = state.config as? VaultConfigState.Configured
        val source = state.index?.sources
            ?.filter { it.vaultId == record.vaultId && it.path == record.relPath && it.status == IndexedSource.AVAILABLE }
            ?.singleOrNull()
        if (current == null || current.vaultId != record.vaultId || source == null) {
            updateState { it.copy(message = R.string.insight_source_unavailable) }
            return null
        }
        val uri = current.uri.toString()
        return InsightRecordDestination.Markdown(
            uri, current.vaultId, source.uri, source.path, source.directory ?: uri, source.name,
        )
    }

    fun saveAliases(previousPrimaryKey: String?, replacement: ExerciseAliasGroup?) {
        if (_uiState.value.aliasSaving) return
        updateState { it.copy(aliasSaving = true, aliasError = null, aliasSaved = false) }
        viewModelScope.launch {
            try {
                aliasStore.save(previousPrimaryKey, replacement)
                updateState { it.copy(aliasSaved = true) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                updateState { it.copy(aliasError = R.string.insight_alias_save_failed) }
            } finally {
                updateState { it.copy(aliasSaving = false) }
            }
        }
    }

    fun aliasSaveHandled() {
        updateState { it.copy(aliasSaved = false) }
    }

    fun focusSaveHandled() {
        updateState { it.copy(focusSaved = false) }
    }

    fun clearAliasError() {
        if (!_uiState.value.aliasSaving) updateState { it.copy(aliasError = null, aliasSaved = false) }
    }

    fun saveFocus(keys: Set<String>) {
        val store = preferences ?: return
        val state = _uiState.value
        val scope = state.data?.vaultId
        if (state.focusSaving || state.loading || state.error != null) return
        updateState { it.copy(focusSaving = true, focusError = null, focusSaved = false) }
        viewModelScope.launch {
            try {
                store.save(scope, keys)
                if (_uiState.value.data?.vaultId == scope) updateState { it.copy(focusSaved = true) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (_uiState.value.data?.vaultId == scope) {
                    updateState { it.copy(focusError = R.string.insight_focus_save_failed) }
                }
            } finally {
                updateState { it.copy(focusSaving = false) }
            }
        }
    }

    fun clearFocusError() {
        if (!_uiState.value.focusSaving) updateState { it.copy(focusError = null, focusSaved = false) }
    }
}
