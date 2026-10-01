package com.example.fitlog.data.index

import com.example.fitlog.data.vault.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class SourceIndexRepositoryTest {
    private val vault = "vault"
    private val store = MemorySourceIndexStore()
    private val documents = IndexDocuments()
    private val index = SourceIndexRepository(documents, store, now = { 42 })

    @Test fun rescanningUpdatesFingerprintWithoutDuplicatesAndIsolatesVaults() = runTest {
        documents.body = "one"
        index.refresh(vault)
        documents.body = "two"
        index.refresh(vault)
        index.refresh("other")
        assertEquals(1, store.sources(vault).size)
        assertEquals("two", store.sources(vault).single().fingerprint)
        assertEquals(1, store.sources("other").size)
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
        index.recordSaved(vault, documents.snapshot("saved"), vault, "daily/note.md")
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
        documents.files = listOf(MarkdownFile("new", "note.md", "note.md", true, vault))
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
    var beforeRead: suspend () -> Unit = {}
    fun snapshot(text: String, uri: String = "file") = MarkdownSnapshot(MarkdownFile(uri, "note.md", "note.md", true), text, text, false)
    override suspend fun scan(vault: String): MarkdownScan { scanCount++; beforeScan(); if (failScan) throw IOException(); return MarkdownScan(files, partial) }
    override suspend fun read(uri: String): MarkdownSnapshot { beforeRead(); if (failRead) throw IOException(); return snapshot(body, uri) }
    override suspend fun find(vault: String, name: String): MarkdownFile? = error("unused")
    override suspend fun create(vault: String, name: String): MarkdownFile = error("unused")
    override suspend fun write(uri: String, text: String, bom: Boolean, expected: String): MarkdownSnapshot = error("unused")
}
