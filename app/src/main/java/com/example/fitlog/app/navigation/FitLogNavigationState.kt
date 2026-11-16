package com.example.fitlog.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack

/** Each root keeps its own entries even while another tab is visible. */
internal class FitLogNavigationState(
    private val selectedTab: MutableIntState,
    val backStacks: Map<NavKey, NavBackStack<NavKey>>,
) {
    private val roots = backStacks.keys.toList()
    var currentTab: NavKey
        get() = roots[selectedTab.intValue]
        set(value) { selectedTab.intValue = roots.indexOf(value).also { require(it >= 0) } }
    val backStack: NavBackStack<NavKey> get() = backStacks.getValue(currentTab)
    val startTab: NavKey get() = roots.first()
}

@Composable
internal fun rememberFitLogNavigationState(): FitLogNavigationState {
    val selected = rememberSaveable { mutableIntStateOf(0) }
    val stacks = topLevelDestinations.associate { it.route to rememberNavBackStack(it.route) }
    return remember(selected) { FitLogNavigationState(selected, stacks) }
}
