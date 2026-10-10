package com.example.fitlog.insight

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fitlog.R
import com.example.fitlog.data.analysis.ConfirmedDiaryReader
import com.example.fitlog.data.analysis.ConfirmedDiaryRecord
import com.example.fitlog.data.analysis.SourceKey
import com.example.fitlog.data.index.IndexedSource
import com.example.fitlog.data.index.SourceIndexSnapshot
import com.example.fitlog.data.insight.ExerciseAliasGroup
import com.example.fitlog.data.insight.ExerciseAliases
import com.example.fitlog.data.insight.ExerciseAliasStore
import com.example.fitlog.data.vault.VaultConfigState
import com.example.fitlog.navigation.FitLogRoute
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class InsightViewModel(
    private val configFlow: Flow<VaultConfigState>,
    private val observeIndex: (String) -> Flow<SourceIndexSnapshot>,
    private val reader: ConfirmedDiaryReader,
    private val aliasStore: ExerciseAliasStore,
    private val savedState: SavedStateHandle,
    private val refreshIndex: (String) -> Unit,
    private val targetVaultId: String? = null,
    initialRange: String = InsightRange.NINETY.name,
    private val localDate: () -> LocalDate = LocalDate::now,
) : ViewModel() {
    var config by mutableStateOf<VaultConfigState>(VaultConfigState.Loading); private set
    var data by mutableStateOf<InsightData?>(null); private set
    var index by mutableStateOf<SourceIndexSnapshot?>(null); private set
    var loading by mutableStateOf(true); private set
    var error by mutableStateOf<Int?>(null); private set
    var message by mutableStateOf<Int?>(null); private set
    var aliasSaving by mutableStateOf(false); private set
    var aliasError by mutableStateOf<Int?>(null); private set
    var range by mutableStateOf(InsightRange.entries.firstOrNull {
        it.name == (savedState.get<String>("insightRange") ?: initialRange)
    } ?: InsightRange.NINETY); private set
    private var observer: Job? = null
    private var input: Input? = null

    private data class Input(val vaultId: String, val index: SourceIndexSnapshot,
        val records: List<ConfirmedDiaryRecord>, val aliases: ExerciseAliases)

    val activeVault get() = (config as? VaultConfigState.Configured)?.let {
        targetVaultId == null || targetVaultId == it.vaultId
    } == true

    init { observe() }

    private fun observe(refreshAfterRead: Boolean = false) {
        observer?.cancel()
        observer = viewModelScope.launch {
            var refreshRequested = refreshAfterRead
            try {
                configFlow.distinctUntilChanged().collectLatest { value ->
                    config = value; input = null; data = null; index = null; error = null; message = null
                    loading = value == VaultConfigState.Loading || value is VaultConfigState.Configured
                    if (value is VaultConfigState.Failed) error = R.string.vault_error_load_config_failed
                    if (value !is VaultConfigState.Configured || !activeVault) {
                        loading = value == VaultConfigState.Loading; return@collectLatest
                    }
                    if (refreshRequested) {
                        refreshRequested = false
                        refreshIndex(value.vaultId)
                    }
                    try {
                        combine(observeIndex(value.vaultId), reader.observeConfirmedDiaries(value.vaultId),
                            aliasStore.observe(value.vaultId)) { snapshot, records, aliases ->
                            Input(value.vaultId, snapshot, records, aliases)
                        }.collect { next ->
                            if ((config as? VaultConfigState.Configured)?.vaultId == next.vaultId && activeVault) {
                                input = next; index = next.index
                                data = buildInsightData(next.vaultId, next.index, next.records, next.aliases, localDate())
                                loading = false
                            }
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        data = null; input = null; loading = false; error = R.string.insight_read_failed
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                config = VaultConfigState.Failed(e); data = null; input = null; index = null
                loading = false; error = R.string.vault_error_load_config_failed
            }
        }
    }

    fun selectRange(value: InsightRange) { range = value; savedState["insightRange"] = value.name }
    fun refresh() { if (index?.refreshing != true && !aliasSaving) observe(refreshAfterRead = true) }

    /** Local date changes update range boundaries without starting a scan or a timer. */
    fun resumed() {
        val next = input ?: return
        if (data?.today != localDate()) data = buildInsightData(next.vaultId, next.index, next.records, next.aliases, localDate())
    }

    fun diaryRoute(key: SourceKey): FitLogRoute.DiaryDetail? {
        val current = config as? VaultConfigState.Configured
        val source = index?.sources?.filter {
            it.vaultId == key.vaultId && it.path == key.relPath && it.status == IndexedSource.AVAILABLE
        }?.singleOrNull()
        if (!activeVault || current?.vaultId != key.vaultId || source == null) {
            message = R.string.insight_source_unavailable; return null
        }
        val uri = current.uri.toString()
        return FitLogRoute.DiaryDetail(uri, key.vaultId, source.uri, source.path,
            source.directory ?: uri, source.name, openAnalysis = true)
    }

    fun saveAliases(previousPrimaryKey: String?, replacement: ExerciseAliasGroup?, onSaved: () -> Unit) {
        val id = data?.vaultId ?: return
        if (!activeVault || aliasSaving) return
        aliasSaving = true; aliasError = null
        viewModelScope.launch {
            try {
                aliasStore.save(id, previousPrimaryKey, replacement)
                if ((config as? VaultConfigState.Configured)?.vaultId == id && activeVault) onSaved()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if ((config as? VaultConfigState.Configured)?.vaultId == id) aliasError = R.string.insight_alias_save_failed
            } finally { aliasSaving = false }
        }
    }

    fun clearAliasError() { if (!aliasSaving) aliasError = null }
}
