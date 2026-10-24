package com.example.fitlog.app.navigation

import androidx.navigation3.runtime.NavKey
import com.example.fitlog.feature.log.navigation.LogNavKey
import com.example.fitlog.feature.record.navigation.NativeRecordNavKey
import com.example.fitlog.feature.vault.navigation.VaultSetupNavKey

/** Source connection never gates native entry and never creates a document. */
internal class FitLogNavigator(private val state: FitLogNavigationState) {
    fun navigateTo(route: NavKey) {
        if (route in state.backStacks) state.currentTab = route else openRoute(route)
    }
    fun back() {
        if (state.backStack.size > 1) state.backStack.removeAt(state.backStack.lastIndex)
        else state.currentTab = state.startTab
    }
    fun openRoute(route: NavKey) {
        if (route in state.backStacks) navigateTo(route)
        else if (state.backStack.lastOrNull() != route) state.backStack.add(route)
    }
    fun importFolder() = openRoute(VaultSetupNavKey())
    fun openTodayLog() = openRoute(NativeRecordNavKey())
    fun onSetupCompleted(route: VaultSetupNavKey) {
        if (state.backStack.lastOrNull() != route) return
        back()
        state.currentTab = LogNavKey
        state.backStack.subList(1, state.backStack.size).clear()
    }
}
