package com.example.fitlog.settings

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import androidx.lifecycle.viewModelScope
import com.example.fitlog.R
import com.example.fitlog.data.settings.AppearancePreferences
import com.example.fitlog.data.settings.AppearanceSettingsStore
import com.example.fitlog.data.settings.ThemeMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Shared by the app theme and settings; only successfully persisted values are applied. */
internal class AppearanceViewModel(private val store: AppearanceSettingsStore) : ViewModel() {
    private val _uiState = MutableStateFlow(
        AppearanceUiState()
    )
    val uiState: StateFlow<AppearanceUiState> = _uiState.asStateFlow()

    private inline fun updateState(transform: (AppearanceUiState) -> AppearanceUiState) {
        _uiState.update { transform(it) }
    }

    private val preferences get() = _uiState.value.preferences
    private val initialized get() = _uiState.value.initialized
    private val loading get() = _uiState.value.loading
    private val saving get() = _uiState.value.saving
    private val message get() = _uiState.value.message
    private var observation: Job? = null
    private val canEdit get() = _uiState.value.canEdit

    init { reload() }

    fun reload() {
        if (saving) return
        observation?.cancel()
        updateState { current -> current.copy(loading = true, message = null) }
        observation = viewModelScope.launch {
            try {
                store.settings.collect {
                    updateState { current -> current.copy(preferences = it, initialized = true, loading = false) }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                updateState { current -> current.copy(initialized = false, loading = false, message = R.string.appearance_read_failed, messageId = current.messageId + 1) }
            }
        }
    }

    fun messageShown(id: Long) { updateState { if (it.messageId == id) it.copy(message = null) else it } }

    fun changeThemeMode(mode: ThemeMode) = save(preferences.copy(themeMode = mode))
    fun changeDynamicColor(enabled: Boolean) = save(preferences.copy(dynamicColor = enabled))

    private fun save(next: AppearancePreferences) {
        if (!canEdit || next == preferences) return
        updateState { current -> current.copy(saving = true, message = null) }
        viewModelScope.launch {
            try {
                store.save(next)
                updateState { current -> current.copy(preferences = next) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                updateState { current -> current.copy(message = R.string.appearance_save_failed, messageId = current.messageId + 1) }
            } finally {
                updateState { current -> current.copy(saving = false) }
            }
        }
    }
}
