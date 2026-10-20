package com.example.fitlog.navigation

import androidx.compose.runtime.Composable
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState

/** Intercept only an overlay or protected operation; otherwise NavDisplay owns predictive back. */
@Composable
internal fun FitLogBackGuard(enabled: Boolean, onBackCompleted: () -> Unit) {
    val state = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
    NavigationBackHandler(state = state, isBackEnabled = enabled, onBackCompleted = onBackCompleted)
}
