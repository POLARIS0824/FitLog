package com.example.fitlog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.fitlog.insight.InsightScreen
import com.example.fitlog.log.LogScreen
import com.example.fitlog.navigation.FitLogNavGraph
import com.example.fitlog.navigation.FitLogRoute
import com.example.fitlog.navigation.TopLevelDestination
import com.example.fitlog.navigation.topLevelDestinations
import com.example.fitlog.today.TodayScreen
import com.example.fitlog.ui.components.FitLogNavigationToolbar

/**
 * 负责整个 App 的 UI 框架
 */
@Composable
fun FitLogApp() {

    // 当前导航历史
    val backStack = rememberNavBackStack(FitLogRoute.Today)
    val currentRoute = backStack.lastOrNull()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        floatingActionButtonPosition = FabPosition.Center,
        floatingActionButton = {
            FitLogNavigationToolbar(
                currentRoute = currentRoute,
                onDestinationClick = { destination ->
                    navigateToTopLevelDestination(
                        backStack = backStack,
                        destination = destination
                    )
                },
                onRecordWorkoutClick = {
                    backStack.add(FitLogRoute.Editor)
                },
            )
        }
    ) { innerPadding ->
        FitLogNavGraph(
            backStack = backStack,
            modifier = Modifier.padding(innerPadding)
        )
    }
}

private fun navigateToTopLevelDestination(
    backStack: NavBackStack<NavKey>,
    destination: TopLevelDestination,
) {
    backStack.clear()
    backStack.add(destination.route)
}