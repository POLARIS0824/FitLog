@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.example.fitlog

import androidx.lifecycle.SavedStateHandle
import com.example.fitlog.data.index.*
import com.example.fitlog.data.insight.*
import com.example.fitlog.data.vault.VaultConfigState
import com.example.fitlog.insight.InsightViewModel
import com.example.fitlog.log.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

class DisplaySubscriptionTest {
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
        vm.search("squat")
        runCurrent()
        assertEquals("squat", vm.uiState.value.query)
        collector.cancel()
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(0, records.readers)
        assertEquals("squat", vm.uiState.value.query)
    }
}
