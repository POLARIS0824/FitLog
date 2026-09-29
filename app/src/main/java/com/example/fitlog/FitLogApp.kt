package com.example.fitlog

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.rememberNavBackStack
import com.example.fitlog.data.vault.VaultPreferences
import com.example.fitlog.data.vault.VaultRepository
import com.example.fitlog.navigation.FitLogNavGraph
import com.example.fitlog.navigation.FitLogRoute
import com.example.fitlog.ui.components.FitLogNavigationToolbar
import com.example.fitlog.vault.VaultFlowController
import kotlinx.coroutines.launch

/**
 * 负责整个 App 的 UI 框架
 */
@Composable
fun FitLogApp() {

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val vaultPreferences = remember { VaultPreferences(context) }
    val vaultRepository = remember { VaultRepository(context) }

    // 当前导航历史
    val backStack = rememberNavBackStack(FitLogRoute.Today)

    val vaultFlow = remember(backStack, coroutineScope) {
        VaultFlowController(
            backStack = backStack,
            scope = coroutineScope,
            getConfig = vaultPreferences::getVaultConfig,
            checkAccess = vaultRepository::checkAccess,
            saveUri = vaultPreferences::setVaultUri,
            showError = { message ->
                coroutineScope.launch { snackbarHostState.showSnackbar(context.getString(message)) }
            },
        )
    }
    val currentRoute = backStack.lastOrNull()

    val showNavigationToolbar =
        currentRoute == FitLogRoute.Today ||
        currentRoute == FitLogRoute.Log ||
        currentRoute == FitLogRoute.Insight

    var fabMenuExpanded by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize()) {
            FitLogNavGraph(
                backStack = backStack,
                onVaultSelected = vaultFlow::selectFolder,
                onBack = vaultFlow::back,
                vaultBusy = vaultFlow.busy,
                vaultSaving = vaultFlow.saving,
                vaultError = vaultFlow.setupError,
                modifier = Modifier.padding(innerPadding)
            )

            AnimatedVisibility(
                visible = showNavigationToolbar && fabMenuExpanded,
                enter = fadeIn(animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec()),
                exit = fadeOut(animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec()),
                modifier = Modifier.matchParentSize(),
            ) {
                // Consume outside taps without triggering the page underneath and dim the background.
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
                        .clickable(
                            interactionSource = null,
                            indication = null,
                            onClickLabel = stringResource(R.string.cd_close_menu),
                        ) { fabMenuExpanded = false }
                )
            }

            if (showNavigationToolbar) {
                FitLogNavigationToolbar(
                    // The menu supplies its own bottom spacing; only add Scaffold's safe insets.
                    modifier = Modifier.align(Alignment.BottomCenter).padding(innerPadding),
                    currentRoute = currentRoute,
                    fabMenuExpanded = fabMenuExpanded,
                    onFabMenuExpandedChange = { fabMenuExpanded = it },
                    onDestinationClick = { vaultFlow.navigateTo(it.route) },
                    onCreateFileClick = vaultFlow::createFile,
                    onImportFolderClick = vaultFlow::importFolder,
                )
            }
        }
    }
}
