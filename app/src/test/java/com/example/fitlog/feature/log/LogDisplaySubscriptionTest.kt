@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.example.fitlog.feature.log

import androidx.lifecycle.SavedStateHandle
import com.example.fitlog.data.index.SourceIndex
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
