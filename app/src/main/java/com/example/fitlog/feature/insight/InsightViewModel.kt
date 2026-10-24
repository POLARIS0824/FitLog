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
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

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
    private data class Controls(
        val message: Int? = null,
        val aliasSaving: Boolean = false, val aliasSaved: Boolean = false, val aliasError: Int? = null,
        val focusSaving: Boolean = false, val focusSaved: Boolean = false, val focusError: Int? = null,
        val focusVaultId: String? = null, val rangeSaving: Boolean = false,
    )
    private data class IndexRead(val snapshot: SourceIndexSnapshot? = null, val failed: Boolean = false)
    private data class Input(
        val config: VaultConfigState, val index: IndexRead = IndexRead(),
        val records: List<TrainingRecordDetails> = emptyList(), val aliases: ExerciseAliases = ExerciseAliases(),
        val focus: Set<String>? = null, val range: String? = null, val failed: Boolean = false,
    )
    private val controls = MutableStateFlow(Controls())
    private val reload = MutableStateFlow(0)
    private val range = savedState.getStateFlow("insightRange", initialRange)
    private val inputs = reload.flatMapLatest {
        configFlow.filter { it !is VaultConfigState.Loading }.distinctUntilChanged().flatMapLatest { config ->
            val id = (config as? VaultConfigState.Configured)?.vaultId
            val snapshots = id?.let {
                flow { emitAll(observeIndex(it)) }.map { snapshot -> IndexRead(snapshot) }
                    .catch { e -> if (e is CancellationException) throw e; emit(IndexRead(failed = true)) }
            } ?: flowOf(IndexRead())
            combine(snapshots, reader.observeRecords(), aliasStore.observe(),
                preferences?.observe(id) ?: flowOf(null), preferences?.observeRange() ?: flowOf(null),
            ) { index, records, aliases, focus, selected -> Input(config, index, records, aliases, focus, selected) }
        }.catch { e ->
            if (e is CancellationException) throw e
            emit(Input(VaultConfigState.Failed(e), failed = true))
        }
    }

    val uiState: StateFlow<InsightUiState> = combine(inputs, range, controls) { input, savedRange, control ->
        val selectedRange = InsightRange.entries.firstOrNull { it.name == (input.range ?: savedRange) } ?: InsightRange.NINETY
        val id = (input.config as? VaultConfigState.Configured)?.vaultId
        val data = if (input.failed) null else buildInsightData(id, input.index.snapshot, input.records, input.aliases, localDate())
        InsightUiState(
            config = input.config, index = input.index.snapshot, data = data, loading = false,
            error = if (input.failed) R.string.insight_read_failed else null,
            presentation = data?.let { prepareInsight(it, selectedRange, input.focus, nameKey) },
            focusedKeys = input.focus, range = selectedRange, rangeSaving = control.rangeSaving,
            message = control.message ?: if (input.index.failed) R.string.insight_read_failed else null,
            aliasSaving = control.aliasSaving, aliasSaved = control.aliasSaved, aliasError = control.aliasError,
            focusSaving = control.focusSaving,
            focusSaved = control.focusSaved && control.focusVaultId == id,
            focusError = control.focusError.takeIf { control.focusVaultId == id },
        )
    }.flowOn(computationDispatcher).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000),
        InsightUiState(range = InsightRange.entries.firstOrNull { it.name == range.value } ?: InsightRange.NINETY))

    fun selectRange(value: InsightRange) {
        val state = uiState.value
        if (controls.value.rangeSaving || state.loading || state.error != null) return
        controls.update { it.copy(rangeSaving = true, message = null) }
        viewModelScope.launch {
            try { preferences?.saveRange(value.name); savedState["insightRange"] = value.name }
            catch (e: Exception) {
                if (e is CancellationException) throw e
                controls.update { it.copy(message = R.string.insight_range_save_failed) }
            } finally { controls.update { it.copy(rangeSaving = false) } }
        }
    }
    fun refresh() {
        val state = uiState.value
        if (state.index?.refreshing == true || controls.value.aliasSaving) return
        controls.update { it.copy(message = null) }
        reload.update { it + 1 }
        (state.config as? VaultConfigState.Configured)?.vaultId?.let(refreshIndex)
    }
    fun resumed() {
        if (uiState.value.data?.today?.let { it != localDate() } == true) reload.update { it + 1 }
    }
    fun recordDestination(id: String): InsightRecordDestination? {
        val state = uiState.value
        val record = state.data?.diaries?.firstOrNull { it.record.diary.id == id }?.record?.diary ?: return null
        if (record.origin == RecordOrigin.NATIVE) return InsightRecordDestination.Native(id)
        val current = state.config as? VaultConfigState.Configured
        val source = state.index?.sources?.filter {
            it.vaultId == record.vaultId && it.path == record.relPath && it.status == IndexedSource.AVAILABLE
        }?.singleOrNull()
        if (current == null || current.vaultId != record.vaultId || source == null) {
            controls.update { it.copy(message = R.string.insight_source_unavailable) }
            return null
        }
        val uri = current.uri.toString()
        return InsightRecordDestination.Markdown(uri, current.vaultId, source.uri, source.path, source.directory ?: uri, source.name)
    }
    fun saveAliases(previousPrimaryKey: String?, replacement: ExerciseAliasGroup?) {
        if (controls.value.aliasSaving) return
        controls.update { it.copy(aliasSaving = true, aliasError = null, aliasSaved = false) }
        viewModelScope.launch {
            try { aliasStore.save(previousPrimaryKey, replacement); controls.update { it.copy(aliasSaved = true) } }
            catch (e: Exception) {
                if (e is CancellationException) throw e
                controls.update { it.copy(aliasError = R.string.insight_alias_save_failed) }
            } finally { controls.update { it.copy(aliasSaving = false) } }
        }
    }
    fun aliasSaveHandled() { controls.update { it.copy(aliasSaved = false) } }
    fun focusSaveHandled() { controls.update { it.copy(focusSaved = false) } }
    fun clearAliasError() {
        if (!controls.value.aliasSaving) controls.update { it.copy(aliasError = null, aliasSaved = false) }
    }
    fun saveFocus(keys: Set<String>) {
        val store = preferences ?: return
        val state = uiState.value
        if (controls.value.focusSaving || state.loading || state.error != null) return
        val id = state.data?.vaultId
        controls.update { it.copy(focusSaving = true, focusError = null, focusSaved = false, focusVaultId = id) }
        viewModelScope.launch {
            try { store.save(id, keys); controls.update { it.copy(focusSaved = true) } }
            catch (e: Exception) {
                if (e is CancellationException) throw e
                controls.update { it.copy(focusError = R.string.insight_focus_save_failed) }
            } finally { controls.update { it.copy(focusSaving = false) } }
        }
    }
    fun clearFocusError() {
        if (!controls.value.focusSaving) controls.update { it.copy(focusError = null, focusSaved = false) }
    }
}
