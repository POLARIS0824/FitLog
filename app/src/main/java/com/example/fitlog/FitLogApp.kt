package com.example.fitlog

import androidx.compose.material3.ButtonDefaults
import android.app.Activity
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.unit.dp
import com.example.fitlog.log.AnalysisRuntimeViewModel

import android.annotation.SuppressLint
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
import androidx.compose.runtime.LaunchedEffect
import com.example.fitlog.data.vault.VaultConfigState
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
import com.example.fitlog.navigation.FitLogNavGraph
import com.example.fitlog.navigation.FitLogRoute
import com.example.fitlog.ui.components.FitLogNavigationToolbar
import com.example.fitlog.vault.VaultFlowController
import com.example.fitlog.settings.AppearanceViewModel

/**
 * 负责整个 App 的 UI 框架
 *
 * 使用 Application 持有的依赖，负责生命周期与页面框架。
 *
 * 维护 Navigation3 back stack、VaultFlowController、索引刷新生命周期，并放置 Scaffold、NavGraph 和底部浮动导航
 */
@SuppressLint("LocalContextGetResourceValueCall")
@Composable
internal fun FitLogApp(container: AppContainer, appearance: AppearanceViewModel) {

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val appearanceState by appearance.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(appearanceState.messageId) {
        appearanceState.message?.let {
            appearance.messageShown(appearanceState.messageId)
            snackbarHostState.showSnackbar(context.getString(it))
        }
    }

    val analysisRuntime = viewModel<AnalysisRuntimeViewModel> { container.analysisRuntime() }
    val runtimeState by analysisRuntime.uiState.collectAsStateWithLifecycle()
    val analysisRun = runtimeState.analysis
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        analysisRuntime.onStopped((context as? Activity)?.isChangingConfigurations == true)
    }

    // 当前导航历史
    val backStack = rememberNavBackStack(FitLogRoute.Today)

    val vaultFlow = remember(backStack) { VaultFlowController(backStack) }
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
            FitLogInsetContent(innerPadding) {
                if (analysisRun.active && currentRoute != FitLogRoute.Log) {
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(stringResource(R.string.log_batch_running, analysisRun.items.size, analysisRun.total), Modifier.weight(1f))
                            TextButton(onClick = analysisRuntime::cancelAnalysis, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.ai_cancel_request)) }
                        }
                    }
                }
                FitLogNavGraph(
                    backStack = backStack,
                    container = container,
                    runtime = analysisRuntime,
                    runtimeState = runtimeState,
                    appearance = appearance,
                    onSetupCompleted = vaultFlow::onSetupCompleted,
                    onOpenRoute = vaultFlow::openRoute,
                    onNavigateTo = vaultFlow::navigateTo,
                    onBack = vaultFlow::back,
                    onOpenToday = vaultFlow::openTodayLog,
                    onImportFolder = vaultFlow::importFolder,
                    modifier = Modifier.weight(1f),
                )
            }

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
                    onOpenTodayClick = vaultFlow::openTodayLog,
                    onImportFolderClick = vaultFlow::importFolder,
                )
            }
        }
    }
}
