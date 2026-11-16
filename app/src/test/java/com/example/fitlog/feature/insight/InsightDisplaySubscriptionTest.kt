@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.example.fitlog.feature.insight

import androidx.lifecycle.SavedStateHandle
import com.example.fitlog.data.settings.ExerciseAliasStore
import com.example.fitlog.data.settings.InsightPreferences
import com.example.fitlog.domain.insight.InsightRange
import com.example.fitlog.data.vault.VaultConfigState
import com.example.fitlog.model.record.ExerciseAliasGroup
import com.example.fitlog.model.record.ExerciseAliases
import com.example.fitlog.testing.FakeRecords
import com.example.fitlog.testing.MainDispatcherRule
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

class InsightDisplaySubscriptionTest {
    @get:Rule val main = MainDispatcherRule()
    private val aliases = object : ExerciseAliasStore {
        override fun observe() = flowOf(ExerciseAliases())
        override suspend fun save(previousPrimaryKey: String?, replacement: ExerciseAliasGroup?) = Unit
    }

    @Test fun theSelectedRangeSurvivesTabAbsenceAndSavedStateRecreation() = runTest(main.dispatcher) {
        val saved = SavedStateHandle()
        val records = FakeRecords()
        fun model(handle: SavedStateHandle) = main.own(InsightViewModel(
            flowOf(VaultConfigState.Loading, VaultConfigState.NotConfigured), { error("no vault") },
            records, aliases, handle, {}, computationDispatcher = main.dispatcher))
        val vm = model(saved)
        val first = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect() }
        runCurrent()
        vm.selectRange(InsightRange.THIRTY); runCurrent()
        first.cancel(); advanceTimeBy(5_000); runCurrent()
        assertEquals(0, records.readers)
        val restored = model(SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) }))
        val second = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { restored.uiState.collect() }
        runCurrent()
        assertEquals(InsightRange.THIRTY, restored.uiState.value.range)
        assertNotNull(restored.uiState.value.presentation)
        second.cancel()
    }

    @Test fun aFailedRangeSaveKeepsThePreviousSelectionUntilAnExplicitSuccessfulSave() = runTest(main.dispatcher) {
        val selected = MutableStateFlow<String?>(InsightRange.NINETY.name)
        var fail = true
        val settings = object : InsightPreferences {
            override fun observe(vaultId: String?): Flow<Set<String>?> = flowOf(null)
            override fun observeRange() = selected
            override suspend fun save(vaultId: String?, names: Set<String>) = Unit
            override suspend fun saveRange(range: String) { check(!fail) { "storage unavailable" }; selected.value = range }
        }
        val vm = main.own(InsightViewModel(flowOf(VaultConfigState.NotConfigured), { error("no vault") },
            FakeRecords(), aliases, SavedStateHandle(), {}, preferences = settings, computationDispatcher = main.dispatcher))
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect() }
        runCurrent()
        vm.selectRange(InsightRange.THIRTY); runCurrent()
        assertEquals(InsightRange.NINETY, vm.uiState.value.range)
        assertNotNull(vm.uiState.value.message)
        fail = false
        vm.selectRange(InsightRange.THIRTY); runCurrent()
        assertEquals(InsightRange.THIRTY, vm.uiState.value.range)
        assertNull(vm.uiState.value.message)
        assertFalse(vm.uiState.value.rangeSaving)
        collector.cancel()
    }

    @Test fun insightObservesOnlyWhileVisibleAndRestartsAfterReturning() = runTest(main.dispatcher) {
        val records = FakeRecords()
        val vm = main.own(InsightViewModel(flowOf(VaultConfigState.NotConfigured),
            { error("no vault") }, records, aliases, SavedStateHandle(), {}, computationDispatcher = main.dispatcher))
        runCurrent()
        assertEquals(0, records.readers)
        val first = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect() }
        runCurrent()
        assertEquals(1, records.readers)
        assertNotNull(vm.uiState.value.presentation)
        first.cancel()
        advanceTimeBy(4_999)
        runCurrent()
        assertEquals(1, records.readers)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(0, records.readers)
        val second = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect() }
        runCurrent()
        assertEquals(1, records.readers)
        second.cancel()
    }

}
