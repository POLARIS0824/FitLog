@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.example.fitlog

import com.example.fitlog.data.index.*
import com.example.fitlog.data.vault.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Test
import org.junit.Assert.*

class SourceIndexRegressionTest {
    private val vault = "00000000-0000-0000-0000-000000000001"
    private class Store : SourceIndexStore {
        val scans = mutableListOf<IndexedScan>()
        var rows = emptyList<IndexedSource>()
        override fun observe(vaultId: String) = flowOf(SourceIndexSnapshot(rows, scans.lastOrNull()))
        override suspend fun sources(vaultId: String) = rows
        override suspend fun commit(sources: List<IndexedSource>, scan: IndexedScan?) {
            rows = sources
            scan?.let(scans::add)
        }
    }
    private val documents = object : MarkdownDocuments {
        override suspend fun scan(vault: String): MarkdownScan = error("not reached")
        override suspend fun read(uri: String): MarkdownSnapshot = error("not reached")
    }

    @Test fun cancelledScanKeepsTheLastSuccessfulScan() = runTest {
        val store = Store().apply { scans += IndexedScan(vault, IndexedScan.COMPLETE, 7) }
        val repo = SourceIndexRepository(documents, store, computationDispatcher = StandardTestDispatcher(testScheduler),
            scope = backgroundScope, resolveVaultUri = { awaitCancellation() })
        val scan = repo.refresh(vault)
        runCurrent()
        scan.cancelAndJoin()
        assertEquals(listOf(IndexedScan.COMPLETE), store.scans.map { it.status })
        assertFalse(repo.observe(vault).first().refreshFailed)
    }

    @Test fun genuineScanFailureIsStillReportedAndExplicitRefreshCanRetry() = runTest {
        val store = Store()
        var attempts = 0
        val repo = SourceIndexRepository(documents, store, computationDispatcher = StandardTestDispatcher(testScheduler),
            scope = backgroundScope, resolveVaultUri = { attempts++; error("permission lost") })
        repo.refresh(vault).join()
        assertEquals(IndexedScan.FAILED, store.scans.single().status)
        assertTrue(repo.observe(vault).first().refreshFailed)
        repo.refresh(vault).join()
        assertEquals(2, attempts)
    }

    @Test fun concurrentRefreshesShareTheSameScan() = runTest {
        val repo = SourceIndexRepository(documents, Store(), computationDispatcher = StandardTestDispatcher(testScheduler),
            scope = backgroundScope, resolveVaultUri = { awaitCancellation() })
        val first = repo.refresh(vault)
        assertSame(first, repo.refresh(vault))
        first.cancelAndJoin()
    }

    @Test fun anUnconfiguredStartDoesNotConsumeTheFirstVaultScan() = runTest {
        var attempts = 0
        val repo = SourceIndexRepository(documents, Store(), computationDispatcher = StandardTestDispatcher(testScheduler),
            scope = backgroundScope, resolveVaultUri = { attempts++; awaitCancellation() })
        repo.activate(null)
        repo.activate(vault)
        runCurrent()
        assertEquals(1, attempts)
    }

}
