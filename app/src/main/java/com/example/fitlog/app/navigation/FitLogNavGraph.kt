package com.example.fitlog.app.navigation

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import com.example.fitlog.app.AppContainer
import com.example.fitlog.app.analysis.AnalysisRuntimeViewModel
import com.example.fitlog.app.analysis.AppRuntimeUiState
import com.example.fitlog.feature.diary.DiaryDetailScreen
import com.example.fitlog.feature.diary.DiaryDetailViewModel
import com.example.fitlog.feature.diary.navigation.DiaryDetailNavKey
import com.example.fitlog.feature.insight.ExerciseHistoryScreen
import com.example.fitlog.feature.insight.InsightRecordDestination
import com.example.fitlog.feature.insight.InsightScreen
import com.example.fitlog.feature.insight.InsightViewModel
import com.example.fitlog.feature.insight.navigation.ExerciseHistoryNavKey
import com.example.fitlog.feature.insight.navigation.InsightNavKey
import com.example.fitlog.feature.log.BatchReviewScreen
import com.example.fitlog.feature.log.BatchReviewViewModel
import com.example.fitlog.feature.log.LogScreen
import com.example.fitlog.feature.log.LogViewModel
import com.example.fitlog.feature.log.navigation.BatchReviewNavKey
import com.example.fitlog.feature.log.navigation.LogNavKey
import com.example.fitlog.feature.record.NativeRecordScreen
import com.example.fitlog.feature.record.NativeRecordViewModel
import com.example.fitlog.feature.record.navigation.NativeRecordNavKey
import com.example.fitlog.feature.settings.AiSettingsScreen
import com.example.fitlog.feature.settings.AiSettingsViewModel
import com.example.fitlog.feature.settings.AppearanceScreen
import com.example.fitlog.feature.settings.AppearanceUiState
import com.example.fitlog.feature.settings.AppearanceViewModel
import com.example.fitlog.feature.settings.SettingsScreen
import com.example.fitlog.feature.settings.navigation.AiSettingsNavKey
import com.example.fitlog.feature.settings.navigation.AppearanceNavKey
import com.example.fitlog.feature.settings.navigation.SettingsNavKey
import com.example.fitlog.feature.today.TodayScreen
import com.example.fitlog.feature.today.navigation.TodayNavKey
import com.example.fitlog.feature.vault.VaultManagementScreen
import com.example.fitlog.feature.vault.VaultManagementViewModel
import com.example.fitlog.feature.vault.VaultSetupRoute
import com.example.fitlog.feature.vault.navigation.VaultManagementNavKey
import com.example.fitlog.feature.vault.navigation.VaultSetupNavKey

/**
 * Navigation3 的核心路由表，entry<Route> 中决定该 Route 显示哪个 Screen、ViewModel 如何创建、点击后去哪里，同时注册录入与校对页面的返回拦截
 */
@Composable
internal fun FitLogNavGraph(
    navigationState: FitLogNavigationState,
    container: AppContainer,
    runtime: AnalysisRuntimeViewModel,
    runtimeState: AppRuntimeUiState,
    appearance: AppearanceViewModel,
    appearanceState: com.example.fitlog.feature.settings.AppearanceUiState,
    onSetupCompleted: (VaultSetupNavKey) -> Unit,
    onOpenRoute: (NavKey) -> Unit,
    onNavigateTo: (NavKey) -> Unit,
    onBack: () -> Unit,
    onOpenToday: () -> Unit,
    onImportFolder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val analysisController = runtime.controller
    val backStack = navigationState.backStack
    var showMarkdown by rememberSaveable { mutableStateOf(false) }
    val backHandlers = remember { mutableStateMapOf<Pair<NavKey, NavKey>, () -> Unit>() }

    FitLogNavDisplay(
        navigationState = navigationState,
        modifier = modifier,
        onBack = { backStack.lastOrNull()?.let { backHandlers[navigationState.currentTab to it] }?.invoke() ?: onBack() },
        entryProvider = { root, key ->
            val backStack = navigationState.backStacks.getValue(root)
            fun isCurrent(route: NavKey) = navigationState.currentTab == root && backStack.lastOrNull() == route
            entryProvider {
                entry<TodayNavKey> {
                    TodayScreen(
                        onSettings = { if (isCurrent(TodayNavKey)) onOpenRoute(SettingsNavKey) },
                        onOpenToday = { if (isCurrent(TodayNavKey)) onOpenToday() },
                        onOpenLog = { if (isCurrent(TodayNavKey)) onNavigateTo(LogNavKey) },
                        onImportFolder = { if (isCurrent(TodayNavKey)) onImportFolder() },
                    )
                }

                entry<SettingsNavKey> {
                    SettingsScreen(
                        appearance = appearanceState.preferences,
                        appearanceReady = appearanceState.initialized,
                        config = runtimeState.config,
                        onAppearance = {
                            if (isCurrent(SettingsNavKey)) onOpenRoute(AppearanceNavKey)
                        },
                        onAiSettings = {
                            if (isCurrent(SettingsNavKey)) onOpenRoute(AiSettingsNavKey)
                        },
                        onVaultManagement = {
                            if (isCurrent(SettingsNavKey)) onOpenRoute(VaultManagementNavKey)
                        },
                        onRetryVault = runtime::retryConfig,
                        onBack = { if (isCurrent(SettingsNavKey)) onBack() },
                    )
                }

                entry<AppearanceNavKey> {
                    AppearanceScreen(appearance) {
                        if (isCurrent(AppearanceNavKey)) onBack()
                    }
                }

                entry<LogNavKey> {
                    val vm = viewModel<LogViewModel> {
                        container.log(createSavedStateHandle(), analysisController)
                    }
                    LogScreen(vm, markdown = showMarkdown, onMarkdown = { showMarkdown = it },
                        onOpenNative = { id -> if (isCurrent(LogNavKey)) onOpenRoute(NativeRecordNavKey(id)) },
                        onOpenMarkdown = { vault, file -> vm.uiState.value.vaultId?.let { vaultId ->
                            if (isCurrent(LogNavKey) && vm.uiState.value.vault == vault &&
                                vm.uiState.value.visibleFiles.any { it.uri == file.uri && it.path == file.path }) {
                                onOpenRoute(DiaryDetailNavKey(vault, vaultId, file.uri, file.path, file.directory ?: vault, file.name))
                            }
                        } },
                        onConnect = { showMarkdown = true; onOpenRoute(VaultSetupNavKey()) },
                        onManage = { onOpenRoute(VaultManagementNavKey) },
                        onAiSettings = { if (isCurrent(LogNavKey)) onOpenRoute(AiSettingsNavKey) },
                        onReview = { val id = vm.uiState.value.vaultId; val uri = vm.uiState.value.vault
                            if (isCurrent(LogNavKey) && id != null && uri != null)
                                onOpenRoute(BatchReviewNavKey(id, uri))
                        })
                }

                entry<BatchReviewNavKey> { route ->
                    val vm = viewModel<BatchReviewViewModel> {
                        container.batchReview(route, createSavedStateHandle())
                    }
                    DisposableEffect(vm) {
                        backHandlers[root to route] = { if (!vm.uiState.value.saving) onBack() }
                        onDispose { backHandlers.remove(root to route) }
                    }
                    BatchReviewScreen(vm, onOpen = { source ->
                        if (isCurrent(route) && vm.uiState.value.activeVault && !vm.uiState.value.saving)
                            onOpenRoute(DiaryDetailNavKey(route.vaultUri, route.vaultId, source.uri,
                                source.path, source.directory ?: route.vaultUri, source.name, openAnalysis = true, openCandidate = true))
                    }, onBack = { if (isCurrent(route)) onBack() })
                }

                entry<InsightNavKey> {
                    val vm = viewModel<InsightViewModel> {
                        container.insight(createSavedStateHandle())
                    }
                    InsightScreen(vm, onOpenLog = {
                        if (isCurrent(InsightNavKey)) onNavigateTo(LogNavKey)
                    }, onOpen = { key ->
                        if (isCurrent(InsightNavKey)) vm.recordDestination(key)?.toRoute()?.let(onOpenRoute)
                    }, onExercise = { name ->
                        if (isCurrent(InsightNavKey))
                            onOpenRoute(ExerciseHistoryNavKey(name, vm.uiState.value.range.name))
                    })
                }

                entry<ExerciseHistoryNavKey> { route ->
                    val vm = viewModel<InsightViewModel> {
                        container.insight(createSavedStateHandle(), route.range, route.nameKey)
                    }
                    DisposableEffect(vm) {
                        backHandlers[root to route] = { if (!vm.uiState.value.aliasSaving) onBack() }
                        onDispose { backHandlers.remove(root to route) }
                    }
                    ExerciseHistoryScreen(vm, route.nameKey, onOpen = { key ->
                        if (isCurrent(route)) vm.recordDestination(key)?.toRoute()?.let(onOpenRoute)
                    }, onOpenLog = { if (isCurrent(route)) onNavigateTo(LogNavKey) },
                        onBack = { if (isCurrent(route) && !vm.uiState.value.aliasSaving) onBack() })
                }

                entry<DiaryDetailNavKey> { route ->
                    val vm = viewModel<DiaryDetailViewModel> {
                        container.diary(route, createSavedStateHandle(), analysisController)
                    }
                    DisposableEffect(vm) {
                        backHandlers[root to route] = { vm.requestLeave() }
                        onDispose { backHandlers.remove(root to route) }
                    }
                    DiaryDetailScreen(vm,
                        onBack = { if (isCurrent(route)) onBack() },
                        onAiSettings = { if (isCurrent(route)) {
                            onOpenRoute(AiSettingsNavKey)
                        } })
                }

                entry<AiSettingsNavKey> {
                    val vm = viewModel<AiSettingsViewModel> {
                        container.aiSettings()
                    }
                    DisposableEffect(vm) {
                        backHandlers[root to AiSettingsNavKey] = { if (!vm.uiState.value.saving) { vm.cancelRequest(); onBack() } }
                        onDispose { backHandlers.remove(root to AiSettingsNavKey) }
                    }
                    AiSettingsScreen(vm) {
                        if (isCurrent(AiSettingsNavKey) && !vm.uiState.value.saving) onBack()
                    }
                }

                entry<NativeRecordNavKey> { route ->
                    val vm = viewModel<NativeRecordViewModel>(key = route.sessionId) {
                        container.nativeRecord(route.recordId, createSavedStateHandle())
                    }
                    DisposableEffect(vm) {
                        backHandlers[root to route] = { vm.requestLeave() }
                        onDispose { backHandlers.remove(root to route) }
                    }
                    NativeRecordScreen(vm) { if (isCurrent(route)) onBack() }
                }

                entry<VaultSetupNavKey> { route ->
                    VaultSetupRoute(
                        route = route,
                        factory = container.vaultSetup(route.requestId),
                        onSetupCompleted = { if (isCurrent(it)) { showMarkdown = true; onSetupCompleted(it) } },
                        onVaultConnected = { vault ->
                            if (isCurrent(route)) runtime.vaultConnected(vault)
                        },
                        onBack = { if (isCurrent(route)) onBack() },
                    )
                }

                entry<VaultManagementNavKey> {
                    val vm = viewModel<VaultManagementViewModel> {
                        container.vaultManagement()
                    }
                    DisposableEffect(vm) {
                        backHandlers[root to VaultManagementNavKey] = { if (!vm.uiState.value.busy) onBack() }
                        onDispose { backHandlers.remove(root to VaultManagementNavKey) }
                    }
                    VaultManagementScreen(vm, { if (isCurrent(VaultManagementNavKey)) onOpenRoute(VaultSetupNavKey()) },
                        { if (isCurrent(VaultManagementNavKey)) onBack() })
                }
            }.invoke(key)
        },
    )
}

private fun InsightRecordDestination.toRoute(): NavKey = when (this) {
    is InsightRecordDestination.Native -> NativeRecordNavKey(id)
    is InsightRecordDestination.Markdown ->
        DiaryDetailNavKey(vaultUri, vaultId, document, path, directory, name, openAnalysis = true)
}
