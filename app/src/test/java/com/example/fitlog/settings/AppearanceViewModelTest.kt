package com.example.fitlog.settings

import androidx.lifecycle.ViewModelStore
import com.example.fitlog.R
import com.example.fitlog.data.settings.*
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppearanceViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val models = ViewModelStore()
    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { models.clear(); Dispatchers.resetMain() }

    @Test fun writeFailureKeepsAppliedThemeAndOnlyExplicitSelectionRetries() = runTest(dispatcher) {
        val saved = MutableStateFlow(AppearancePreferences())
        var attempts = 0
        val gate = CompletableDeferred<Unit>()
        val vm = viewModel(object : AppearanceSettingsStore {
            override val settings = saved
            override suspend fun save(settings: AppearancePreferences) {
                attempts++
                if (attempts == 1) { gate.await(); throw IOException("Write failed") }
                saved.value = settings
            }
        })
        runCurrent()
        vm.changeThemeMode(ThemeMode.DARK)
        vm.changeDynamicColor(false)
        runCurrent()
        assertEquals(1, attempts)
        assertTrue(vm.saving)
        assertEquals(ThemeMode.SYSTEM, vm.preferences.themeMode)
        gate.complete(Unit)
        runCurrent()
        assertEquals(R.string.appearance_save_failed, vm.message)
        assertEquals(AppearancePreferences(), vm.preferences)
        advanceUntilIdle()
        assertEquals(1, attempts)
        vm.changeThemeMode(ThemeMode.DARK)
        runCurrent()
        assertEquals(ThemeMode.DARK, vm.preferences.themeMode)
        vm.changeDynamicColor(false)
        runCurrent()
        assertEquals(AppearancePreferences(ThemeMode.DARK, false), vm.preferences)
        assertEquals(3, attempts)
    }

    @Test fun failedReadRequiresManualReloadAndNeverWritesDefaults() = runTest(dispatcher) {
        var fail = true
        var reads = 0
        var writes = 0
        val vm = viewModel(object : AppearanceSettingsStore {
            override val settings = flow {
                reads++
                if (fail) throw IOException("Read failed")
                emit(AppearancePreferences(ThemeMode.LIGHT, false))
                awaitCancellation()
            }
            override suspend fun save(settings: AppearancePreferences) { writes++ }
        })
        runCurrent()
        assertFalse(vm.canEdit)
        assertEquals(R.string.appearance_read_failed, vm.message)
        vm.changeThemeMode(ThemeMode.DARK)
        advanceUntilIdle()
        assertEquals(1, reads)
        assertEquals(0, writes)
        fail = false
        vm.reload()
        runCurrent()
        assertTrue(vm.canEdit)
        assertEquals(AppearancePreferences(ThemeMode.LIGHT, false), vm.preferences)
        assertNull(vm.message)
    }

    @Test fun externalPersistedChangesAreAppliedWithoutSavingAgain() = runTest(dispatcher) {
        val saved = MutableStateFlow(AppearancePreferences())
        val vm = viewModel(object : AppearanceSettingsStore {
            override val settings = saved
            override suspend fun save(settings: AppearancePreferences) { error("No write expected") }
        })
        runCurrent()
        saved.value = AppearancePreferences(ThemeMode.DARK, false)
        runCurrent()
        assertEquals(saved.value, vm.preferences)
    }

    private fun viewModel(store: AppearanceSettingsStore) = AppearanceViewModel(store).also { models.put("appearance", it) }
}
