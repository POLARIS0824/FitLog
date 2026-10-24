package com.example.fitlog.app.navigation

import androidx.compose.runtime.mutableIntStateOf
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.NavEntry
import com.example.fitlog.feature.settings.navigation.SettingsNavKey
import org.junit.Assert.assertNotEquals
import com.example.fitlog.feature.today.navigation.TodayNavKey
import com.example.fitlog.feature.insight.navigation.ExerciseHistoryNavKey
import com.example.fitlog.feature.vault.navigation.VaultSetupNavKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import com.example.fitlog.feature.insight.navigation.InsightNavKey
import com.example.fitlog.feature.log.navigation.LogNavKey
import org.junit.Assert.assertTrue
import org.junit.Test

class FitLogNavigatorTest {
    @Test fun aSharedDestinationHasSeparateStorageKeysInDifferentTabs() {
        val entry = NavEntry<NavKey>(SettingsNavKey) {}
        val today = scopedNavEntry(TodayNavKey, SettingsNavKey, entry)
        val log = scopedNavEntry(LogNavKey, SettingsNavKey, entry)
        assertEquals(today.metadata, log.metadata)
        assertNotEquals(today.contentKey, log.contentKey)
    }
    private fun state(selected: Int = 0) = FitLogNavigationState(mutableIntStateOf(selected), linkedMapOf(
        TodayNavKey to NavBackStack<NavKey>(TodayNavKey),
        LogNavKey to NavBackStack<NavKey>(LogNavKey),
        InsightNavKey to NavBackStack<NavKey>(InsightNavKey),
    ))

    @Test fun changingTabsRetainsThePreviousRootEntry() {
        val state = state(1)
        val backStack = state.backStack
        val navigator = FitLogNavigator(state)
        navigator.navigateTo(InsightNavKey)
        assertTrue("Switching tabs must retain Log's entry and its saved state", LogNavKey in backStack)
        navigator.navigateTo(LogNavKey)
        assertSame(backStack, state.backStack)
    }

    @Test fun eachTabRetainsItsNestedDestinationAndBackOnlyPopsTheActiveStack() {
        val state = state()
        val navigator = FitLogNavigator(state)
        val history = ExerciseHistoryNavKey("squat")
        navigator.navigateTo(InsightNavKey)
        navigator.openRoute(history)
        navigator.navigateTo(LogNavKey)
        navigator.navigateTo(InsightNavKey)
        assertEquals(history, state.backStack.last())
        navigator.back()
        assertEquals(listOf(InsightNavKey), state.backStack.toList())
        assertEquals(listOf(LogNavKey), state.backStacks.getValue(LogNavKey).toList())
        navigator.back()
        assertEquals(TodayNavKey, state.currentTab)
    }

    @Test fun staleSetupCompletionCannotChangeTheSelectedTab() {
        val state = state()
        val navigator = FitLogNavigator(state)
        val active = VaultSetupNavKey()
        navigator.openRoute(active)
        navigator.onSetupCompleted(VaultSetupNavKey())
        assertEquals(active, state.backStack.last())
        navigator.onSetupCompleted(active)
        assertEquals(LogNavKey, state.currentTab)
        assertEquals(listOf(LogNavKey), state.backStack.toList())
    }
}
