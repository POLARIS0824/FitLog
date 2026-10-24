@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.example.fitlog.feature.log

import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.text.TextRange
import androidx.lifecycle.SavedStateHandle
import com.example.fitlog.data.index.SourceIndex
import com.example.fitlog.data.record.TrainingRecords
import com.example.fitlog.data.settings.LogSettingsStore
import com.example.fitlog.data.settings.LogSortOrder
import com.example.fitlog.data.vault.MarkdownSnapshot
import com.example.fitlog.data.vault.VaultConfigState
import com.example.fitlog.model.source.SourceIndexSnapshot
import com.example.fitlog.testing.FakeRecords
import com.example.fitlog.testing.MainDispatcherRule
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

class LogDisplaySubscriptionTest {
    @get:Rule val main = MainDispatcherRule()

    private fun model(records: TrainingRecords, saved: SavedStateHandle = SavedStateHandle(),
        settings: LogSettingsStore = object : LogSettingsStore {
            override suspend fun readSort() = LogSortOrder.Descending
            override suspend fun saveSort(order: LogSortOrder) = Unit
        }): LogViewModel {
        val index = object : SourceIndex {
            override fun activate(vaultId: String?) = Unit
            override fun observe(vaultId: String): Flow<SourceIndexSnapshot> = error("no vault")
            override fun refresh(vaultId: String): Job = error("no vault")
            override suspend fun recordRead(vaultId: String, path: String, snapshot: MarkdownSnapshot) = Unit
        }
        return main.own(LogViewModel(saved, flowOf(VaultConfigState.Loading, VaultConfigState.NotConfigured),
            index, settings, records = records, computationDispatcher = main.dispatcher))
    }

    @Test fun searchAndFiltersSurviveTabAbsenceAndSavedStateRecreation() = runTest(main.dispatcher) {
        val saved = SavedStateHandle()
        val records = FakeRecords()
        val vm = model(records, saved)
        val first = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect() }
        runCurrent()
        vm.searchFieldState.setTextAndPlaceCursorAtEnd("squat")
        vm.searchFieldState.edit { selection = TextRange(2) }
        val editor = vm.searchFieldState
        Snapshot.sendApplyNotifications()
        vm.filter(LogStatusFilter.CONFIRMED); vm.toggleMissing()
        runCurrent()
        first.cancel(); advanceTimeBy(5_000); runCurrent()
        assertEquals(0, records.readers)
        val second = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect() }
        runCurrent()
        assertEquals("squat", vm.uiState.value.query)
        assertSame(editor, vm.searchFieldState)
        assertEquals(TextRange(2), vm.searchFieldState.selection)
        assertEquals(LogStatusFilter.CONFIRMED, vm.uiState.value.statusFilter)
        assertTrue(vm.uiState.value.showMissing)
        val restored = model(FakeRecords(), SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) }))
        val restoredCollector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { restored.uiState.collect() }
        runCurrent()
        assertEquals("squat", restored.uiState.value.query)
        assertEquals("squat", restored.searchFieldState.text.toString())
        assertEquals(LogStatusFilter.CONFIRMED, restored.uiState.value.statusFilter)
        assertTrue(restored.uiState.value.showMissing)
        second.cancel(); restoredCollector.cancel()
    }

    @Test fun searchInputDoesNotWaitForResultComputation() = runTest(main.dispatcher) {
        val vm = model(FakeRecords())
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect() }
        runCurrent()

        // Exercise the editor bound to OutlinedTextField without letting the
        // asynchronous result pipeline run between keystrokes.
        for (text in listOf("s", "sq", "squ", "squa", "squat")) {
            vm.searchFieldState.edit { append(text.last()); selection = TextRange(length) }
            assertEquals(text, vm.searchFieldState.text.toString())
            assertEquals(TextRange(text.length), vm.searchFieldState.selection)
        }
        assertEquals("", vm.uiState.value.query)
        Snapshot.sendApplyNotifications()
        runCurrent()
        assertEquals("squat", vm.uiState.value.query)
        assertEquals("squat", vm.searchFieldState.text.toString())
        collector.cancel()
    }

    @Test fun resultsCannotMoveTheCursorOrRestoreClearedText() = runTest(main.dispatcher) {
        val saved = SavedStateHandle(mapOf("query" to "sqat"))
        val vm = model(FakeRecords(), saved)
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect() }
        runCurrent()
        vm.searchFieldState.edit {
            replace(2, 2, "u")
            selection = TextRange(3)
        }
        assertEquals("squat", vm.searchFieldState.text.toString())
        assertEquals(TextRange(3), vm.searchFieldState.selection)
        assertEquals("sqat", vm.uiState.value.query)
        vm.filter(LogStatusFilter.CONFIRMED)
        Snapshot.sendApplyNotifications()
        runCurrent()
        assertEquals("squat", vm.uiState.value.query)
        assertEquals(TextRange(3), vm.searchFieldState.selection)

        vm.searchFieldState.edit { replace(2, 3, ""); selection = TextRange(2) }
        assertEquals("sqat", vm.searchFieldState.text.toString())
        vm.searchFieldState.clearText()
        assertEquals("", vm.searchFieldState.text.toString())
        assertEquals(TextRange.Zero, vm.searchFieldState.selection)
        // The displayed results are still from the old search when clearing.
        assertEquals("squat", vm.uiState.value.query)
        Snapshot.sendApplyNotifications()
        runCurrent()
        assertEquals("", vm.uiState.value.query)
        assertEquals("", vm.searchFieldState.text.toString())
        assertEquals("", saved.get<String>("query"))

        vm.searchFieldState.edit { append("深蹲 60kg"); selection = TextRange(2) }
        Snapshot.sendApplyNotifications()
        runCurrent()
        assertEquals("深蹲 60kg", vm.uiState.value.query)
        assertEquals("深蹲 60kg", saved.get<String>("query"))
        assertEquals(TextRange(2), vm.searchFieldState.selection)
        collector.cancel()
    }

    @Test fun searchIsSavedWithoutKeepingDisplayQueriesSubscribed() = runTest(main.dispatcher) {
        val saved = SavedStateHandle(mapOf("query" to "深蹲"))
        val records = FakeRecords()
        val vm = model(records, saved)
        runCurrent()
        assertEquals("深蹲", vm.searchFieldState.text.toString())
        assertEquals(0, records.readers)

        vm.searchFieldState.edit { append(" 60kg"); selection = TextRange(2) }
        Snapshot.sendApplyNotifications()
        runCurrent()
        assertEquals("深蹲 60kg", saved.get<String>("query"))
        assertEquals(TextRange(2), vm.searchFieldState.selection)
        assertEquals(0, records.readers)

        val restored = model(FakeRecords(), SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) }))
        assertEquals("深蹲 60kg", restored.searchFieldState.text.toString())
    }

    @Test fun aReadFailureWaitsForExplicitRetryIncludingObserverCreationFailures() = runTest(main.dispatcher) {
        val stored = FakeRecords()
        var available = false
        var attempts = 0
        val records = object : TrainingRecords by stored {
            override fun observeRecords(): Flow<List<com.example.fitlog.model.record.TrainingRecordDetails>> {
                attempts++
                check(available) { "database unavailable" }
                return stored.observeRecords()
            }
        }
        val vm = model(records)
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect() }
        runCurrent()
        assertTrue(vm.uiState.value.recordsReadFailed)
        available = true
        advanceTimeBy(60_000); runCurrent()
        assertEquals(1, attempts)
        vm.retryRecords(); runCurrent()
        assertEquals(2, attempts)
        assertFalse(vm.uiState.value.recordsReadFailed)
        collector.cancel()
    }

    @Test fun aPendingSettingsWriteContinuesAfterDisplayCollectionStops() = runTest(main.dispatcher) {
        val records = FakeRecords()
        val gate = CompletableDeferred<Unit>()
        var persisted: LogSortOrder? = null
        val settings = object : LogSettingsStore {
            override suspend fun readSort() = LogSortOrder.Descending
            override suspend fun saveSort(order: LogSortOrder) { gate.await(); persisted = order }
        }
        val vm = model(records, settings = settings)
        val first = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect() }
        runCurrent()
        vm.changeSort(LogSortOrder.Ascending); runCurrent()
        first.cancel(); advanceTimeBy(5_000); runCurrent()
        assertEquals(0, records.readers)
        gate.complete(Unit); runCurrent()
        assertEquals(LogSortOrder.Ascending, persisted)
        val second = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect() }
        runCurrent()
        assertEquals(LogSortOrder.Ascending, vm.uiState.value.sort)
        assertFalse(vm.uiState.value.sortBusy)
        second.cancel()
    }

    @Test fun logStopsDisplayQueriesWithoutCancellingIndependentWrites() = runTest(main.dispatcher) {
        val records = FakeRecords()
        val index = object : SourceIndex {
            override fun activate(vaultId: String?) = Unit
            override fun observe(vaultId: String): Flow<SourceIndexSnapshot> = error("no vault")
            override fun refresh(vaultId: String): Job = error("no vault")
            override suspend fun recordRead(vaultId: String, path: String, snapshot: com.example.fitlog.data.vault.MarkdownSnapshot) = Unit
        }
        val settings = object : LogSettingsStore {
            override suspend fun readSort() = LogSortOrder.Descending
            override suspend fun saveSort(order: LogSortOrder) = Unit
        }
        val vm = main.own(LogViewModel(SavedStateHandle(), flowOf(VaultConfigState.NotConfigured),
            index, settings, records = records, computationDispatcher = main.dispatcher))
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect() }
        runCurrent()
        assertEquals(1, records.readers)
        vm.searchFieldState.setTextAndPlaceCursorAtEnd("squat")
        Snapshot.sendApplyNotifications()
        runCurrent()
        assertEquals("squat", vm.uiState.value.query)
        collector.cancel()
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(0, records.readers)
        assertEquals("squat", vm.uiState.value.query)
    }
}
