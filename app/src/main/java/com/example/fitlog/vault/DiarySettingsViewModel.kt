package com.example.fitlog.vault

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fitlog.R
import com.example.fitlog.data.vault.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class DiarySettingsViewModel(
    private val vault: String,
    private val settings: DiarySettingsStore,
    private val directories: DiaryDirectories,
    private val resolveVaultId: suspend (String) -> String = { it },
) : ViewModel() {
    var path by mutableStateOf<List<String>>(emptyList()); private set
    var format by mutableStateOf(DiaryDateFormat.Dashed); private set
    var children by mutableStateOf<List<DiaryDirectory>>(emptyList()); private set
    var loading by mutableStateOf(true); private set
    var saving by mutableStateOf(false); private set
    var completed by mutableStateOf(false); private set
    var error by mutableStateOf<Int?>(null); private set
    private var initialized = false
    private var directoryValid = false
    private var generation = 0
    private var job: Job? = null
    private var vaultId = vault
    val canSave get() = initialized && directoryValid && !loading && !saving

    init { load() }

    fun load() {
        if (saving) return
        val token = ++generation
        job?.cancel()
        loading = true
        error = null
        job = viewModelScope.launch {
            try {
                val config = settings.read(resolveVaultId(vault).also { vaultId = it })
                if (token != generation) return@launch
                path = config.directoryPath
                format = config.dateFormat
                initialized = true
                browse(path)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (token == generation) { error = R.string.diary_settings_failed; loading = false }
            }
        }
    }

    fun browse(newPath: List<String>) {
        if (!initialized || saving) return
        val token = ++generation
        job?.cancel()
        path = newPath.toList()
        loading = true
        directoryValid = false
        children = emptyList()
        error = null
        job = viewModelScope.launch {
            try {
                val uri = directories.resolveDirectory(vault, newPath)
                val result = directories.directories(uri)
                if (token != generation) return@launch
                children = result
                directoryValid = true
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (token == generation) error = R.string.diary_directory_failed
            } finally { if (token == generation) loading = false }
        }
    }

    fun chooseFormat(value: DiaryDateFormat) { if (!saving) format = value }
    fun retry() { if (initialized) browse(path) else load() }
    fun requestBack(onBack: () -> Unit) { if (!saving) onBack() }

    fun save() {
        if (!canSave) return
        saving = true
        error = null
        val config = DiarySettings(path.toList(), format)
        viewModelScope.launch {
            try {
                directories.resolveDirectory(vault, config.directoryPath)
                settings.save(vaultId, config)
                completed = true
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                error = R.string.diary_settings_failed
            } finally { saving = false }
        }
    }
}
