package com.example.fitlog.log

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.example.fitlog.data.vault.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
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
class LogViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private fun file(uri: String, name: String, path: String = name) = MarkdownFile(uri, name, path, true)
    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { store.clear(); Dispatchers.resetMain() }

    @Test fun filenameAndPathSearchUseStableOrdering() {
        val files = listOf(file("3", "a.md", "z/a.md"), file("1", "B.md"), file("2", "A.md", "a/A.md"))
        val sorted = filterAndSortFiles(files, "", LogSortOrder.Ascending)
        assertEquals(listOf("2", "3", "1"), sorted.map { it.uri })
        assertEquals(sorted.reversed(), filterAndSortFiles(files.reversed(), "", LogSortOrder.Descending))
        assertEquals(listOf("3"), filterAndSortFiles(files, " Z/ ", LogSortOrder.Ascending).map { it.uri })
        assertEquals(2, filterAndSortFiles(files, "a.MD", LogSortOrder.Descending).size)
        assertTrue(filterAndSortFiles(files, "missing", LogSortOrder.Descending).isEmpty())
    }

    @Test fun refreshPreservesQueryAndSortAndLoadsSavedFile() = runTest(dispatcher) {
        val config = MutableStateFlow<VaultConfigState>(VaultConfigState.Configured(Uri.parse("vault")))
        var files = listOf(file("1", "2026-09-29.md"))
        val settings = TestLogSettings()
        val saved = SavedStateHandle()
        val vm = LogViewModel(saved, config, ScanDocuments { MarkdownScan(files, false) }, settings)
        store.put("log", vm)
        advanceUntilIdle()
        assertEquals(LogSortOrder.Descending, vm.sort)
        vm.search("2026")
        vm.changeSort(LogSortOrder.Ascending)
        advanceUntilIdle()
        files = files + file("2", "2026-09-30.md")
        vm.refresh()
        advanceUntilIdle()
        assertEquals(2, vm.visibleFiles.size)
        assertEquals("2026", saved.get<String>("query"))
        assertEquals(LogSortOrder.Ascending, settings.order)
        assertFalse(vm.loading)
    }

    @Test fun switchingVaultDiscardsLateScanEvenIfProviderIgnoresCancellation() = runTest(dispatcher) {
        val config = MutableStateFlow<VaultConfigState>(VaultConfigState.Configured(Uri.parse("old")))
        val old = CompletableDeferred<MarkdownScan>()
        val vm = LogViewModel(SavedStateHandle(), config, ScanDocuments { vault ->
            if (vault == "old") withContext(NonCancellable) { old.await() }
            else MarkdownScan(listOf(file("new", "new.md")), true)
        }, TestLogSettings())
        store.put("log", vm)
        runCurrent()
        config.value = VaultConfigState.Configured(Uri.parse("new"))
        runCurrent()
        old.complete(MarkdownScan(listOf(file("old", "old.md")), false))
        advanceUntilIdle()
        assertEquals("new", vm.vault)
        assertEquals(listOf("new"), vm.files.map { it.uri })
        assertTrue(vm.partial)
        assertFalse(vm.loading)
    }

    @Test fun scanAndSortFailuresCanBeRetriedWithoutLosingQuery() = runTest(dispatcher) {
        val config = MutableStateFlow<VaultConfigState>(VaultConfigState.Configured(Uri.parse("vault")))
        var fail = true
        val settings = TestLogSettings().apply { failSave = true }
        val vm = LogViewModel(SavedStateHandle(), config, ScanDocuments {
            if (fail) throw IOException()
            MarkdownScan(emptyList(), false)
        }, settings)
        store.put("log", vm)
        advanceUntilIdle()
        assertNotNull(vm.error)
        vm.search("saved query")
        vm.changeSort(LogSortOrder.Ascending)
        advanceUntilIdle()
        assertNotNull(vm.sortError)
        assertEquals(LogSortOrder.Descending, vm.sort)
        settings.failSave = false
        fail = false
        vm.changeSort(LogSortOrder.Ascending)
        vm.refresh()
        advanceUntilIdle()
        assertNull(vm.error)
        assertNull(vm.sortError)
        assertEquals("saved query", vm.query)
    }
}

private class TestLogSettings : LogSettingsStore {
    var order = LogSortOrder.Descending
    var failSave = false
    override suspend fun readSort() = order
    override suspend fun saveSort(order: LogSortOrder) { if (failSave) throw IOException(); this.order = order }
}

private class ScanDocuments(private val scan: suspend (String) -> MarkdownScan) : MarkdownDocuments {
    override suspend fun scan(vault: String) = scan.invoke(vault)
    override suspend fun find(vault: String, name: String): MarkdownFile? = error("unused")
    override suspend fun read(uri: String): MarkdownSnapshot = error("unused")
    override suspend fun create(vault: String, name: String): MarkdownFile = error("unused")
    override suspend fun write(uri: String, text: String, bom: Boolean, expected: String): MarkdownSnapshot = error("unused")
}
