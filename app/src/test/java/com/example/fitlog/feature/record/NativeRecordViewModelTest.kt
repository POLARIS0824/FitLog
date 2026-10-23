@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.example.fitlog.feature.record

import androidx.lifecycle.SavedStateHandle
import com.example.fitlog.domain.record.SaveNativeRecord
import com.example.fitlog.testing.FakeRecords
import com.example.fitlog.testing.MainDispatcherRule
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

class NativeRecordViewModelTest {
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

    @Test fun undoSetRemovalPreservesLaterEditsAndUsesTheSequentialWriter() = runTest(main.dispatcher) {
        val records = FakeRecords()
        val vm = native(records)
        runCurrent()
        vm.addExercise()
        vm.name(0, "Squat")
        vm.set(0, 0) { it.copy(weight = 60.0, reps = 8) }
        vm.copySet(0, 0)
        runCurrent()
        val original = vm.uiState.value.input.exercises.single().sets
        assertEquals(2, original.size)
        assertTrue(original.last().userEdited)
        val gate = CompletableDeferred<Unit>()
        records.saveGate = gate
        vm.removeSet(0, 0)
        runCurrent()
        assertTrue(vm.uiState.value.canUndoRemoval)
        vm.notes("keep later edits")
        vm.undoRemoval()
        assertEquals(original, vm.uiState.value.input.exercises.single().sets)
        assertEquals("keep later edits", vm.uiState.value.input.notes)
        assertFalse(vm.uiState.value.canUndoRemoval)
        vm.requestLeave()
        runCurrent()
        assertFalse(vm.uiState.value.closeRequested)
        gate.complete(Unit)
        runCurrent()
        assertEquals(vm.uiState.value.input, vm.uiState.value.persisted)
        assertTrue(vm.uiState.value.closeRequested)
    }

    @Test fun undoExerciseRemovalRestoresItsPositionWithoutDiscardingOtherEdits() = runTest(main.dispatcher) {
        val records = FakeRecords()
        val vm = native(records)
        runCurrent()
        vm.addExercise()
        vm.name(0, "Squat")
        vm.addExercise()
        vm.name(1, "Press")
        runCurrent()
        vm.removeExercise(0)
        vm.name(0, "Bench press")
        vm.undoRemoval()
        assertEquals(listOf("Squat", "Bench press"), vm.uiState.value.input.exercises.map { it.rawName })
        assertFalse(vm.uiState.value.canUndoRemoval)
        runCurrent()
        assertEquals(vm.uiState.value.input, vm.uiState.value.persisted)
    }

}
