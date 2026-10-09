package com.example.fitlog.data.index

import android.net.Uri
import com.example.fitlog.data.vault.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SourceIndexRepositoryTest {
    private val vault = "00000000-0000-4000-8000-000000000001"
    private val other = "00000000-0000-4000-8000-000000000002"
    private class Fixture(scope: CoroutineScope) {
        val documents = IndexDocuments()
        val store = MemorySourceIndexStore()
        val index = SourceIndexRepository(documents, store, now = { 42 }, scope = scope,
            resolveVaultUri = { Uri.parse("content://test/tree/a") })
    }

    @Test fun uuidOwnsIndexWhileSafReceivesOnlyTheTreeUri() = runTest {
        val f = Fixture(this)
        f.index.refresh(vault).join()
        assertEquals("content://test/tree/a", f.documents.scanned)
        assertEquals(vault, f.store.sources(vault).single().vaultId)
        assertEquals(vault, f.store.observe(vault).first().scan?.vaultId)
        try { f.index.refresh("content://test/tree/a"); fail() } catch (_: IllegalArgumentException) { }
        assertEquals(1, f.documents.scanCount)
    }

    @Test fun everyRefreshReadsAllFilesRegardlessOfUnchangedMetadata() = runTest {
        val f = Fixture(this)
        f.index.refresh(vault).join()
        f.documents.body = "two"
        f.index.refresh(vault).join()
        f.index.refresh(other).join()
        assertEquals(listOf("one", "two", "two"), f.documents.readBodies)
        assertEquals(1, f.store.sources(vault).size)
        assertEquals(1, f.store.sources(other).size)
        assertEquals(42L, f.store.observe(vault).first().scan?.completedAt)
    }

    @Test fun scanReadAndSaveVersionsNormalizeBomAndLineEndingsWithoutChangingRawFingerprint() = runTest {
        val f = Fixture(this)
        f.documents.body = "\uFEFFone\r\ntwo"
        f.index.refresh(vault).join()
        val row = f.store.sources(vault).single()
        val expected = com.example.fitlog.data.hash.ContentTextSnapshot.fromRawText("one\ntwo")
        assertEquals(expected.hash, row.contentHash); assertEquals(expected.hashVersion, row.hashVersion)
        val snapshot = MarkdownSnapshot(row.file(), "one\ntwo", "raw-byte-fingerprint", false)
        f.index.recordRead(vault, row.path, snapshot)
        assertEquals(row.contentHash, f.store.sources(vault).single().contentHash)
        assertEquals(42L, f.store.observe(vault).first().scan?.completedAt)
        f.index.recordSaved(vault, snapshot.copy(text = "edited"), row.directory!!, row.path)
        assertNotEquals(row.contentHash, f.store.sources(vault).single().contentHash)
        assertEquals("raw-byte-fingerprint", snapshot.fingerprint)
        assertEquals(1, f.documents.scanCount)
    }

    @Test fun partialFailedAndUnreadableScansNeverMarkUnseenFilesMissing() = runTest {
        val f = Fixture(this)
        f.documents.files += MarkdownFile("other", "other.md", "other.md", true)
        f.index.refresh(vault).join()
        f.documents.files = f.documents.files.take(1)
        f.documents.partial = true
        f.index.refresh(vault).join()
        assertTrue(f.store.sources(vault).none { it.status == IndexedSource.MISSING })
        f.documents.partial = false
        f.documents.failRead = true
        f.index.refresh(vault).join()
        assertEquals(IndexedScan.PARTIAL, f.store.observe(vault).first().scan?.status)
        assertEquals(IndexedSource.READ_FAILED, f.store.sources(vault).first { it.uri == "file" }.status)
        assertEquals(IndexedSource.AVAILABLE, f.store.sources(vault).first { it.uri == "other" }.status)
        f.documents.failScan = true
        f.index.refresh(vault).join()
        assertEquals(IndexedScan.FAILED, f.store.observe(vault).first().scan?.status)
        assertTrue(f.store.sources(vault).none { it.status == IndexedSource.MISSING })
        f.documents.failScan = false
        f.documents.failRead = false
        f.index.refresh(vault).join()
        assertEquals(IndexedSource.MISSING, f.store.sources(vault).first { it.uri == "other" }.status)
    }

    @Test fun failedSaveIndexUpdateOnlyThrowsAndManualRefreshRepairsIt() = runTest {
        val f = Fixture(this)
        f.store.fail = true
        try { f.index.recordSaved(vault, f.documents.snapshot("saved"), "directory", "daily/note.md"); fail() }
        catch (_: IOException) { }
        runCurrent()
        assertEquals(0, f.documents.scanCount)
        f.index.refresh(vault).join()
        assertTrue(f.index.observe(vault).first().refreshFailed)
        runCurrent()
        assertEquals(1, f.documents.scanCount)
        f.store.fail = false
        f.index.refresh(vault).join()
        assertFalse(f.index.observe(vault).first().refreshFailed)
        assertEquals(2, f.documents.scanCount)
        assertEquals(IndexedScan.COMPLETE, f.store.observe(vault).first().scan?.status)
    }

    @Test fun saveDuringScanIsAppliedAfterTheOlderScanSnapshot() = runTest {
        val f = Fixture(this)
        val read = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        f.documents.beforeRead = { read.complete(Unit); release.await() }
        val scan = f.index.refresh(vault)
        read.await()
        val save = async { f.index.recordSaved(vault, f.documents.snapshot("saved"), "directory", "daily/note.md") }
        runCurrent()
        assertFalse(save.isCompleted)
        release.complete(Unit)
        scan.join(); save.await()
        assertEquals("daily/note.md", f.store.sources(vault).single().path)
        assertEquals(1, f.documents.scanCount)
    }

    @Test fun duplicateRefreshSharesOneScanWithoutAQueuedFollowup() = runTest {
        val f = Fixture(this)
        val release = CompletableDeferred<Unit>()
        f.documents.beforeScan = { release.await() }
        val first = f.index.refresh(vault)
        runCurrent()
        repeat(3) { assertSame(first, f.index.refresh(vault)) }
        assertTrue(f.index.observe(vault).first().refreshing)
        release.complete(Unit)
        first.join()
        runCurrent()
        assertEquals(1, f.documents.scanCount)
        assertFalse(f.index.observe(vault).first().refreshing)
    }

    @Test fun appOpeningScansOnceAndConfigurationChangesDoNotScanImplicitly() = runTest {
        val f = Fixture(this)
        f.index.activate(vault)
        advanceUntilIdle()
        repeat(3) { f.index.activate(vault) }
        f.index.activate(null)
        f.index.activate(vault)
        advanceUntilIdle()
        assertEquals(1, f.documents.scanCount)
        f.index.refresh(vault).join()
        assertEquals(2, f.documents.scanCount)
        val unconfigured = Fixture(this)
        unconfigured.index.activate(null)
        unconfigured.index.activate(vault)
        advanceUntilIdle()
        assertEquals(0, unconfigured.documents.scanCount)
        unconfigured.index.refresh(vault).join()
        assertEquals(1, unconfigured.documents.scanCount)
    }

    @Test fun switchingVaultCancelsLateProviderResultsAndScansNeverOverlap() = runTest {
        val f = Fixture(this)
        f.index.activate(vault)
        advanceUntilIdle()
        val release = CompletableDeferred<Unit>()
        f.documents.beforeScan = { withContext(NonCancellable) { release.await() } }
        val old = f.index.refresh(vault)
        runCurrent()
        f.index.activate(other)
        f.documents.files = emptyList()
        f.documents.beforeScan = {}
        val next = f.index.refresh(other)
        runCurrent()
        assertEquals(2, f.documents.scanCount)
        release.complete(Unit)
        old.join(); next.join()
        assertEquals(3, f.documents.scanCount)
        assertEquals(IndexedSource.AVAILABLE, f.store.sources(vault).single().status)
        assertEquals(IndexedScan.FAILED, f.store.observe(vault).first().scan?.status)
        assertEquals(IndexedScan.COMPLETE, f.store.observe(other).first().scan?.status)
    }

    @Test fun changedUriIsANewSourceAndEmptyVaultCompletes() = runTest {
        val f = Fixture(this)
        f.index.refresh(vault).join()
        f.documents.files = listOf(MarkdownFile("new", "note.md", "note.md", true))
        f.index.refresh(vault).join()
        assertEquals(2, f.store.sources(vault).size)
        assertEquals(IndexedSource.MISSING, f.store.sources(vault).first { it.uri == "file" }.status)
        f.documents.files = emptyList()
        f.index.refresh(vault).join()
        assertTrue(f.store.sources(vault).all { it.status == IndexedSource.MISSING })
        assertEquals(IndexedScan.COMPLETE, f.store.observe(vault).first().scan?.status)
    }
}

private class IndexDocuments : MarkdownDocuments {
    var files = listOf(MarkdownFile("file", "note.md", "note.md", true, "vault", 1, 3))
    var body = "one"
    var partial = false
    var failScan = false
    var failRead = false
    var scanCount = 0
    var scanned: String? = null
    val readBodies = mutableListOf<String>()
    var beforeScan: suspend () -> Unit = {}
    var beforeRead: suspend () -> Unit = {}
    fun snapshot(text: String, uri: String = "file") = MarkdownSnapshot(MarkdownFile(uri, "note.md", "note.md", true), text, text, false)
    override suspend fun scan(vault: String): MarkdownScan {
        scanCount++; scanned = vault; beforeScan()
        if (failScan) throw IOException()
        return MarkdownScan(files, partial)
    }
    override suspend fun read(uri: String): MarkdownSnapshot {
        beforeRead()
        if (failRead) throw IOException()
        readBodies += body
        return snapshot(body, uri)
    }
    override suspend fun find(vault: String, name: String): MarkdownFile? = error("unused")
    override suspend fun create(vault: String, name: String): MarkdownFile = error("unused")
    override suspend fun write(uri: String, text: String, bom: Boolean, expected: String): MarkdownSnapshot = error("unused")
}
