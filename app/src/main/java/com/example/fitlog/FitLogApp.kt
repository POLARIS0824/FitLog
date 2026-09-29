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
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import com.example.fitlog.data.vault.VaultAccessStatus
import com.example.fitlog.data.vault.VaultConfigState
import com.example.fitlog.data.vault.VaultPreferences
import com.example.fitlog.data.vault.VaultRepository
import com.example.fitlog.navigation.FitLogNavGraph
import com.example.fitlog.navigation.FitLogRoute
import com.example.fitlog.navigation.TopLevelDestination
import com.example.fitlog.ui.components.FitLogNavigationToolbar
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
                onVaultSelected = { uri ->
                    coroutineScope.launch {
                        // 1. 保存到 DataStore
                        vaultPreferences.setVaultUri(uri)
                        // 2. 关闭 VaultSetup 页面返回
                        backStack.removeLastOrNull()
                    }
                },
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
                            onClickLabel = "Close menu",
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
                    onDestinationClick = { destination ->
                        navigateToTopLevelDestination(
                            backStack = backStack,
                            destination = destination
                        )
                    },
                    onCreateFileClick = {
                        coroutineScope.launch {
                            when (val config = vaultPreferences.getVaultConfig()) {
                                is VaultConfigState.Configured -> {
                                    // 检查目录真实可用性
                                    when (val access = vaultRepository.checkAccess(config.uri)) {
                                        VaultAccessStatus.CanCreateFiles -> {
                                            // 状态正常，直接进编辑页
                                            backStack.add(FitLogRoute.Editor)
                                        }
                                        VaultAccessStatus.NeedsReauthorization -> {
                                            snackbarHostState.showSnackbar("授权失效，请重新授权文件夹")
                                            backStack.add(FitLogRoute.VaultSetup)
                                        }
                                        VaultAccessStatus.DirectoryUnavailable -> {
                                            snackbarHostState.showSnackbar("文件夹不可用或已被删除，请重新选择")
                                            backStack.add(FitLogRoute.VaultSetup)
                                        }
                                        VaultAccessStatus.ReadOnly -> {
                                            snackbarHostState.showSnackbar("该文件夹为只读，无法新建笔记")
                                        }
                                        is VaultAccessStatus.Failed -> {
                                            snackbarHostState.showSnackbar("检查失败: ${access.cause.localizedMessage}")
                                        }
                                    }
                                }
                                VaultConfigState.NotConfigured -> {
                                    // 尚未配置，直接引导至选择页面
                                    backStack.add(FitLogRoute.VaultSetup)
                                }
                                VaultConfigState.Loading -> {
                                    // 读取配置中，忽略连续误触
                                }
                                is VaultConfigState.Failed -> {
                                    snackbarHostState.showSnackbar("读取配置失败，请重新选择文件夹")
                                    backStack.add(FitLogRoute.VaultSetup)
                                }
                            }
                        }
                    },
                    // 导入/更换文件夹点击逻辑
                    onImportFolderClick = {
                        backStack.add(FitLogRoute.VaultSetup)
                    },
                )
            }
        }
    }
}

/**
 * [Today]
 *   ↓ 直接 replace
 * [Log]
 */
private fun navigateToTopLevelDestination(
    backStack: NavBackStack<NavKey>,
    destination: TopLevelDestination,
) {
    if (backStack.lastOrNull() == destination.route) {
        return
    }

    if (backStack.isEmpty()) {
        backStack.add(destination.route)
    } else {
        backStack[backStack.lastIndex] = destination.route
    }
}
