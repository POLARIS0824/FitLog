@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.example.fitlog.feature.settings

import com.example.fitlog.data.settings.AppearancePreferences
import com.example.fitlog.data.settings.AppearanceSettingsStore
import com.example.fitlog.data.settings.ThemeMode
import com.example.fitlog.data.settings.UiLook
import com.example.fitlog.testing.MainDispatcherRule
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

class AppearanceViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    @Test fun lookSwitchWaitsForPersistenceAndPreservesClassicAppearanceOnReopen() = runTest(main.dispatcher) {
        val initial = AppearancePreferences(themeMode = ThemeMode.LIGHT, dynamicColor = false)
        val persisted = MutableStateFlow(initial)
        val saved = CompletableDeferred<Unit>()
        val store = object : AppearanceSettingsStore {
            override val settings = persisted
            override suspend fun save(settings: AppearancePreferences) {
                saved.await()
                persisted.value = settings
            }
        }
        val vm = main.own(AppearanceViewModel(store))
        runCurrent()
        vm.changeLook(UiLook.LIQUID_GLASS)
        runCurrent()
        assertTrue(vm.uiState.value.saving)
        assertEquals(initial, vm.uiState.value.preferences)
        saved.complete(Unit)
        runCurrent()
        val expected = initial.copy(look = UiLook.LIQUID_GLASS)
        assertEquals(expected, vm.uiState.value.preferences)
        val reopened = main.own(AppearanceViewModel(store))
        runCurrent()
        assertEquals(expected, reopened.uiState.value.preferences)
        reopened.changeLook(UiLook.CLASSIC)
        runCurrent()
        assertEquals(initial, reopened.uiState.value.preferences)
    }

    @Test fun failedLookSwitchKeepsCurrentInterface() = runTest(main.dispatcher) {
        val initial = AppearancePreferences(themeMode = ThemeMode.DARK, liquidGlass = false)
        val store = object : AppearanceSettingsStore {
            override val settings = flowOf(initial)
            override suspend fun save(settings: AppearancePreferences) { error("disk full") }
        }
        val vm = main.own(AppearanceViewModel(store))
        runCurrent()
        vm.changeLook(UiLook.LIQUID_GLASS)
        runCurrent()
        assertEquals(initial, vm.uiState.value.preferences)
        assertNotNull(vm.uiState.value.message)
        assertFalse(vm.uiState.value.saving)
    }

    @Test fun repeatedSnackbarFailuresGetDistinctIdsAndOldAcknowledgementCannotClearNewMessage() = runTest(main.dispatcher) {
        val store = object : AppearanceSettingsStore {
            override val settings = flowOf(AppearancePreferences())
            override suspend fun save(settings: AppearancePreferences) { error("disk full") }
        }
        val vm = main.own(AppearanceViewModel(store))
        runCurrent()
        vm.changeDynamicColor(false)
        runCurrent()
        val first = vm.uiState.value.messageId
        vm.messageShown(first)
        assertNull(vm.uiState.value.message)
        vm.changeDynamicColor(false)
        runCurrent()
        assertTrue(vm.uiState.value.messageId > first)
        vm.messageShown(first)
        assertNotNull(vm.uiState.value.message)
        vm.messageShown(vm.uiState.value.messageId)
        assertNull(vm.uiState.value.message)
    }
}
