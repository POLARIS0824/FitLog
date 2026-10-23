@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.example.fitlog

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import com.example.fitlog.data.analysis.*
import com.example.fitlog.domain.SaveNativeRecord
import com.example.fitlog.records.*
import com.example.fitlog.settings.AppearanceViewModel
import com.example.fitlog.data.settings.*
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TestWatcher
import org.junit.runner.Description

class MainDispatcherRule : TestWatcher() {
    val dispatcher = StandardTestDispatcher()
    private val models = ViewModelStore()
    fun <T : ViewModel> own(vm: T): T = vm.also { models.put(UUID.randomUUID().toString(), it) }
    override fun starting(description: Description) { Dispatchers.setMain(dispatcher) }
    override fun finished(description: Description) { models.clear(); Dispatchers.resetMain() }
}

internal class FakeRecords : TrainingRecords {
    val rows = MutableStateFlow<List<TrainingRecordDetails>>(emptyList())
    var readsFail = false
    var writesFail = false
    var saveGate: CompletableDeferred<Unit>? = null
    val savedNotes = mutableListOf<String?>()
    var readers = 0
    override fun observeRecords() = flow {
        readers++
        try { emitAll(rows) } finally { readers-- }
    }
    override suspend fun read(id: String): TrainingRecordDetails? {
        if (readsFail) error("storage unavailable")
        return rows.value.firstOrNull { it.diary.id == id }
    }
    override suspend fun saveNative(id: String, date: LocalDate, sessions: List<ReviewedSession>, kind: RecordKind) {
        if (writesFail) error("storage unavailable")
        saveGate?.await()
        savedNotes += sessions.single().notes
        val row = TrainingRecordDetails(TrainingRecord(id, null, null, date.toString(), null, null, null,
            1, false, RecordOrigin.NATIVE, kind), sessions.mapIndexed { index, session ->
            ConfirmedSessionRecord(ConfirmedSessionRow("session-$index", id, index, session.notes, null), emptyList())
        })
        rows.value = rows.value.filterNot { it.diary.id == id } + row
    }
    override suspend fun delete(id: String) { rows.value = rows.value.filterNot { it.diary.id == id } }
    override suspend fun deleteConfirmation(key: SourceKey) = Unit
}

class ArchitectureRegressionTest {
    @get:Rule val main = MainDispatcherRule()
    private fun native(records: FakeRecords, id: String? = null, saved: SavedStateHandle = SavedStateHandle()) =
        main.own(NativeRecordViewModel(id, records, SaveNativeRecord(records, main.dispatcher), saved))

    @Test fun readFailureCanBeRetriedWithoutLeavingTheEditor() = runTest(main.dispatcher) {
        val records = FakeRecords().apply { readsFail = true }
        val vm = native(records)
        runCurrent()
        assertTrue(vm.uiState.value.readFailed)
        assertFalse(vm.uiState.value.canEdit)
        records.readsFail = false
        vm.retry()
        runCurrent()
        assertFalse(vm.uiState.value.readFailed)
        assertTrue(vm.uiState.value.canEdit)
        vm.notes("recovered")
        runCurrent()
        assertEquals(RecordSaveState.SAVED, vm.uiState.value.state)
    }

    @Test fun blankEditorDoesNotCreateARecord() = runTest(main.dispatcher) {
        val records = FakeRecords()
        val vm = native(records)
        runCurrent()
        vm.requestLeave()
        assertTrue(vm.uiState.value.closeRequested)
        assertTrue(records.rows.value.isEmpty())
    }

    @Test fun editsDuringAnActiveSaveAreWrittenInOrderBeforeClosing() = runTest(main.dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val records = FakeRecords().apply { saveGate = gate }
        val vm = native(records)
        runCurrent()
        vm.notes("first")
        runCurrent()
        vm.notes("latest")
        vm.requestLeave()
        runCurrent()
        assertFalse(vm.uiState.value.closeRequested)
        gate.complete(Unit)
        runCurrent()
        assertEquals(listOf("first", "latest"), records.savedNotes)
        assertTrue(vm.uiState.value.closeRequested)
        assertFalse(vm.uiState.value.hasChanges)
    }

    @Test fun failedSaveRetainsInputAndRequiresManualRetry() = runTest(main.dispatcher) {
        val records = FakeRecords().apply { writesFail = true }
        val vm = native(records)
        runCurrent()
        vm.notes("keep me")
        runCurrent()
        vm.requestLeave()
        assertTrue(vm.uiState.value.leaveRequested)
        assertEquals("keep me", vm.uiState.value.input.notes)
        records.writesFail = false
        advanceTimeBy(60_000)
        runCurrent()
        assertTrue(records.rows.value.isEmpty())
        vm.retryAndLeave()
        runCurrent()
        assertEquals(listOf("keep me"), records.savedNotes)
        assertTrue(vm.uiState.value.closeRequested)
    }

    @Test fun deletePublishesAConsumableCompletionAndDisablesFurtherEdits() = runTest(main.dispatcher) {
        val records = FakeRecords()
        val vm = native(records)
        runCurrent()
        vm.notes("saved")
        runCurrent()
        vm.delete()
        runCurrent()
        assertTrue(records.rows.value.isEmpty())
        assertTrue(vm.uiState.value.deleted)
        assertTrue(vm.uiState.value.closeRequested)
        vm.closeHandled()
        vm.notes("must not resurrect")
        runCurrent()
        assertFalse(vm.uiState.value.closeRequested)
        assertTrue(records.rows.value.isEmpty())
    }

    @Test fun processRestorationReloadsDatabaseAndKeepsBundleSmall() = runTest(main.dispatcher) {
        val records = FakeRecords()
        val saved = SavedStateHandle()
        val vm = native(records, saved = saved)
        runCurrent()
        vm.notes("persisted")
        runCurrent()
        val restored = native(records, saved = SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) }))
        runCurrent()
        assertEquals(vm.id, restored.id)
        assertEquals("persisted", restored.uiState.value.input.notes)
        assertEquals(setOf("recordId", "unsavedInput"), saved.keys())
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
