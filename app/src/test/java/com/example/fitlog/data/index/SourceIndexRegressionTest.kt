@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.example.fitlog.data.index

import com.example.fitlog.data.hash.ContentTextSnapshot
import com.example.fitlog.data.vault.MarkdownDocuments
import com.example.fitlog.data.vault.MarkdownFile
import com.example.fitlog.data.vault.MarkdownScan
import com.example.fitlog.data.vault.MarkdownSnapshot
import com.example.fitlog.data.vault.nextMarkdownReadOrder
import com.example.fitlog.model.source.IndexedScan
import com.example.fitlog.model.source.IndexedSource
import com.example.fitlog.model.source.SourceIndexSnapshot
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

class SourceIndexRegressionTest {
    private val vault = "00000000-0000-0000-0000-000000000001"
    private class Store : SourceIndexStore {
        val scans = mutableListOf<IndexedScan>()
        var rows = emptyList<IndexedSource>()
        override fun observe(vaultId: String) = flowOf(SourceIndexSnapshot(rows, scans.lastOrNull()))
        override suspend fun sources(vaultId: String) = rows
        override suspend fun commit(sources: List<IndexedSource>, scan: IndexedScan?) {
            val updated = rows.associateByTo(linkedMapOf()) { it.vaultId to it.uri }
            sources.forEach { updated[it.vaultId to it.uri] = it }
            rows = updated.values.toList()
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

    @Test fun queuedReadCannotReplaceAScannedVersionWithTheSameObservationTime() =
        queuedReadDoesNotReplaceScan(olderOrder = 100, scannedOrder = 100)

    @Test fun queuedReadCannotReplaceAScannedVersionWithALaterObservationTime() =
        queuedReadDoesNotReplaceScan(olderOrder = 100, scannedOrder = 200)

    @Test fun observationOrderIncreasesWhenTheWallClockRepeatsOrMovesBackwards() {
        val now = System.currentTimeMillis()
        val first = nextMarkdownReadOrder(now)
        val sameMillisecond = nextMarkdownReadOrder(now)
        val clockMovedBackwards = nextMarkdownReadOrder(now - 60_000)
        assertTrue(first < sameMillisecond)
        assertTrue(sameMillisecond < clockMovedBackwards)
    }

    @Test fun anOldReadCannotResurrectASourceMissingFromACompleteScan() = runTest {
        val file = MarkdownFile("content://test/deleted", "deleted.md", "deleted.md", false)
        val older = MarkdownSnapshot(file, "version A", "A", false)
        val store = Store().apply {
            rows = listOf(IndexedSource(vault, file.uri, file.name, file.path, null, false))
        }
        val source = object : MarkdownDocuments {
            override suspend fun scan(vault: String) = MarkdownScan(emptyList(), partial = false)
            override suspend fun read(uri: String): MarkdownSnapshot = error("not reached")
        }
        val repo = SourceIndexRepository(source, store,
            computationDispatcher = StandardTestDispatcher(testScheduler),
            scope = backgroundScope, resolveVaultUri = { "content://test/vault" })
        repo.refresh(vault).join()
        repo.recordRead(vault, file.path, older)
        assertEquals(IndexedSource.MISSING, store.rows.single().status)
    }

    private fun queuedReadDoesNotReplaceScan(olderOrder: Long, scannedOrder: Long) = runTest {
        val file = MarkdownFile("content://test/diary", "diary.md", "diary.md", false)
        val older = MarkdownSnapshot(file, "version A", "A", false, readAt = olderOrder)
        val scanned = MarkdownSnapshot(file, "version B", "B", false, readAt = scannedOrder)
        val oldVersion = ContentTextSnapshot.fromRawText(older.text)
        val scannedVersion = ContentTextSnapshot.fromRawText(scanned.text)
        val store = Store().apply {
            rows = listOf(IndexedSource(vault, file.uri, file.name, file.path, null, false,
                contentHash = oldVersion.hash, hashVersion = oldVersion.hashVersion))
        }
        val scanStarted = CompletableDeferred<Unit>()
        val continueScan = CompletableDeferred<Unit>()
        val source = object : MarkdownDocuments {
            override suspend fun scan(vault: String): MarkdownScan {
                scanStarted.complete(Unit)
                continueScan.await()
                return MarkdownScan(listOf(file), partial = false)
            }
            override suspend fun read(uri: String) = scanned
        }
        val repo = SourceIndexRepository(source, store,
            computationDispatcher = StandardTestDispatcher(testScheduler),
            scope = backgroundScope, resolveVaultUri = { "content://test/vault" })
        val scan = repo.refresh(vault)
        scanStarted.await()
        val queuedRead = backgroundScope.launch { repo.recordRead(vault, file.path, older) }
        runCurrent()
        assertFalse(queuedRead.isCompleted)
        continueScan.complete(Unit)
        scan.join()
        queuedRead.join()
        assertEquals(scannedVersion.hash, store.rows.single().contentHash)
        assertEquals(scannedOrder, store.rows.single().observedAt)

        val newer = MarkdownSnapshot(file, "version C", "C", false, readAt = scannedOrder + 1)
        repo.recordRead(vault, file.path, newer)
        assertEquals(ContentTextSnapshot.fromRawText(newer.text).hash, store.rows.single().contentHash)
    }

}
