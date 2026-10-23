package com.example.fitlog.app.navigation

import androidx.navigation3.runtime.NavKey

/** Source connection never gates native entry and never creates a document. */
internal class FitLogNavigator(private val backStack: MutableList<NavKey>) {
    fun navigateTo(route: FitLogRoute) {
        if (backStack.isEmpty()) backStack.add(route) else backStack[backStack.lastIndex] = route
    }
    fun back() { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) }
    fun openRoute(route: FitLogRoute) { if (backStack.lastOrNull() != route) backStack.add(route) }
    fun importFolder() = openRoute(FitLogRoute.VaultSetup())
    fun openTodayLog() = openRoute(FitLogRoute.NativeRecord())
    fun onSetupCompleted(route: FitLogRoute.VaultSetup) {
        if (backStack.lastOrNull() != route) return
        backStack.clear(); backStack.add(FitLogRoute.Log)
    }
}
