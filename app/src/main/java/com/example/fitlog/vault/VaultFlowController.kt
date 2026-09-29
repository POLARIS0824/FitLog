package com.example.fitlog.vault

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.NavKey
import com.example.fitlog.R
import com.example.fitlog.data.vault.VaultAccessStatus
import com.example.fitlog.data.vault.VaultConfigState
import com.example.fitlog.navigation.FitLogRoute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 负责入口检查与顶级导航协调控制器。
 */
internal class VaultFlowController(
    private val backStack: MutableList<NavKey>,
    private val scope: CoroutineScope,
    private val getConfig: suspend () -> VaultConfigState,
    private val checkAccess: suspend (Uri) -> VaultAccessStatus,
    private val showError: (Int) -> Unit,
) {
    var busy by mutableStateOf(false)
        private set
    private var generation = 0
    private var job: Job? = null

    private fun invalidate() {
        generation++
        job?.cancel()
        job = null
        busy = false
    }

    fun navigateTo(route: FitLogRoute) {
        invalidate()
        if (backStack.isEmpty()) backStack.add(route)
        else backStack[backStack.lastIndex] = route
    }

    fun back() {
        invalidate()
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }

    fun importFolder() {
        if (!isTopLevel()) return
        invalidate()
        backStack.add(FitLogRoute.VaultSetup(createAfterSetup = false))
    }

    fun createFile() {
        if (busy || !isTopLevel()) return
        val origin = backStack.lastOrNull() ?: return
        val token = ++generation
        busy = true
        job = scope.launch {
            try {
                val config = getConfig()
                val access = if (config is VaultConfigState.Configured) checkAccess(config.uri) else null
                if (token != generation || backStack.lastOrNull() != origin) return@launch
                when (config) {
                    is VaultConfigState.Configured -> when (access) {
                        VaultAccessStatus.CanCreateFiles -> backStack.add(FitLogRoute.Editor)
                        VaultAccessStatus.NeedsReauthorization, VaultAccessStatus.DirectoryUnavailable -> {
                            backStack.add(FitLogRoute.VaultSetup(createAfterSetup = true))
                            showError(accessError(access))
                        }
                        else -> showError(accessError(access))
                    }
                    VaultConfigState.NotConfigured -> backStack.add(FitLogRoute.VaultSetup(createAfterSetup = true))
                    is VaultConfigState.Failed -> showError(R.string.vault_error_load_config_failed)
                    VaultConfigState.Loading -> Unit
                }
            } finally {
                if (token == generation) busy = false
            }
        }
    }

    fun onSetupCompleted(route: FitLogRoute.VaultSetup) {
        if (backStack.lastOrNull() != route) return
        invalidate()
        if (route.createAfterSetup) {
            backStack[backStack.lastIndex] = FitLogRoute.Editor
        } else {
            backStack.remove(route)
            if (backStack.isEmpty()) {
                backStack.add(FitLogRoute.Log)
            } else {
                backStack[0] = FitLogRoute.Log
            }
        }
    }

    private fun accessError(access: VaultAccessStatus?): Int = when (access) {
        VaultAccessStatus.NeedsReauthorization -> R.string.vault_error_needs_reauthorization
        VaultAccessStatus.DirectoryUnavailable -> R.string.vault_error_directory_unavailable
        VaultAccessStatus.ReadOnly -> R.string.vault_error_read_only
        else -> R.string.vault_setup_error_access_failed
    }

    private fun isTopLevel(): Boolean = when (backStack.lastOrNull()) {
        FitLogRoute.Today, FitLogRoute.Log, FitLogRoute.Insight -> true
        else -> false
    }
}
