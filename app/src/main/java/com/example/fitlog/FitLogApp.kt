package com.example.fitlog

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
import androidx.compose.runtime.collectAsState
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
import com.example.fitlog.data.vault.VaultPreferences
import com.example.fitlog.data.vault.VaultRepository
import com.example.fitlog.data.vault.MarkdownDocumentRepository
import com.example.fitlog.data.vault.TodayLogResolver
import com.example.fitlog.data.vault.vaultDataStore
import com.example.fitlog.data.index.SourceIndexRepository
import com.example.fitlog.data.analysis.DiaryAnalysisRepository
import com.example.fitlog.data.ai.AiProviderRepository
import com.example.fitlog.data.ai.aiDataStore
import com.example.fitlog.data.analysis.adapter.sharedAiHttpClient
import com.example.fitlog.log.LogPreferences
import com.example.fitlog.navigation.FitLogNavGraph
import com.example.fitlog.navigation.FitLogRoute
import com.example.fitlog.ui.components.FitLogNavigationToolbar
import com.example.fitlog.vault.VaultFlowController
import com.example.fitlog.settings.AppearanceViewModel
import kotlinx.coroutines.launch

/**
 * 负责整个 App 的 UI 框架
 *
 * 创建 VaultPreferences、VaultRepository、MarkdownDocumentRepository、SourceIndexRepository、DiaryAnalysisRepository 等全局对象，
 *
 * 维护 Navigation3 back stack、VaultFlowController、索引刷新生命周期，并放置 Scaffold、NavGraph 和底部浮动导航
 */
@SuppressLint("LocalContextGetResourceValueCall")
@Composable
internal fun FitLogApp(appearance: AppearanceViewModel) {

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(appearance.message) {
        appearance.message?.let { snackbarHostState.showSnackbar(context.getString(it)) }
    }

    val preferencesStore = remember { context.applicationContext.vaultDataStore }
    val vaultPreferences = remember { VaultPreferences(preferencesStore) }
    val logPreferences = remember { LogPreferences(preferencesStore) }
    val vaultRepository = remember { VaultRepository(context) }
    val documents = remember { MarkdownDocumentRepository(context) }
    val todayLog = remember { TodayLogResolver(vaultPreferences.diary, documents, documents) }
    val sourceIndex = remember { SourceIndexRepository.get(context) }
    val diaryAnalysis = remember { DiaryAnalysisRepository.get(context) }
    val aiProviders = remember { AiProviderRepository(context.applicationContext.aiDataStore) }
    val aiClient = remember { sharedAiHttpClient }

    // 当前导航历史
    val backStack = rememberNavBackStack(FitLogRoute.Today)

    val vaultFlow = remember(backStack, coroutineScope) {
        VaultFlowController(
            backStack = backStack,
            scope = coroutineScope,
            getConfig = vaultPreferences::getVaultConfig,
            checkAccess = vaultRepository::checkAccess,
            resolveToday = { config, date ->
                val resolved = todayLog.resolve(config.uri.toString(), date, config.vaultId)
                FitLogRoute.Editor(
                    vaultUri = resolved.vaultUri,
                    document = resolved.document,
                    date = resolved.date.toString(),
                    directory = resolved.directory,
                    fileName = resolved.fileName,
                    displayPath = resolved.displayPath,
                    vaultId = resolved.vaultId,
                )
            },
            showError = { message ->
                coroutineScope.launch { snackbarHostState.showSnackbar(context.getString(message)) }
            },
        )
    }
    val currentRoute = backStack.lastOrNull()
    val config by vaultPreferences.vaultConfig.collectAsState(initial = VaultConfigState.Loading)
    val currentVault = (config as? VaultConfigState.Configured)?.vaultId
    LaunchedEffect(config) {
        // Loading is not a disconnect; do not cancel an active scan during collection restart.
        if (config !is VaultConfigState.Loading) sourceIndex.activate(currentVault)
    }

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
                vaultPreferences = vaultPreferences,
                logSettings = logPreferences,
                vaultRepository = vaultRepository,
                sourceIndex = sourceIndex,
                analysisRepository = diaryAnalysis,
                aiRepository = aiProviders,
                aiClient = aiClient,
                appearance = appearance,
                onSetupCompleted = vaultFlow::onSetupCompleted,
                onOpenRoute = vaultFlow::openRoute,
                onNavigateTo = vaultFlow::navigateTo,
                onBack = vaultFlow::back,
                onOpenToday = vaultFlow::openTodayLog,
                onImportFolder = vaultFlow::importFolder,
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
                    onOpenTodayClick = vaultFlow::openTodayLog,
                    onImportFolderClick = vaultFlow::importFolder,
                )
            }
        }
    }
}
