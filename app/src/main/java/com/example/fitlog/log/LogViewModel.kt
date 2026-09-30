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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.util.Locale

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
) : ViewModel() {
    var vault by mutableStateOf<String?>(null); private set
    var files by mutableStateOf<List<MarkdownFile>>(emptyList()); private set
    var query by mutableStateOf(savedState.get<String>("query") ?: ""); private set
    var sort by mutableStateOf(LogSortOrder.Descending); private set
    var sortBusy by mutableStateOf(true); private set
    var sortError by mutableStateOf<Int?>(null); private set
    var loading by mutableStateOf(true); private set
    var partial by mutableStateOf(false); private set
    var error by mutableStateOf<Int?>(null); private set
    var scanStatus by mutableStateOf<String?>(null); private set
    var showMissing by mutableStateOf(savedState.get<Boolean>("showMissing") ?: false); private set
    var sources by mutableStateOf<List<IndexedSource>>(emptyList()); private set
    val visibleFiles by derivedStateOf {
        filterAndSortFiles(sources.filter { showMissing || it.status != IndexedSource.MISSING }.map { it.file() }, query, sort)
    }
    fun toggleMissing() { showMissing = !showMissing; savedState["showMissing"] = showMissing }
    private var currentConfig: VaultConfigState = VaultConfigState.Loading
    private var generation = 0
    private var scanJob: Job? = null
    private var configJob: Job? = null
    private var indexJob: Job? = null

    init {
        viewModelScope.launch {
            try { sort = settings.readSort() }
            catch (e: Exception) { if (e is CancellationException) throw e; sortError = R.string.log_sort_failed }
            finally { sortBusy = false }
        }
        observeConfig()
    }

    private fun observeConfig() {
        configJob?.cancel()
        configJob = viewModelScope.launch {
            config.distinctUntilChanged().collect { value ->
                currentConfig = value
                val nextVault = (value as? VaultConfigState.Configured)?.uri?.toString()
                if (vault != nextVault) {
                    vault?.let(index::cancel)
                    indexJob?.cancel()
                    files = emptyList(); sources = emptyList(); partial = false; scanStatus = null
                }
                vault = nextVault
                nextVault?.let(::observeIndex)
                startScan()
            }
        }
    }

    fun search(value: String) { query = value; savedState["query"] = value }

    private fun observeIndex(source: String) {
        if (indexJob?.isActive == true) return
        indexJob = viewModelScope.launch {
            try {
                index.observe(source).collect { snapshot ->
                    if (vault != source) return@collect
                    sources = snapshot.sources
                    files = sources.map { it.file() }
                    scanStatus = snapshot.scan?.status
                    partial = scanStatus == IndexedScan.PARTIAL
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (vault == source) error = R.string.index_read_failed
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
        vault?.let(::observeIndex)
        if (currentConfig is VaultConfigState.Failed) observeConfig()
        else if (scanJob?.isActive != true) startScan()
    }

    private fun startScan() {
        val token = ++generation
        scanJob?.cancel()
        error = null
        loading = currentConfig != VaultConfigState.NotConfigured
        when (currentConfig) {
            VaultConfigState.Loading -> return
            VaultConfigState.NotConfigured -> { loading = false; return }
            is VaultConfigState.Failed -> { loading = false; error = R.string.vault_error_load_config_failed; return }
            is VaultConfigState.Configured -> Unit
        }
        val source = vault ?: return
        scanJob = viewModelScope.launch {
            try {
                index.refresh(source)
                if (token != generation) return@launch
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (token == generation) error = R.string.log_failed
            } finally { if (token == generation) loading = false }
        }
    }

    override fun onCleared() { vault?.let(index::cancel); super.onCleared() }
}
