package com.example.fitlog.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fitlog.data.analysis.DiaryAnalysisRun
import com.example.fitlog.data.index.SourceIndex
import com.example.fitlog.data.vault.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

internal data class AppRuntimeUiState(val config: VaultConfigState = VaultConfigState.Loading,
    val analysis: DiaryAnalysisRun = DiaryAnalysisRun())

/** App-level work survives page navigation and configuration changes. */
internal class AnalysisRuntimeViewModel(
    private val settings: VaultSettings,
    private val index: SourceIndex,
    createController: (CoroutineScope) -> DiaryAnalysisController,
) : ViewModel() {
    val controller = createController(viewModelScope)
    private val config = MutableStateFlow<VaultConfigState>(VaultConfigState.Loading)
    val uiState = combine(config, controller.state, ::AppRuntimeUiState)
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppRuntimeUiState())
    private var observation: Job? = null
    init { retryConfig() }
    fun retryConfig() {
        observation?.cancel()
        observation = viewModelScope.launch {
            settings.vaultConfig.collect { value ->
                config.value = value
                if (value !is VaultConfigState.Loading) {
                    val id = (value as? VaultConfigState.Configured)?.vaultId
                    index.activate(id)
                    controller.activate(id)
                }
            }
        }
    }
    suspend fun vaultConnected(uri: String) {
        val current = settings.getVaultConfig()
        if (current is VaultConfigState.Configured && current.uri.toString() == uri) {
            index.activate(current.vaultId)
            controller.activate(current.vaultId)
            index.refresh(current.vaultId)
        }
    }
    fun onStopped(changingConfiguration: Boolean) { if (!changingConfiguration) controller.cancel() }
    fun cancelAnalysis() = controller.cancel()
}
