package com.example.fitlog.data.index

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
    @Test fun uuidOwnsIndexWhileSafReceivesOnlyTheTreeUri() = runTest {
        val uri = "content://test/tree/a"
        val id = "00000000-0000-4000-8000-000000000001"
        var scanned: String? = null
        documents.onScan = { scanned = it }
        val index = SourceIndexRepository(documents, store, resolveVaultUri = { requested ->
            assertEquals(id, requested); android.net.Uri.parse(uri)
        })
        assertTrue(index.observe(id).first().sources.isEmpty())
        index.refresh(id)
        assertEquals(uri, scanned)
        assertEquals(id, store.sources(id).single().vaultId)
        assertEquals(id, store.observe(id).first().scan?.vaultId)
    }

    @Test fun uriCannotBeUsedAsIndexIdentity() = runTest {
        try {
            index.refresh("content://test/tree/a")
            fail("A locator must not be accepted as a UUID")
        } catch (_: IllegalArgumentException) { }
        assertEquals(0, documents.scanCount)
    }

    @Test fun indexFailureAfterMarkdownSaveLeavesARepairMarker() = runTest {
        val id = "00000000-0000-4000-8000-000000000001"
        val dirty = MemoryIndexInvalidations()
        store.fail = true
        val index = SourceIndexRepository(documents, store, invalidations = dirty,
            resolveVaultUri = { android.net.Uri.parse("content://test/tree/a") })
        try { index.recordSaved(id, documents.snapshot("saved"), "directory", "note.md"); fail() }
        catch (_: IOException) { }
        assertTrue(dirty.contains(id))
        store.fail = false
        index.recordSaved(id, documents.snapshot("saved"), "directory", "note.md")
        assertEquals("saved", store.sources(id).single().fingerprint)
    }

    private val vault = "00000000-0000-4000-8000-000000000001"
    private val store = MemorySourceIndexStore()
    private val documents = IndexDocuments()
    private val index = SourceIndexRepository(documents, store, now = { 42 }, resolveVaultUri = { android.net.Uri.parse("content://test/tree/a") })

    @Test fun rescanningUpdatesFingerprintWithoutDuplicatesAndIsolatesVaults() = runTest {
        documents.body = "one"
        index.refresh(vault)
        documents.body = "two"
        index.refresh(vault)
        index.refresh("00000000-0000-4000-8000-000000000002")
        assertEquals(1, store.sources(vault).size)
        assertEquals("two", store.sources(vault).single().fingerprint)
        assertEquals(1, store.sources("00000000-0000-4000-8000-000000000002").size)
    }

    @Test fun partialAndFailedScansNeverMarkUnseenFilesMissing() = runTest {
        index.refresh(vault)
        documents.files = emptyList(); documents.partial = true
        index.refresh(vault)
        assertEquals(IndexedSource.AVAILABLE, store.sources(vault).single().status)
        documents.failScan = true
        try { index.refresh(vault); fail() } catch (_: IOException) { }
        assertEquals(IndexedSource.AVAILABLE, store.sources(vault).single().status)
        documents.failScan = false; documents.partial = false
        index.refresh(vault)
        assertEquals(IndexedSource.MISSING, store.sources(vault).single().status)
        assertEquals(IndexedScan.COMPLETE, store.observe(vault).first().scan?.status)
    }

    @Test fun unreadableContentsRetainLastVerifiedVersion() = runTest {
        index.refresh(vault)
        documents.failRead = true
        index.refresh(vault)
        val row = store.sources(vault).single()
        assertEquals("one", row.fingerprint)
        assertEquals(42L, row.verifiedAt)
        assertEquals(IndexedSource.READ_FAILED, row.status)
        assertEquals(IndexedScan.PARTIAL, store.observe(vault).first().scan?.status)
    }

    @Test fun newerEditorSaveWinsOverScanSnapshotAndMissingReconciliation() = runTest {
        val read = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        documents.beforeRead = { read.complete(Unit); release.await() }
        val scan = launch { index.refresh(vault) }
        read.await()
        index.recordSaved(vault, documents.snapshot("saved"), "content://test/tree/a", "daily/note.md")
        release.complete(Unit); scan.join()
        assertEquals("saved", store.sources(vault).single().fingerprint)
        assertEquals("daily/note.md", store.sources(vault).single().path)
    }

    @Test fun cancellationDiscardsLateProviderResultAndDuplicateRefreshDoesNotRunTwice() = runTest {
        index.refresh(vault)
        val release = CompletableDeferred<Unit>()
        documents.beforeScan = { withContext(NonCancellable) { release.await() } }
        val scan = launch { index.refresh(vault) }
        runCurrent()
        index.refresh(vault)
        assertEquals(2, documents.scanCount)
        index.cancel(vault)
        documents.files = emptyList()
        release.complete(Unit); scan.join()
        assertEquals(IndexedSource.AVAILABLE, store.sources(vault).single().status)
        assertEquals(IndexedScan.INTERRUPTED, store.observe(vault).first().scan?.status)
    }

    @Test fun changedUriIsANewSourceAndInterruptedPersistedScanIsNotRunning() = runTest {
        index.refresh(vault)
        documents.files = listOf(MarkdownFile("new", "note.md", "note.md", true, "content://test/tree/a"))
        index.refresh(vault)
        assertEquals(2, store.sources(vault).size)
        assertEquals(IndexedSource.MISSING, store.sources(vault).first { it.uri == "file" }.status)
        store.commit(emptyList(), IndexedScan(vault, IndexedScan.SCANNING))
        assertEquals(IndexedScan.INTERRUPTED, index.observe(vault).first().scan?.status)
    }
}

private class IndexDocuments : MarkdownDocuments {
    var files = listOf(MarkdownFile("file", "note.md", "note.md", true, "vault"))
    var body = "one"
    var partial = false
    var failScan = false
    var failRead = false
    var scanCount = 0
    var beforeScan: suspend () -> Unit = {}
    var onScan: (String) -> Unit = {}
    var beforeRead: suspend () -> Unit = {}
    fun snapshot(text: String, uri: String = "file") = MarkdownSnapshot(MarkdownFile(uri, "note.md", "note.md", true), text, text, false)
    override suspend fun scan(vault: String): MarkdownScan { scanCount++; onScan(vault); beforeScan(); if (failScan) throw IOException(); return MarkdownScan(files, partial) }
    override suspend fun read(uri: String): MarkdownSnapshot { beforeRead(); if (failRead) throw IOException(); return snapshot(body, uri) }
    override suspend fun find(vault: String, name: String): MarkdownFile? = error("unused")
    override suspend fun create(vault: String, name: String): MarkdownFile = error("unused")
    override suspend fun write(uri: String, text: String, bom: Boolean, expected: String): MarkdownSnapshot = error("unused")
}
