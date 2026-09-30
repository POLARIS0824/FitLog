package com.example.fitlog.vault

import android.net.Uri
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fitlog.R
import com.example.fitlog.data.vault.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class VaultManagementViewModel(
    config: Flow<VaultConfigState>,
    private val inspect: suspend (Uri) -> VaultFolderInfo,
    private val clear: suspend () -> Result<Unit>,
) : ViewModel() {
    var folder by mutableStateOf<VaultFolderInfo?>(null); private set
    var loading by mutableStateOf(true); private set
    var busy by mutableStateOf(false); private set
    var error by mutableStateOf<Int?>(null); private set
    var confirmDisconnect by mutableStateOf(false); private set
    init {
        viewModelScope.launch {
            config.collectLatest { value ->
                folder = null; error = null; loading = true
                try {
                    when (value) {
                        is VaultConfigState.Configured -> folder = inspect(value.uri)
                        is VaultConfigState.Failed -> error = R.string.vault_error_load_config_failed
                        VaultConfigState.Loading -> return@collectLatest
                        VaultConfigState.NotConfigured -> Unit
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    error = R.string.vault_setup_error_access_failed
                } finally { loading = value == VaultConfigState.Loading }
            }
        }
    }
    fun requestDisconnect() { if (!busy && folder != null) confirmDisconnect = true }
    fun cancelDisconnect() { if (!busy) confirmDisconnect = false }
    fun disconnect() = viewModelScope.launch {
        if (busy || !confirmDisconnect) return@launch
        busy = true; error = null
        try {
            clear().getOrThrow()
            folder = null; confirmDisconnect = false
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            error = R.string.vault_disconnect_failed
        } finally { busy = false }
    }
}
