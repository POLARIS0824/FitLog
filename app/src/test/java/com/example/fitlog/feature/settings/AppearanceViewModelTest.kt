@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.example.fitlog.feature.settings

import com.example.fitlog.data.settings.AppearancePreferences
import com.example.fitlog.data.settings.AppearanceSettingsStore
import com.example.fitlog.testing.MainDispatcherRule
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

class AppearanceViewModelTest {
    @get:Rule val main = MainDispatcherRule()

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
