package com.example.fitlog.insight

import androidx.compose.runtime.*
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fitlog.R
import com.example.fitlog.data.analysis.*
import com.example.fitlog.data.index.IndexedSource
import com.example.fitlog.data.index.SourceIndexSnapshot
import com.example.fitlog.data.insight.*
import com.example.fitlog.data.vault.VaultConfigState
import com.example.fitlog.navigation.FitLogRoute
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class InsightViewModel(
    private val configFlow: Flow<VaultConfigState>,
    private val observeIndex: (String) -> Flow<SourceIndexSnapshot>,
    private val reader: TrainingRecordRepository,
    private val aliasStore: ExerciseAliasStore,
    private val savedState: SavedStateHandle,
    private val refreshIndex: (String) -> Unit,
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
    private data class Input(val vaultId: String?, val index: SourceIndexSnapshot?,
        val records: List<TrainingRecordDetails>, val aliases: ExerciseAliases)

    init { observe() }
    private fun observe(refreshAfterRead: Boolean = false) {
        observer?.cancel()
        observer = viewModelScope.launch {
            var shouldRefresh = refreshAfterRead
            try {
                configFlow.distinctUntilChanged().collectLatest { value ->
                    config = value; loading = true; error = null
                    val vault = value as? VaultConfigState.Configured
                    if (shouldRefresh && vault != null) { shouldRefresh = false; refreshIndex(vault.vaultId) }
                    val snapshots = vault?.let {
                        observeIndex(it.vaultId).map<SourceIndexSnapshot, SourceIndexSnapshot?> { snapshot -> snapshot }
                            .catch { e -> if (e is CancellationException) throw e
                                message = R.string.insight_read_failed; emit(null) }
                    } ?: flowOf(null)
                    combine(snapshots, reader.observeRecords(), aliasStore.observe()) { snapshot, records, aliases ->
                        Input(vault?.vaultId, snapshot, records, aliases)
                    }.collect { next ->
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
    }
    fun selectRange(value: InsightRange) { range = value; savedState["insightRange"] = value.name }
    fun refresh() { if (index?.refreshing != true && !aliasSaving) { message = null; observe(true) } }
    fun resumed() { input?.let { next ->
        if (data?.today != localDate()) data = buildInsightData(next.vaultId, next.index, next.records, next.aliases, localDate())
    } }
    fun recordTitle(id: String): String = input?.records?.firstOrNull { it.diary.id == id }?.diary?.let {
        it.relPath ?: it.date
    }.orEmpty()
    fun diaryRoute(id: String): FitLogRoute? {
        val record = input?.records?.firstOrNull { it.diary.id == id }?.diary ?: return null
        if (record.origin == RecordOrigin.NATIVE) return FitLogRoute.NativeRecord(id)
        val current = config as? VaultConfigState.Configured
        val source = index?.sources?.filter { it.vaultId == record.vaultId && it.path == record.relPath && it.status == IndexedSource.AVAILABLE }?.singleOrNull()
        if (current == null || current.vaultId != record.vaultId || source == null) {
            message = R.string.insight_source_unavailable; return null
        }
        val uri = current.uri.toString()
        return FitLogRoute.DiaryDetail(uri, current.vaultId, source.uri, source.path,
            source.directory ?: uri, source.name, openAnalysis = true)
    }
    fun saveAliases(previousPrimaryKey: String?, replacement: ExerciseAliasGroup?, onSaved: () -> Unit) {
        if (aliasSaving) return
        aliasSaving = true; aliasError = null
        viewModelScope.launch {
            try { aliasStore.save(previousPrimaryKey, replacement); onSaved() }
            catch (e: Exception) { if (e is CancellationException) throw e; aliasError = R.string.insight_alias_save_failed }
            finally { aliasSaving = false }
        }
    }
    fun clearAliasError() { if (!aliasSaving) aliasError = null }
}
