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

/** Owns one in-flight vault operation. All methods are called on the UI thread. */
internal class VaultFlowController(
    private val backStack: MutableList<NavKey>,
    private val scope: CoroutineScope,
    private val getConfig: suspend () -> VaultConfigState,
    private val checkAccess: suspend (Uri) -> VaultAccessStatus,
    private val saveUri: suspend (Uri) -> Result<Unit>,
    private val showError: (Int) -> Unit,
) {
    var busy by mutableStateOf(false)
        private set
    var saving by mutableStateOf(false)
        private set
    var setupError by mutableStateOf<Int?>(null)
        private set
    private var generation = 0
    private var job: Job? = null

    private fun invalidate() {
        generation++
        job?.cancel()
        job = null
        busy = false
        setupError = null
    }

    fun navigateTo(route: FitLogRoute) {
        if (saving) return
        invalidate()
        if (backStack.isEmpty()) backStack.add(route)
        else backStack[backStack.lastIndex] = route
    }

    fun back() {
        // A DataStore commit cannot be undone by cancelling its caller. Keep the
        // setup visible until its outcome is known instead of promising cancellation.
        if (saving) return
        invalidate()
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }

    fun importFolder() {
        if (saving || !isTopLevel()) return
        invalidate()
        backStack.add(FitLogRoute.VaultSetup())
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

    fun selectFolder(route: FitLogRoute.VaultSetup, uri: Uri) {
        if (busy || backStack.lastOrNull() != route) return
        val token = ++generation
        busy = true
        setupError = null
        job = scope.launch {
            try {
                val access = checkAccess(uri)
                if (token != generation || backStack.lastOrNull() != route) return@launch
                val allowed = access == VaultAccessStatus.CanCreateFiles ||
                    (!route.createAfterSetup && access == VaultAccessStatus.ReadOnly)
                if (!allowed) {
                    setupError = accessError(access)
                    return@launch
                }
                saving = true
                val result = saveUri(uri)
                if (token != generation || backStack.lastOrNull() != route) return@launch
                if (result.isFailure) {
                    setupError = R.string.vault_error_save_config_failed
                } else if (route.createAfterSetup) {
                    backStack[backStack.lastIndex] = FitLogRoute.Editor
                } else if (backStack.size > 1) {
                    backStack.removeAt(backStack.lastIndex)
                }
            } finally {
                if (token == generation) {
                    saving = false
                    busy = false
                }
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
