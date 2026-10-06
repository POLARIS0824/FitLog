package com.example.fitlog.vault

import androidx.lifecycle.ViewModelStore
import com.example.fitlog.data.vault.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DiarySettingsViewModelTest {
    @Test fun uuidOwnsSettingsWhileDirectoryBrowsingUsesTheOriginalUri() = runTest(dispatcher) {
        val uri = "content://test/tree/a"
        val id = "00000000-0000-4000-8000-000000000001"
        var saved: DiarySettings? = null
        val settings = object : DiarySettingsStore {
            override suspend fun read(vault: String): DiarySettings { assertEquals(id, vault); return DiarySettings() }
            override suspend fun save(vault: String, settings: DiarySettings) { assertEquals(id, vault); saved = settings }
        }
        val dirs = object : DiaryDirectories {
            override suspend fun resolveDirectory(vault: String, path: List<String>): String {
                assertEquals(uri, vault); return uri
            }
            override suspend fun directories(directory: String) = emptyList<DiaryDirectory>()
            override suspend fun canCreate(directory: String) = true
        }
        val vm = DiarySettingsViewModel(uri, id, settings, dirs)
        store.put("settings", vm)
        advanceUntilIdle()
        vm.chooseFormat(DiaryDateFormat.Compact)
        vm.save(); advanceUntilIdle()
        assertEquals(DiarySettings(dateFormat = DiaryDateFormat.Compact), saved)
        assertTrue(vm.completed)
    }
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { store.clear(); Dispatchers.resetMain() }

    @Test fun browsingAndCancellingNeverSaveButExplicitSavePersistsSelection() = runTest(dispatcher) {
        val settings = SettingsMemory()
        val vm = DiarySettingsViewModel("vault", "00000000-0000-4000-8000-000000000001", settings, DirectoryMemory())
        store.put("settings", vm)
        advanceUntilIdle()
        assertTrue(vm.canSave)
        vm.browse(listOf("daily"))
        vm.chooseFormat(DiaryDateFormat.Chinese)
        advanceUntilIdle()
        var left = false
        vm.requestBack { left = true }
        assertTrue(left)
        assertEquals(0, settings.saves)
        vm.save()
        advanceUntilIdle()
        assertEquals(DiarySettings(listOf("daily"), DiaryDateFormat.Chinese), settings.value)
        assertTrue(vm.completed)
    }

    @Test fun failedSavePreservesSelectionAndAllowsRetry() = runTest(dispatcher) {
        val settings = SettingsMemory().apply { failSave = true }
        val vm = DiarySettingsViewModel("vault", "00000000-0000-4000-8000-000000000001", settings, DirectoryMemory())
        store.put("settings", vm)
        advanceUntilIdle()
        vm.browse(listOf("daily"))
        advanceUntilIdle()
        vm.save()
        advanceUntilIdle()
        assertNotNull(vm.error)
        assertFalse(vm.completed)
        assertEquals(listOf("daily"), vm.path)
        assertTrue(vm.canSave)
        settings.failSave = false
        vm.save()
        advanceUntilIdle()
        assertTrue(vm.completed)
    }

    @Test fun lateDirectoryResultsDoNotReplaceNewSelection() = runTest(dispatcher) {
        val old = CompletableDeferred<String>()
        val dirs = DirectoryMemory().apply {
            resolve = { path -> if (path == listOf("slow")) withContext(NonCancellable) { old.await() } else path.joinToString("/") }
        }
        val vm = DiarySettingsViewModel("vault", "00000000-0000-4000-8000-000000000001", SettingsMemory(), dirs)
        store.put("settings", vm)
        advanceUntilIdle()
        vm.browse(listOf("slow"))
        runCurrent()
        vm.browse(listOf("new"))
        runCurrent()
        old.complete("old")
        advanceUntilIdle()
        assertEquals(listOf("new"), vm.path)
        assertEquals("new/child", vm.children.single().uri)
        assertTrue(vm.canSave)
    }

    @Test fun missingSavedDirectoryRequiresExplicitNewSelection() = runTest(dispatcher) {
        val settings = SettingsMemory().apply { value = DiarySettings(listOf("missing")) }
        val dirs = DirectoryMemory().apply { resolve = { if (it.isNotEmpty()) throw IOException(); "root" } }
        val vm = DiarySettingsViewModel("vault", "00000000-0000-4000-8000-000000000001", settings, dirs)
        store.put("settings", vm)
        advanceUntilIdle()
        assertFalse(vm.canSave)
        assertNotNull(vm.error)
        assertEquals(0, settings.saves)
        vm.browse(emptyList())
        advanceUntilIdle()
        assertTrue(vm.canSave)
        assertEquals(listOf("missing"), settings.value.directoryPath)
    }
}

private class SettingsMemory : DiarySettingsStore {
    var value = DiarySettings()
    var saves = 0
    var failSave = false
    override suspend fun read(vault: String) = value
    override suspend fun save(vault: String, settings: DiarySettings) {
        if (failSave) throw IOException()
        saves++
        value = settings
    }
}

private class DirectoryMemory : DiaryDirectories {
    var resolve: suspend (List<String>) -> String = { it.joinToString("/") }
    override suspend fun resolveDirectory(vault: String, path: List<String>) = resolve(path)
    override suspend fun directories(directory: String) = listOf(DiaryDirectory("$directory/child", "child"))
    override suspend fun canCreate(directory: String) = true
}
