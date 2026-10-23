package com.example.fitlog.navigation

import com.example.fitlog.runtime.DiaryAnalysisController
import com.example.fitlog.runtime.AnalysisRuntimeViewModel
import com.example.fitlog.runtime.AppRuntimeUiState
import com.example.fitlog.AppContainer
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import com.example.fitlog.insight.InsightScreen
import com.example.fitlog.insight.InsightViewModel
import com.example.fitlog.insight.ExerciseHistoryScreen
import com.example.fitlog.log.LogScreen
import com.example.fitlog.log.LogSettingsStore
import com.example.fitlog.log.LogViewModel
import com.example.fitlog.log.BatchReviewViewModel
import com.example.fitlog.log.BatchReviewScreen
import com.example.fitlog.today.TodayScreen
import com.example.fitlog.settings.SettingsScreen
import com.example.fitlog.settings.AppearanceScreen
import com.example.fitlog.settings.AppearanceViewModel
import com.example.fitlog.vault.VaultSetupRoute
import com.example.fitlog.diary.DiaryDetailScreen
import com.example.fitlog.diary.DiaryDetailViewModel
import com.example.fitlog.data.vault.VaultConfigState
import com.example.fitlog.vault.VaultManagementViewModel
import com.example.fitlog.vault.VaultManagementScreen
import com.example.fitlog.ai.AiSettingsScreen
import com.example.fitlog.ai.AiSettingsViewModel
import com.example.fitlog.records.*
import com.example.fitlog.R

/**
 * Navigation3 的核心路由表，entry<Route> 中决定该 Route 显示哪个 Screen、ViewModel 如何创建、点击后去哪里，同时注册录入与校对页面的返回拦截
 */
@Composable
internal fun FitLogNavGraph(
    backStack: NavBackStack<NavKey>,
    container: AppContainer,
    runtime: AnalysisRuntimeViewModel,
    runtimeState: AppRuntimeUiState,
    appearance: AppearanceViewModel,
    appearanceState: com.example.fitlog.settings.AppearanceUiState,
    onSetupCompleted: (FitLogRoute.VaultSetup) -> Unit,
    onOpenRoute: (FitLogRoute) -> Unit,
    onNavigateTo: (FitLogRoute) -> Unit,
    onBack: () -> Unit,
    onOpenToday: () -> Unit,
    onImportFolder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val analysisController = runtime.controller
    var showMarkdown by rememberSaveable { mutableStateOf(false) }
    val backHandlers = remember { mutableStateMapOf<NavKey, () -> Unit>() }

    FitLogNavDisplay(
        backStack = backStack,
        modifier = modifier,
        onBack = { backHandlers[backStack.lastOrNull()]?.invoke() ?: onBack() },
        entryProvider = entryProvider {
            entry<FitLogRoute.Today> {
                TodayScreen(
                    onSettings = { if (backStack.lastOrNull() == FitLogRoute.Today) onOpenRoute(FitLogRoute.Settings) },
                    onOpenToday = { if (backStack.lastOrNull() == FitLogRoute.Today) onOpenToday() },
                    onOpenLog = { if (backStack.lastOrNull() == FitLogRoute.Today) onNavigateTo(FitLogRoute.Log) },
                    onImportFolder = { if (backStack.lastOrNull() == FitLogRoute.Today) onImportFolder() },
                )
            }

            entry<FitLogRoute.Settings> {
                SettingsScreen(
                    appearance = appearanceState.preferences,
                    appearanceReady = appearanceState.initialized,
                    config = runtimeState.config,
                    onAppearance = {
                        if (backStack.lastOrNull() == FitLogRoute.Settings) onOpenRoute(FitLogRoute.Appearance)
                    },
                    onAiSettings = {
                        if (backStack.lastOrNull() == FitLogRoute.Settings) onOpenRoute(FitLogRoute.AiSettings)
                    },
                    onVaultManagement = {
                        if (backStack.lastOrNull() == FitLogRoute.Settings) onOpenRoute(FitLogRoute.VaultManagement)
                    },
                    onRetryVault = runtime::retryConfig,
                    onBack = { if (backStack.lastOrNull() == FitLogRoute.Settings) onBack() },
                )
            }

            entry<FitLogRoute.Appearance> {
                AppearanceScreen(appearance) {
                    if (backStack.lastOrNull() == FitLogRoute.Appearance) onBack()
                }
            }

            entry<FitLogRoute.Log> {
                val vm = viewModel<LogViewModel> {
                    container.log(createSavedStateHandle(), analysisController)
                }
                RecordsLogScreen(vm, markdown = showMarkdown, onMarkdown = { showMarkdown = it },
                    onOpenNative = { id -> if (backStack.lastOrNull() == FitLogRoute.Log) onOpenRoute(FitLogRoute.NativeRecord(id)) },
                    onOpenMarkdown = { vault, file -> vm.uiState.value.vaultId?.let { vaultId ->
                        if (backStack.lastOrNull() == FitLogRoute.Log && vm.uiState.value.vault == vault &&
                            vm.uiState.value.visibleFiles.any { it.uri == file.uri && it.path == file.path }) {
                            onOpenRoute(FitLogRoute.DiaryDetail(vault, vaultId, file.uri, file.path, file.directory ?: vault, file.name))
                        }
                    } },
                    onConnect = { showMarkdown = true; onOpenRoute(FitLogRoute.VaultSetup()) },
                    onManage = { onOpenRoute(FitLogRoute.VaultManagement) },
                    onAiSettings = { if (backStack.lastOrNull() == FitLogRoute.Log) onOpenRoute(FitLogRoute.AiSettings) },
                    onReview = { val id = vm.uiState.value.vaultId; val uri = vm.uiState.value.vault
                        if (backStack.lastOrNull() == FitLogRoute.Log && id != null && uri != null)
                            onOpenRoute(FitLogRoute.BatchReview(id, uri))
                    })
            }

            entry<FitLogRoute.BatchReview> { route ->
                val vm = viewModel<BatchReviewViewModel> {
                    container.batchReview(route, createSavedStateHandle())
                }
                DisposableEffect(vm) {
                    backHandlers[route] = { if (!vm.uiState.value.saving) onBack() }
                    onDispose { backHandlers.remove(route) }
                }
                BatchReviewScreen(vm, onOpen = { source ->
                    if (backStack.lastOrNull() == route && vm.uiState.value.activeVault && !vm.uiState.value.saving)
                        onOpenRoute(FitLogRoute.DiaryDetail(route.vaultUri, route.vaultId, source.uri,
                            source.path, source.directory ?: route.vaultUri, source.name, openAnalysis = true, openCandidate = true))
                }, onBack = { if (backStack.lastOrNull() == route) onBack() })
            }

            entry<FitLogRoute.Insight> {
                val vm = viewModel<InsightViewModel> {
                    container.insight(createSavedStateHandle())
                }
                InsightScreen(vm, onOpenLog = {
                    if (backStack.lastOrNull() == FitLogRoute.Insight) onNavigateTo(FitLogRoute.Log)
                }, onOpen = { key ->
                    if (backStack.lastOrNull() == FitLogRoute.Insight) vm.recordDestination(key)?.toRoute()?.let(onOpenRoute)
                }, onExercise = { name ->
                    if (backStack.lastOrNull() == FitLogRoute.Insight)
                        onOpenRoute(FitLogRoute.ExerciseHistory(name, vm.uiState.value.range.name))
                })
            }

            entry<FitLogRoute.ExerciseHistory> { route ->
                val vm = viewModel<InsightViewModel> {
                    container.insight(createSavedStateHandle(), route.range, route.nameKey)
                }
                DisposableEffect(vm) {
                    backHandlers[route] = { if (!vm.uiState.value.aliasSaving) onBack() }
                    onDispose { backHandlers.remove(route) }
                }
                ExerciseHistoryScreen(vm, route.nameKey, onOpen = { key ->
                    if (backStack.lastOrNull() == route) vm.recordDestination(key)?.toRoute()?.let(onOpenRoute)
                }, onOpenLog = { if (backStack.lastOrNull() == route) onNavigateTo(FitLogRoute.Log) },
                    onBack = { if (backStack.lastOrNull() == route && !vm.uiState.value.aliasSaving) onBack() })
            }

            entry<FitLogRoute.DiaryDetail> { route ->
                val vm = viewModel<DiaryDetailViewModel> {
                    container.diary(route, createSavedStateHandle(), analysisController)
                }
                DisposableEffect(vm) {
                    backHandlers[route] = { vm.requestLeave() }
                    onDispose { backHandlers.remove(route) }
                }
                DiaryDetailScreen(vm,
                    onBack = { if (backStack.lastOrNull() == route) onBack() },
                    onAiSettings = { if (backStack.lastOrNull() == route) {
                        onOpenRoute(FitLogRoute.AiSettings)
                    } })
            }

            entry<FitLogRoute.AiSettings> {
                val vm = viewModel<AiSettingsViewModel> {
                    container.aiSettings()
                }
                DisposableEffect(vm) {
                    backHandlers[FitLogRoute.AiSettings] = { if (!vm.uiState.value.saving) { vm.cancelRequest(); onBack() } }
                    onDispose { backHandlers.remove(FitLogRoute.AiSettings) }
                }
                AiSettingsScreen(vm) {
                    if (backStack.lastOrNull() == FitLogRoute.AiSettings && !vm.uiState.value.saving) onBack()
                }
            }

            entry<FitLogRoute.NativeRecord> { route ->
                val vm = viewModel<NativeRecordViewModel>(key = route.sessionId) {
                    container.nativeRecord(route.recordId, createSavedStateHandle())
                }
                DisposableEffect(vm) {
                    backHandlers[route] = { vm.requestLeave() }
                    onDispose { backHandlers.remove(route) }
                }
                NativeRecordScreen(vm) { if (backStack.lastOrNull() == route) onBack() }
            }

            entry<FitLogRoute.VaultSetup> { route ->
                VaultSetupRoute(
                    route = route,
                    factory = container.vaultSetup(route.requestId),
                    onSetupCompleted = { showMarkdown = true; onSetupCompleted(it) },
                    onVaultConnected = { vault ->
                        if (backStack.lastOrNull() == route) runtime.vaultConnected(vault)
                    },
                    onBack = { if (backStack.lastOrNull() == route) onBack() },
                )
            }

            entry<FitLogRoute.VaultManagement> {
                val vm = viewModel<VaultManagementViewModel> {
                    container.vaultManagement()
                }
                DisposableEffect(vm) {
                    backHandlers[FitLogRoute.VaultManagement] = { if (!vm.uiState.value.busy) onBack() }
                    onDispose { backHandlers.remove(FitLogRoute.VaultManagement) }
                }
                VaultManagementScreen(vm, { onOpenRoute(FitLogRoute.VaultSetup()) }, onBack)
            }
        },
    )
}

private fun com.example.fitlog.domain.insight.InsightRecordDestination.toRoute(): FitLogRoute = when (this) {
    is com.example.fitlog.domain.insight.InsightRecordDestination.Native -> FitLogRoute.NativeRecord(id)
    is com.example.fitlog.domain.insight.InsightRecordDestination.Markdown ->
        FitLogRoute.DiaryDetail(vaultUri, vaultId, document, path, directory, name, openAnalysis = true)
}
