package com.example.fitlog.navigation

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import com.example.fitlog.data.vault.MarkdownDocumentRepository
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import com.example.fitlog.data.vault.VaultPreferences
import com.example.fitlog.data.vault.VaultRepository
import com.example.fitlog.insight.InsightScreen
import com.example.fitlog.insight.InsightViewModel
import com.example.fitlog.insight.ExerciseHistoryScreen
import com.example.fitlog.data.insight.DataStoreExerciseAliasStore
import com.example.fitlog.data.insight.InsightPreferencesStore
import com.example.fitlog.log.LogScreen
import com.example.fitlog.log.LogSettingsStore
import com.example.fitlog.log.LogViewModel
import com.example.fitlog.log.BatchReviewViewModel
import com.example.fitlog.log.BatchReviewScreen
import com.example.fitlog.data.analysis.DiaryAnalysisController
import com.example.fitlog.data.analysis.AnalysisSource
import com.example.fitlog.data.analysis.confirmCurrentDiary
import kotlinx.coroutines.CancellationException
import com.example.fitlog.today.TodayScreen
import com.example.fitlog.settings.SettingsScreen
import com.example.fitlog.settings.AppearanceScreen
import com.example.fitlog.settings.AppearanceViewModel
import com.example.fitlog.vault.VaultSetupRoute
import com.example.fitlog.data.index.SourceIndexRepository
import com.example.fitlog.data.analysis.DiaryAnalysisRepository
import com.example.fitlog.diary.DiaryDetailScreen
import com.example.fitlog.diary.DiaryDetailViewModel
import com.example.fitlog.data.vault.VaultConfigState
import com.example.fitlog.vault.VaultManagementViewModel
import com.example.fitlog.vault.VaultManagementScreen
import com.example.fitlog.ai.AiSettingsScreen
import com.example.fitlog.ai.AiSettingsViewModel
import com.example.fitlog.records.*
import com.example.fitlog.data.analysis.TrainingRecordRepository
import com.example.fitlog.data.analysis.DiaryAnalysisDatabase
import com.example.fitlog.R
import com.example.fitlog.data.ai.AiProviderRepository
import com.example.fitlog.data.analysis.SourceKey
import com.example.fitlog.data.analysis.adapter.fetchOpenAiModels
import com.example.fitlog.data.analysis.adapter.testOpenAiConnection
import io.ktor.client.HttpClient

/**
 * Navigation3 的核心路由表，entry<Route> 中决定该 Route 显示哪个 Screen、ViewModel 如何创建、点击后去哪里，同时注册录入与校对页面的返回拦截
 */
@Composable
internal fun FitLogNavGraph(
    backStack: NavBackStack<NavKey>,
    vaultPreferences: VaultPreferences,
    logSettings: LogSettingsStore,
    vaultRepository: VaultRepository,
    sourceIndex: SourceIndexRepository,
    analysisRepository: DiaryAnalysisRepository,
    analysisController: DiaryAnalysisController,
    aiRepository: AiProviderRepository,
    aiClient: HttpClient,
    appearance: AppearanceViewModel,
    onSetupCompleted: (FitLogRoute.VaultSetup) -> Unit,
    onOpenRoute: (FitLogRoute) -> Unit,
    onNavigateTo: (FitLogRoute) -> Unit,
    onBack: () -> Unit,
    onOpenToday: () -> Unit,
    onImportFolder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current.applicationContext
    val documents = remember(context) { MarkdownDocumentRepository(context) }
    val records = remember(context) { TrainingRecordRepository(DiaryAnalysisDatabase.get(context)) }
    val exerciseAliases = remember(context) { DataStoreExerciseAliasStore(context) }
    val insightPreferences = remember(context) { InsightPreferencesStore(context) }
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
                var retry by remember { mutableIntStateOf(0) }
                val config by key(retry) {
                    vaultPreferences.vaultConfig.collectAsStateWithLifecycle(initialValue = VaultConfigState.Loading)
                }
                SettingsScreen(
                    appearance = appearance.preferences,
                    appearanceReady = appearance.initialized,
                    config = config,
                    onAppearance = {
                        if (backStack.lastOrNull() == FitLogRoute.Settings) onOpenRoute(FitLogRoute.Appearance)
                    },
                    onAiSettings = {
                        if (backStack.lastOrNull() == FitLogRoute.Settings) onOpenRoute(FitLogRoute.AiSettings)
                    },
                    onVaultManagement = {
                        if (backStack.lastOrNull() == FitLogRoute.Settings) onOpenRoute(FitLogRoute.VaultManagement)
                    },
                    onRetryVault = { retry++ },
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
                    LogViewModel(createSavedStateHandle(), vaultPreferences.vaultConfig, sourceIndex, logSettings,
                        analysisRepository, analysisController)
                }
                RecordsLogScreen(vm, records, markdown = showMarkdown, onMarkdown = { showMarkdown = it },
                    onOpenNative = { id -> if (backStack.lastOrNull() == FitLogRoute.Log) onOpenRoute(FitLogRoute.NativeRecord(id)) },
                    onOpenMarkdown = { vault, file -> vm.vaultId?.let { vaultId ->
                        if (backStack.lastOrNull() == FitLogRoute.Log && vm.vault == vault &&
                            vm.visibleFiles.any { it.uri == file.uri && it.path == file.path }) {
                            onOpenRoute(FitLogRoute.DiaryDetail(vault, vaultId, file.uri, file.path, file.directory ?: vault, file.name))
                        }
                    } },
                    onConnect = { showMarkdown = true; onOpenRoute(FitLogRoute.VaultSetup()) },
                    onManage = { onOpenRoute(FitLogRoute.VaultManagement) },
                    onAiSettings = { if (backStack.lastOrNull() == FitLogRoute.Log) onOpenRoute(FitLogRoute.AiSettings) },
                    onReview = { val id = vm.vaultId; val uri = vm.vault
                        if (backStack.lastOrNull() == FitLogRoute.Log && id != null && uri != null)
                            onOpenRoute(FitLogRoute.BatchReview(id, uri))
                    })
            }

            entry<FitLogRoute.BatchReview> { route ->
                val vm = viewModel<BatchReviewViewModel> {
                    lateinit var reviewVm: BatchReviewViewModel
                    reviewVm = BatchReviewViewModel(route, createSavedStateHandle(), sourceIndex.observe(route.vaultId),
                        analysisRepository, vaultPreferences.vaultConfig,
                        confirm = { source, request -> confirmCurrentDiary(request, source.uri, documents, analysisRepository) { snapshot ->
                            try { sourceIndex.recordRead(route.vaultId, source.path, snapshot) } catch (e: Exception) {
                                if (e is CancellationException) throw e
                                reviewVm.reportIndexWarning()
                            }
                        } }, refreshIndex = { sourceIndex.refresh(route.vaultId) })
                    reviewVm
                }
                DisposableEffect(vm) {
                    backHandlers[route] = { vm.requestLeave(onBack) }
                    onDispose { backHandlers.remove(route) }
                }
                BatchReviewScreen(vm, onOpen = { source ->
                    if (backStack.lastOrNull() == route && vm.activeVault)
                        vm.requestLeave { onOpenRoute(FitLogRoute.DiaryDetail(route.vaultUri, route.vaultId, source.uri,
                            source.path, source.directory ?: route.vaultUri, source.name, openAnalysis = true, openCandidate = true)) }
                }, onBack = { if (backStack.lastOrNull() == route) onBack() })
            }

            entry<FitLogRoute.Insight> {
                val vm = viewModel<InsightViewModel> {
                    InsightViewModel(vaultPreferences.vaultConfig, sourceIndex::observe, records,
                        exerciseAliases, createSavedStateHandle(), refreshIndex = { sourceIndex.refresh(it) },
                        preferences = insightPreferences)
                }
                InsightScreen(vm, onOpenLog = {
                    if (backStack.lastOrNull() == FitLogRoute.Insight) onNavigateTo(FitLogRoute.Log)
                }, onOpen = { key ->
                    if (backStack.lastOrNull() == FitLogRoute.Insight) vm.diaryRoute(key)?.let(onOpenRoute)
                }, onExercise = { name ->
                    if (backStack.lastOrNull() == FitLogRoute.Insight)
                        onOpenRoute(FitLogRoute.ExerciseHistory(name, vm.range.name))
                })
            }

            entry<FitLogRoute.ExerciseHistory> { route ->
                val vm = viewModel<InsightViewModel> {
                    InsightViewModel(vaultPreferences.vaultConfig, sourceIndex::observe, records,
                        exerciseAliases, createSavedStateHandle(), refreshIndex = { sourceIndex.refresh(it) },
                        initialRange = route.range, preferences = insightPreferences)
                }
                DisposableEffect(vm) {
                    backHandlers[route] = { if (!vm.aliasSaving) onBack() }
                    onDispose { backHandlers.remove(route) }
                }
                ExerciseHistoryScreen(vm, route.nameKey, onOpen = { key ->
                    if (backStack.lastOrNull() == route) vm.diaryRoute(key)?.let(onOpenRoute)
                }, onOpenLog = { if (backStack.lastOrNull() == route) onNavigateTo(FitLogRoute.Log) },
                    onBack = { if (backStack.lastOrNull() == route && !vm.aliasSaving) onBack() })
            }

            entry<FitLogRoute.DiaryDetail> { route ->
                val vm = viewModel<DiaryDetailViewModel> {
                    DiaryDetailViewModel(route, documents, analysisRepository, createSavedStateHandle(),
                        parseDiary = { analysisController.runSingle(AnalysisSource(SourceKey(route.vaultId, route.relPath), route.document)) },
                        confirmDiary = { request -> confirmCurrentDiary(request, route.document, documents, analysisRepository) { snapshot ->
                            try { sourceIndex.recordRead(route.vaultId, route.relPath, snapshot) } catch (e: Exception) {
                                if (e is CancellationException) throw e
                                analysisController.reportIndexWarning()
                            }
                        } }, analysisState = analysisController.state, cancelAnalysis = analysisController::cancel,
                        deleteConfirmation = { records.deleteConfirmation(SourceKey(route.vaultId, route.relPath)) },
                        onSourceRead = { sourceIndex.recordRead(route.vaultId, route.relPath, it) })
                }
                DisposableEffect(vm) {
                    backHandlers[route] = { vm.requestLeave(onBack) }
                    onDispose { backHandlers.remove(route) }
                }
                DiaryDetailScreen(vm,
                    onBack = { if (backStack.lastOrNull() == route) vm.requestLeave(onBack) },
                    onAiSettings = { if (backStack.lastOrNull() == route) {
                        vm.requestLeave { onOpenRoute(FitLogRoute.AiSettings) }
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

            entry<FitLogRoute.NativeRecord> { route ->
                val vm = viewModel<NativeRecordViewModel>(key = route.sessionId) {
                    NativeRecordViewModel(route.recordId, records, createSavedStateHandle())
                }
                DisposableEffect(vm) {
                    backHandlers[route] = { vm.requestLeave { if (backStack.lastOrNull() == route) onBack() } }
                    onDispose { backHandlers.remove(route) }
                }
                NativeRecordScreen(vm) { if (backStack.lastOrNull() == route) onBack() }
            }

            entry<FitLogRoute.VaultSetup> { route ->
                VaultSetupRoute(
                    route = route,
                    vaultPreferences = vaultPreferences,
                    vaultRepository = vaultRepository,
                    onSetupCompleted = { showMarkdown = true; onSetupCompleted(it) },
                    onVaultConnected = { vault ->
                        // A completed connection explicitly requests a scan, including reconnecting the same folder.
                        val config = vaultPreferences.getVaultConfig()
                        if (backStack.lastOrNull() == route && config is VaultConfigState.Configured &&
                            config.uri.toString() == vault) {
                            sourceIndex.activate(config.vaultId)
                            sourceIndex.refresh(config.vaultId)
                        }
                    },
                    onBack = { if (backStack.lastOrNull() == route) onBack() },
                )
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
