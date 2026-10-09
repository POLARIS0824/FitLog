package com.example.fitlog.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
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
    var preferences by mutableStateOf(AppearancePreferences()); private set
    var initialized by mutableStateOf(false); private set
    var loading by mutableStateOf(true); private set
    var saving by mutableStateOf(false); private set
    var message by mutableStateOf<Int?>(null); private set
    private var observation: Job? = null
    val canEdit get() = initialized && !loading && !saving

    init { reload() }

    fun reload() {
        if (saving) return
        observation?.cancel()
        loading = true
        message = null
        observation = viewModelScope.launch {
            try {
                store.settings.collect {
                    preferences = it
                    initialized = true
                    loading = false
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                initialized = false
                loading = false
                message = R.string.appearance_read_failed
            }
        }
    }

    fun changeThemeMode(mode: ThemeMode) = save(preferences.copy(themeMode = mode))
    fun changeDynamicColor(enabled: Boolean) = save(preferences.copy(dynamicColor = enabled))

    private fun save(next: AppearancePreferences) {
        if (!canEdit || next == preferences) return
        saving = true
        message = null
        viewModelScope.launch {
            try {
                store.save(next)
                preferences = next
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                message = R.string.appearance_save_failed
            } finally {
                saving = false
            }
        }
    }
}
