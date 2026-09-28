package com.example.fitlog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import com.example.fitlog.navigation.FitLogNavGraph
import com.example.fitlog.navigation.FitLogRoute
import com.example.fitlog.navigation.TopLevelDestination
import com.example.fitlog.ui.components.FitLogNavigationToolbar

/**
 * 负责整个 App 的 UI 框架
 */
@Composable
fun FitLogApp() {

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
    ) { innerPadding ->
        Box(Modifier.fillMaxSize()) {
            FitLogNavGraph(
                backStack = backStack,
                modifier = Modifier.padding(innerPadding)
            )
            if (showNavigationToolbar && fabMenuExpanded) {
                // Consume outside taps without triggering the page underneath.
                Box(
                    Modifier.matchParentSize().clickable(
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
                        backStack.add(FitLogRoute.Editor)
                    },

                    onImportFolderClick = {
                        // TODO: 下一步实现 SAF 文件夹选择
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
