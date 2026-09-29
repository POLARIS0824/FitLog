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
import com.example.fitlog.log.LogViewModel
import com.example.fitlog.today.TodayScreen
import com.example.fitlog.vault.VaultSetupRoute
import com.example.fitlog.vault.DiarySettingsScreen
import com.example.fitlog.vault.DiarySettingsViewModel

@Composable
fun FitLogNavGraph(
    backStack: NavBackStack<NavKey>,
    vaultPreferences: VaultPreferences,
    vaultRepository: VaultRepository,
    onSetupCompleted: (FitLogRoute.VaultSetup) -> Unit,
    onOpenRoute: (FitLogRoute) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motionScheme = MaterialTheme.motionScheme
    val context = LocalContext.current.applicationContext
    val documents = remember(context) { MarkdownDocumentRepository(context) }
    val drafts = remember(context) { EditorDraftStore(File(context.filesDir, "editor-drafts")) }
    var editorBack by remember { mutableStateOf<(() -> Unit)?>(null) }

    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        onBack = { editorBack?.invoke() ?: onBack() },
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
                TodayScreen()
            }

            entry<FitLogRoute.Log> {
                val vm = viewModel<LogViewModel> {
                    LogViewModel(createSavedStateHandle(), vaultPreferences.vaultConfig, documents, vaultPreferences.log)
                }
                LogScreen(vm,
                    onOpen = { vault, file -> onOpenRoute(FitLogRoute.Editor(vault, file.uri, directory = file.directory ?: vault, fileName = file.name)) },
                    onSettings = { vault -> onOpenRoute(FitLogRoute.DiarySettings(vault)) },
                    onConnect = { onOpenRoute(FitLogRoute.VaultSetup()) })
            }

            entry<FitLogRoute.Insight> {
                InsightScreen()
            }

            entry<FitLogRoute.Editor> { route ->
                val vm = viewModel<EditorViewModel>(key = route.sessionId) {
                    EditorViewModel(route, documents, drafts)
                }
                DisposableEffect(vm) {
                    editorBack = { vm.requestExit(onBack) }
                    onDispose { editorBack = null }
                }
                EditorScreen(vm = vm, onBack = onBack)
            }

            entry<FitLogRoute.VaultSetup> { route ->
                VaultSetupRoute(
                    route = route,
                    vaultPreferences = vaultPreferences,
                    vaultRepository = vaultRepository,
                    onSetupCompleted = onSetupCompleted,
                    onBack = onBack,
                )
            }

            entry<FitLogRoute.DiarySettings> { route ->
                val vm = viewModel<DiarySettingsViewModel> {
                    DiarySettingsViewModel(route.vault, vaultPreferences.diary, documents)
                }
                DisposableEffect(vm) {
                    editorBack = { vm.requestBack(onBack) }
                    onDispose { editorBack = null }
                }
                DiarySettingsScreen(vm) { if (backStack.lastOrNull() == route) onBack() }
            }
        },
    )
}
