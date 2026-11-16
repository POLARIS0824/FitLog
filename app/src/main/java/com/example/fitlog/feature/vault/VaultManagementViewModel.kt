package com.example.fitlog.feature.vault

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fitlog.R
import com.example.fitlog.data.vault.VaultConfigState
import com.example.fitlog.data.vault.VaultFolderInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class VaultManagementViewModel(
    config: Flow<VaultConfigState>,
    private val inspect: suspend (Uri) -> VaultFolderInfo,
    private val clear: suspend () -> Result<Unit>,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        VaultManagementUiState(loading = true)
    )
    val uiState: StateFlow<VaultManagementUiState> = _uiState.asStateFlow()

    private inline fun updateState(transform: (VaultManagementUiState) -> VaultManagementUiState) {
        _uiState.update { transform(it) }
    }

    private val folder get() = _uiState.value.folder
    private val loading get() = _uiState.value.loading
    private val busy get() = _uiState.value.busy
    private val error get() = _uiState.value.error
    private val confirmDisconnect get() = _uiState.value.confirmDisconnect
    init {
        viewModelScope.launch {
            config.collectLatest { value ->
                updateState { current -> current.copy(folder = null, error = null, loading = true) }
                try {
                    when (value) {
                        is VaultConfigState.Configured -> {
                            val info = inspect(value.uri)
                            updateState { it.copy(folder = info) }
                        }
                        is VaultConfigState.Failed -> updateState { current -> current.copy(error = R.string.vault_error_load_config_failed) }
                        VaultConfigState.Loading -> return@collectLatest
                        VaultConfigState.NotConfigured -> Unit
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    updateState { current -> current.copy(error = R.string.vault_setup_error_access_failed) }
                } finally { updateState { current -> current.copy(loading = value == VaultConfigState.Loading) } }
            }
        }
    }
    fun requestDisconnect() { if (!busy && folder != null) updateState { current -> current.copy(confirmDisconnect = true) } }
    fun cancelDisconnect() { if (!busy) updateState { current -> current.copy(confirmDisconnect = false) } }
    fun disconnect() = viewModelScope.launch {
        if (busy || !confirmDisconnect) return@launch
        updateState { current -> current.copy(busy = true, error = null) }
        try {
            clear().getOrThrow()
            updateState { current -> current.copy(folder = null, confirmDisconnect = false) }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            updateState { current -> current.copy(error = R.string.vault_disconnect_failed) }
        } finally { updateState { current -> current.copy(busy = false) } }
    }
}
