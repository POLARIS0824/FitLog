@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.example.fitlog.feature.insight

import androidx.lifecycle.SavedStateHandle
import com.example.fitlog.data.settings.ExerciseAliasStore
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
