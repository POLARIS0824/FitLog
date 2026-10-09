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
import kotlinx.coroutines.CancellationException
import com.example.fitlog.data.vault.DiaryCreationUnavailable
import java.time.LocalDate

/**
 * 负责入口检查与顶级导航协调控制器。
 */
internal class VaultFlowController(
    private val backStack: MutableList<NavKey>,
    private val scope: CoroutineScope,
    private val getConfig: suspend () -> VaultConfigState,
    private val checkAccess: suspend (Uri) -> VaultAccessStatus,
    private val showError: (Int) -> Unit,
    private val resolveToday: suspend (VaultConfigState.Configured, LocalDate) -> FitLogRoute.Editor,
    private val today: () -> LocalDate = { LocalDate.now() },
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

    fun openRoute(route: FitLogRoute) {
        val current = backStack.lastOrNull()
        val detailEditor = current is FitLogRoute.DiaryDetail && route is FitLogRoute.Editor &&
            current.vaultUri == route.vaultUri && current.vaultId == route.vaultId && current.document == route.document
        val aiSettings = route == FitLogRoute.AiSettings &&
            (current is FitLogRoute.DiaryDetail || current == FitLogRoute.Settings)
        val settingsChild = current == FitLogRoute.Settings &&
            (route == FitLogRoute.Appearance || route == FitLogRoute.VaultManagement || route is FitLogRoute.DiarySettings)
        val nested = detailEditor || (current == FitLogRoute.RecoveryCenter && route is FitLogRoute.Editor) ||
            (current == FitLogRoute.VaultManagement && route is FitLogRoute.VaultSetup) || aiSettings || settingsChild
        if (!isTopLevel() && !nested) return
        invalidate()
        backStack.add(route)
    }

    fun importFolder() {
        if (!isTopLevel()) return
        invalidate()
        backStack.add(FitLogRoute.VaultSetup(createAfterSetup = false))
    }

    fun openTodayLog() {
        if (busy || !isTopLevel()) return
        val origin = backStack.lastOrNull() ?: return
        val date = today()
        val token = ++generation
        busy = true
        job = scope.launch {
            try {
                val config = getConfig()
                val access = if (config is VaultConfigState.Configured) checkAccess(config.uri) else null
                if (token != generation || backStack.lastOrNull() != origin) return@launch
                when (config) {
                    is VaultConfigState.Configured -> when (access) {
                        VaultAccessStatus.CanCreateFiles, VaultAccessStatus.ReadOnly -> {
                            val editor = resolveToday(config, date)
                            if (token == generation && backStack.lastOrNull() == origin) backStack.add(editor)
                        }
                        VaultAccessStatus.NeedsReauthorization, VaultAccessStatus.DirectoryUnavailable -> {
                            backStack.add(FitLogRoute.VaultSetup(createAfterSetup = true, todayDate = date.toString()))
                            showError(accessError(access))
                        }
                        else -> showError(accessError(access))
                    }
                    VaultConfigState.NotConfigured -> backStack.add(FitLogRoute.VaultSetup(createAfterSetup = true, todayDate = date.toString()))
                    is VaultConfigState.Failed -> showError(R.string.vault_error_load_config_failed)
                    VaultConfigState.Loading -> Unit
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (token == generation && backStack.lastOrNull() == origin) showError(openError(e))
            } finally {
                if (token == generation) busy = false
            }
        }
    }

    fun onSetupCompleted(route: FitLogRoute.VaultSetup) {
        if (backStack.lastOrNull() != route) return
        invalidate()
        if (route.createAfterSetup) {
            val token = generation
            busy = true
            job = scope.launch {
                try {
                    val config = getConfig()
                    val access = if (config is VaultConfigState.Configured) checkAccess(config.uri) else null
                    if (token != generation || backStack.lastOrNull() != route) return@launch
                    if (config is VaultConfigState.Configured &&
                        access?.usable == true) {
                        val editor = resolveToday(config, LocalDate.parse(route.todayDate))
                        if (token == generation && backStack.lastOrNull() == route) backStack[backStack.lastIndex] = editor
                    } else {
                        returnToLog(route)
                        showError(if (config is VaultConfigState.Failed) R.string.vault_error_load_config_failed else accessError(access))
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    if (token == generation && backStack.lastOrNull() == route) {
                        // Connection succeeded, but today's target needs attention. Log offers settings/reconnect.
                        returnToLog(route)
                        showError(openError(e))
                    }
                } finally { if (token == generation) busy = false }
            }
        } else {
            returnToLog(route)
        }
    }

    private fun returnToLog(route: FitLogRoute.VaultSetup) {
        if (backStack.lastOrNull() != route) return
        backStack.clear()
        backStack.add(FitLogRoute.Log)
    }

    private fun openError(error: Exception): Int = when (error) {
        is DiaryCreationUnavailable -> R.string.vault_error_read_only
        is SecurityException -> R.string.vault_error_needs_reauthorization
        else -> R.string.diary_open_failed
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
