package com.example.fitlog.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.createSavedStateHandle
import com.example.fitlog.editor.EditorViewModel
import com.example.fitlog.editor.EditorDraftStore
import com.example.fitlog.data.vault.MarkdownDocumentRepository
import java.io.File
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.example.fitlog.data.vault.VaultPreferences
import com.example.fitlog.data.vault.VaultRepository
import com.example.fitlog.editor.EditorScreen
import com.example.fitlog.insight.InsightScreen
import com.example.fitlog.log.LogScreen
import com.example.fitlog.log.LogSettingsStore
import com.example.fitlog.log.LogViewModel
import com.example.fitlog.today.TodayScreen
import com.example.fitlog.settings.SettingsScreen
import com.example.fitlog.vault.VaultSetupRoute
import com.example.fitlog.vault.DiarySettingsScreen
import com.example.fitlog.vault.DiarySettingsViewModel
import com.example.fitlog.data.index.SourceIndexRepository
import com.example.fitlog.data.analysis.DiaryAnalysisRepository
import com.example.fitlog.diary.DiaryDetailScreen
import com.example.fitlog.diary.DiaryDetailViewModel
import com.example.fitlog.data.vault.VaultConfigState
import com.example.fitlog.editor.RecoveryViewModel
import com.example.fitlog.editor.RecoveryScreen
import com.example.fitlog.vault.VaultManagementViewModel
import com.example.fitlog.vault.VaultManagementScreen
import com.example.fitlog.ai.AiSettingsScreen
import com.example.fitlog.ai.AiSettingsViewModel
import com.example.fitlog.R
import com.example.fitlog.data.ai.AiProviderRepository
import com.example.fitlog.data.analysis.SourceKey
import com.example.fitlog.data.analysis.adapter.fetchOpenAiModels
import com.example.fitlog.data.analysis.adapter.testOpenAiConnection
import com.example.fitlog.data.analysis.adapter.parseConfiguredDiary
import io.ktor.client.HttpClient

/**
 * Navigation3 的核心路由表，entry<Route> 中决定该 Route 显示哪个 Screen、ViewModel 如何创建、点击后去哪里，同时注册编辑器等页面的返回拦截
 */
@Composable
fun FitLogNavGraph(
    backStack: NavBackStack<NavKey>,
    vaultPreferences: VaultPreferences,
    logSettings: LogSettingsStore,
    vaultRepository: VaultRepository,
    sourceIndex: SourceIndexRepository,
    analysisRepository: DiaryAnalysisRepository,
    aiRepository: AiProviderRepository,
    aiClient: HttpClient,
    onSetupCompleted: (FitLogRoute.VaultSetup) -> Unit,
    onOpenRoute: (FitLogRoute) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motionScheme = MaterialTheme.motionScheme
    val context = LocalContext.current.applicationContext
    val documents = remember(context) { MarkdownDocumentRepository(context) }
    val drafts = remember(context) { EditorDraftStore(File(context.filesDir, "editor-drafts")) }
    val backHandlers = remember { mutableStateMapOf<NavKey, () -> Unit>() }

    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        onBack = { backHandlers[backStack.lastOrNull()]?.invoke() ?: onBack() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        transitionSpec = {
            fadeIn(
                animationSpec = motionScheme.defaultEffectsSpec(),
            ) togetherWith fadeOut(
                animationSpec = motionScheme.defaultEffectsSpec(),
            )
        },
        popTransitionSpec = {
            fadeIn(
                animationSpec = motionScheme.defaultEffectsSpec(),
            ) togetherWith fadeOut(
                animationSpec = motionScheme.defaultEffectsSpec(),
            )
        },
        predictivePopTransitionSpec = {
            fadeIn(
                animationSpec = motionScheme.defaultEffectsSpec(),
            ) togetherWith fadeOut(
                animationSpec = motionScheme.defaultEffectsSpec(),
            )
        },
        entryProvider = entryProvider {
            entry<FitLogRoute.Today> {
                TodayScreen(onSettings = {
                    if (backStack.lastOrNull() == FitLogRoute.Today) onOpenRoute(FitLogRoute.Settings)
                })
            }

            entry<FitLogRoute.Settings> {
                SettingsScreen(
                    onAiSettings = {
                        if (backStack.lastOrNull() == FitLogRoute.Settings) onOpenRoute(FitLogRoute.AiSettings)
                    },
                    onBack = { if (backStack.lastOrNull() == FitLogRoute.Settings) onBack() },
                )
            }

            entry<FitLogRoute.Log> {
                val vm = viewModel<LogViewModel> {
                    LogViewModel(createSavedStateHandle(), vaultPreferences.vaultConfig, sourceIndex, logSettings)
                }
                LogScreen(vm,
                    onOpen = { vault, file -> vm.vaultId?.let { vaultId ->
                        if (backStack.lastOrNull() == FitLogRoute.Log && vm.vault == vault &&
                            vm.visibleFiles.any { it.uri == file.uri && it.path == file.path }) {
                            onOpenRoute(FitLogRoute.DiaryDetail(vault, vaultId, file.uri, file.path, file.directory ?: vault, file.name))
                        }
                    } },
                    onSettings = { vault -> onOpenRoute(FitLogRoute.DiarySettings(vault, requireNotNull(vm.vaultId))) },
                    onConnect = { onOpenRoute(FitLogRoute.VaultSetup()) },
                    onRecovery = { onOpenRoute(FitLogRoute.RecoveryCenter) },
                    onManage = { onOpenRoute(FitLogRoute.VaultManagement) })
            }

            entry<FitLogRoute.Insight> {
                InsightScreen()
            }

            entry<FitLogRoute.DiaryDetail> { route ->
                val vm = viewModel<DiaryDetailViewModel> {
                    DiaryDetailViewModel(route, documents, analysisRepository, createSavedStateHandle(),
                        parseDiary = { parseConfiguredDiary(SourceKey(route.vaultId, route.relPath), route.document,
                            aiRepository, documents, analysisRepository, aiClient) })
                }
                DisposableEffect(vm) {
                    backHandlers[route] = { vm.cancelParse(); onBack() }
                    onDispose { backHandlers.remove(route) }
                }
                DiaryDetailScreen(vm,
                    onEdit = { if (backStack.lastOrNull() == route) vm.editorRoute()?.let {
                        vm.cancelParse(); onOpenRoute(it)
                    } },
                    onBack = { if (backStack.lastOrNull() == route) { vm.cancelParse(); onBack() } },
                    onAiSettings = { if (backStack.lastOrNull() == route) {
                        vm.cancelParse(); onOpenRoute(FitLogRoute.AiSettings)
                    } })
            }

            entry<FitLogRoute.AiSettings> {
                val vm = viewModel<AiSettingsViewModel> {
                    AiSettingsViewModel(aiRepository, context.getString(R.string.ai_deepseek),
                        fetchModels = { url, key -> fetchOpenAiModels(aiClient, url, key) },
                        testConnection = { url, model, key -> testOpenAiConnection(aiClient, url, model, key) })
                }
                DisposableEffect(vm) {
                    backHandlers[FitLogRoute.AiSettings] = { if (!vm.saving) { vm.cancelRequest(); onBack() } }
                    onDispose { backHandlers.remove(FitLogRoute.AiSettings) }
                }
                AiSettingsScreen(vm) {
                    if (backStack.lastOrNull() == FitLogRoute.AiSettings && !vm.saving) onBack()
                }
            }

            entry<FitLogRoute.Editor> { route ->
                val vm = viewModel<EditorViewModel>(key = route.sessionId) {
                    EditorViewModel(route, documents, drafts, createSavedStateHandle()) { snapshot, directory, path ->
                        sourceIndex.recordSaved(route.vaultId, snapshot, directory, path)
                    }
                }
                DisposableEffect(vm) {
                    backHandlers[route] = { vm.requestExit(onBack) }
                    onDispose { backHandlers.remove(route) }
                }
                EditorScreen(vm = vm, onBack = onBack)
            }

            entry<FitLogRoute.VaultSetup> { route ->
                VaultSetupRoute(
                    route = route,
                    vaultPreferences = vaultPreferences,
                    vaultRepository = vaultRepository,
                    onSetupCompleted = onSetupCompleted,
                    onVaultConnected = { vault ->
                        // A completed connection explicitly requests a scan, including reconnecting the same folder.
                        val config = vaultPreferences.getVaultConfig()
                        if (backStack.lastOrNull() == route && config is VaultConfigState.Configured &&
                            config.uri.toString() == vault) {
                            sourceIndex.activate(config.vaultId)
                            sourceIndex.refresh(config.vaultId)
                        }
                    },
                    onBack = onBack,
                )
            }

            entry<FitLogRoute.DiarySettings> { route ->
                val vm = viewModel<DiarySettingsViewModel> {
                    DiarySettingsViewModel(route.vaultUri, route.vaultId, vaultPreferences.diary, documents)
                }
                DisposableEffect(vm) {
                    backHandlers[route] = { vm.requestBack(onBack) }
                    onDispose { backHandlers.remove(route) }
                }
                DiarySettingsScreen(vm) { if (backStack.lastOrNull() == route) onBack() }
            }
            entry<FitLogRoute.RecoveryCenter> {
                val vm = viewModel<RecoveryViewModel> {
                    RecoveryViewModel(createSavedStateHandle(), drafts, vaultPreferences::getVaultUri)
                }
                val config by vaultPreferences.vaultConfig.collectAsState(initial = VaultConfigState.Loading)
                DisposableEffect(vm) {
                    backHandlers[FitLogRoute.RecoveryCenter] = { if (vm.selectedId != null) vm.select(null) else onBack() }
                    onDispose { backHandlers.remove(FitLogRoute.RecoveryCenter) }
                }
                RecoveryScreen(vm, (config as? VaultConfigState.Configured)?.vaultId, onOpenRoute, onBack)
            }
            entry<FitLogRoute.VaultManagement> {
                val vm = viewModel<VaultManagementViewModel> {
                    VaultManagementViewModel(vaultPreferences.vaultConfig, vaultRepository::inspectFolder, vaultPreferences::clearVaultUri)
                }
                DisposableEffect(vm) {
                    backHandlers[FitLogRoute.VaultManagement] = { if (!vm.busy) onBack() }
                    onDispose { backHandlers.remove(FitLogRoute.VaultManagement) }
                }
                VaultManagementScreen(vm, { onOpenRoute(FitLogRoute.VaultSetup()) }, onBack)
            }
        },
    )
}
