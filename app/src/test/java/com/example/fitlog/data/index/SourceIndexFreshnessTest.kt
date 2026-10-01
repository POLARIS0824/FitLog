package com.example.fitlog.data.index

import com.example.fitlog.data.vault.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class SourceIndexFreshnessTest {
    private class Documents : MarkdownDocuments {
        var files = listOf(MarkdownFile("file", "note.md", "note.md", true, "vault", 10, 3))
        var body = "one"
        var reads = 0
        var scans = 0
        var partial = false
        var failRead = false
        var failScan = false
        var beforeScan: suspend () -> Unit = {}
        override suspend fun scan(vault: String): MarkdownScan {
            scans++; beforeScan()
            if (failScan) throw IOException()
            return MarkdownScan(files, partial)
        }
        override suspend fun read(uri: String): MarkdownSnapshot {
            reads++
            if (failRead) throw IOException()
            return MarkdownSnapshot(files.first { it.uri == uri }, body, body, false)
        }
        override suspend fun find(vault: String, name: String): MarkdownFile? = error("unused")
        override suspend fun create(vault: String, name: String): MarkdownFile = error("unused")
        override suspend fun write(uri: String, text: String, bom: Boolean, expected: String): MarkdownSnapshot = error("unused")
    }
    private class Fixture(scope: CoroutineScope) {
        var time = 1_000L
        var elapsed = 0L
        val documents = Documents()
        val store = MemorySourceIndexStore()
        val dirty = MemoryIndexInvalidations()
        val index = SourceIndexRepository(documents, store, { time }, scope, { elapsed }, dirty)
        fun foreground() = index.ensureFresh("vault", SourceIndexRepository.Reason.Foreground)
        fun tick(delta: Long) { time += delta; elapsed += delta }
    }

    @Test fun navigationAndForegroundAreThrottledWhileUnchangedBodiesAreReused() = runTest {
        val f = Fixture(this)
        f.index.activate("vault"); runCurrent()
        assertEquals(1, f.documents.reads)
        f.index.activate("vault"); f.foreground(); runCurrent()
        assertEquals(1, f.documents.scans)
        f.tick(29_999); f.foreground(); runCurrent()
        assertEquals(1, f.documents.scans)
        f.tick(1); f.foreground(); runCurrent()
        assertEquals(2, f.documents.scans)
        assertEquals(1, f.documents.reads)
        // An ordinary page return never scans, even after the freshness interval.
        f.tick(SourceIndexRepository.VISIBLE_INTERVAL); f.index.activate("vault"); runCurrent()
        assertEquals(2, f.documents.scans)
        f.index.ensureFresh("vault", SourceIndexRepository.Reason.VisiblePeriodic); runCurrent()
        assertEquals(3, f.documents.scans)
    }

    @Test fun changedAndUnknownMetadataAreReadButRenameReusesFingerprint() = runTest {
        val f = Fixture(this)
        f.foreground(); runCurrent()
        f.documents.files = f.documents.files.map { it.copy(name = "renamed.md", path = "daily/renamed.md") }
        f.tick(30_000); f.foreground(); runCurrent()
        assertEquals(1, f.documents.reads)
        assertEquals("daily/renamed.md", f.store.sources("vault").single().path)
        f.documents.body = "two"
        f.documents.files = f.documents.files.map { it.copy(lastModified = 20) }
        f.tick(30_000); f.foreground(); runCurrent()
        assertEquals(2, f.documents.reads)
        assertEquals("two", f.store.sources("vault").single().fingerprint)
        f.documents.files = f.documents.files.map { it.copy(size = null) }
        repeat(2) { f.tick(30_000); f.foreground(); runCurrent() }
        assertEquals(4, f.documents.reads)
    }

    @Test fun fullVerificationDetectsSameMetadataChangesAndManualRefreshBypassesThrottle() = runTest {
        val f = Fixture(this)
        f.foreground(); runCurrent()
        f.documents.body = "two"
        f.tick(30_000); f.foreground(); runCurrent()
        assertEquals("one", f.store.sources("vault").single().fingerprint)
        f.index.forceRefresh("vault"); runCurrent()
        assertEquals("two", f.store.sources("vault").single().fingerprint)
        f.documents.body = "end"
        f.tick(SourceIndexRepository.FULL_INTERVAL); f.foreground(); runCurrent()
        assertEquals("end", f.store.sources("vault").single().fingerprint)
        assertEquals(f.time, f.store.observe("vault").first().scan?.fullVerifiedAt)
    }

    @Test fun failuresPreserveVerifiedMetadataAndRetryUnreadableSources() = runTest {
        val f = Fixture(this)
        f.foreground(); runCurrent()
        f.documents.files = f.documents.files.map { it.copy(lastModified = 20) }
        f.documents.failRead = true
        f.tick(30_000); f.foreground(); runCurrent()
        val row = f.store.sources("vault").single()
        assertEquals(10L, row.lastModified)
        assertEquals("one", row.fingerprint)
        assertEquals(IndexedSource.READ_FAILED, row.status)
        assertEquals(1_000L, f.store.observe("vault").first().scan?.metadataCheckedAt)
        f.documents.failRead = false
        f.tick(30_000); f.foreground(); runCurrent()
        assertEquals(20L, f.store.sources("vault").single().lastModified)
        assertEquals(3, f.documents.reads)
    }

    @Test fun emptyVaultIsCompletedAndOnlyCompleteEnumerationMarksMissing() = runTest {
        val f = Fixture(this)
        f.documents.files = emptyList()
        f.foreground(); runCurrent()
        assertEquals(IndexedScan.COMPLETE, f.store.observe("vault").first().scan?.status)
        f.tick(30_000); f.foreground(); runCurrent()
        assertEquals(0, f.documents.reads)
        f.documents.files = listOf(MarkdownFile("new", "new.md", "sub/new.md", false, "sub", 1, 3))
        f.tick(30_000); f.foreground(); runCurrent()
        f.documents.files = emptyList(); f.documents.partial = true
        f.tick(30_000); f.foreground(); runCurrent()
        assertEquals(IndexedSource.AVAILABLE, f.store.sources("vault").single().status)
        f.documents.partial = false; f.documents.failScan = true
        f.tick(30_000); f.foreground(); runCurrent()
        assertEquals(IndexedSource.AVAILABLE, f.store.sources("vault").single().status)
        f.documents.failScan = false
        f.tick(30_000); f.foreground(); runCurrent()
        assertEquals(IndexedSource.MISSING, f.store.sources("vault").single().status)
    }

    @Test fun concurrentAutomaticRequestsMergeAndForceQueuesOneFollowup() = runTest {
        val f = Fixture(this)
        val release = CompletableDeferred<Unit>()
        f.documents.beforeScan = { release.await() }
        f.foreground(); runCurrent()
        repeat(3) { f.foreground(); f.index.forceRefresh("vault") }
        assertEquals(1, f.documents.scans)
        release.complete(Unit); runCurrent()
        assertEquals(2, f.documents.scans)
        assertFalse(f.index.observe("vault").first().refreshing)
    }

    @Test fun queuedForceStillRunsIfCurrentScanFails() = runTest {
        val f = Fixture(this)
        val release = CompletableDeferred<Unit>()
        f.documents.beforeScan = {
            if (f.documents.scans == 1) { release.await(); throw IOException() }
        }
        f.foreground(); runCurrent()
        f.index.forceRefresh("vault")
        release.complete(Unit); runCurrent()
        assertEquals(2, f.documents.scans)
        assertEquals(IndexedScan.COMPLETE, f.store.observe("vault").first().scan?.status)
        assertFalse(f.index.observe("vault").first().refreshFailed)
    }

    @Test fun coldStartUsesMetadataAndLegacyRowsAreReadOnce() = runTest {
        val f = Fixture(this)
        f.foreground(); runCurrent()
        val cold = SourceIndexRepository(f.documents, f.store, { f.time }, this, { f.elapsed })
        cold.activate("vault"); runCurrent()
        assertEquals(2, f.documents.scans)
        assertEquals(1, f.documents.reads)
        val old = f.store.sources("vault").single()
        f.store.commit(listOf(old.copy(lastModified = null, size = null)))
        f.tick(30_000); f.foreground(); runCurrent()
        assertEquals(2, f.documents.reads)
        f.tick(30_000); f.foreground(); runCurrent()
        assertEquals(2, f.documents.reads)
    }

    @Test fun failedIndexSaveInvalidatesAndRepairSurvivesNewCoordinator() = runTest {
        val f = Fixture(this)
        f.foreground(); runCurrent()
        f.store.fail = true
        try {
            f.index.recordSaved("vault", MarkdownSnapshot(f.documents.files.single(), "two", "two", false), "vault", null)
            fail()
        } catch (_: IOException) { }
        assertTrue(f.dirty.contains("vault"))
        f.store.fail = false; f.documents.body = "two"
        val restarted = SourceIndexRepository(f.documents, f.store, { f.time }, this, { f.elapsed }, f.dirty)
        restarted.ensureFresh("vault", SourceIndexRepository.Reason.Activation); runCurrent()
        assertEquals("two", f.store.sources("vault").single().fingerprint)
        assertFalse(f.dirty.contains("vault"))
    }

    @Test fun saveDuringScanWinsAndInvalidationQueuesFullRepair() = runTest {
        val f = Fixture(this)
        f.foreground(); runCurrent()
        val release = CompletableDeferred<Unit>()
        f.documents.beforeScan = { release.await() }
        f.tick(30_000); f.foreground(); runCurrent()
        f.store.fail = true
        try { f.index.recordSaved("vault", MarkdownSnapshot(f.documents.files.single(), "two", "two", false), "vault", null) }
        catch (_: IOException) { }
        f.store.fail = false; f.documents.body = "two"
        release.complete(Unit); runCurrent()
        assertEquals(3, f.documents.scans)
        assertEquals("two", f.store.sources("vault").single().fingerprint)
        assertFalse(f.dirty.contains("vault"))
    }

    @Test fun switchingVaultCancelsIgnoredProviderResultsAndPageDisposalDoesNot() = runTest {
        val f = Fixture(this)
        val release = CompletableDeferred<Unit>()
        f.documents.beforeScan = { withContext(NonCancellable) { release.await() } }
        f.index.activate("vault"); runCurrent()
        f.index.activate("vault"); runCurrent()
        assertTrue(f.index.observe("vault").first().refreshing)
        f.index.activate(null)
        release.complete(Unit); runCurrent()
        assertTrue(f.store.sources("vault").isEmpty())
        assertEquals(IndexedScan.INTERRUPTED, f.index.observe("vault").first().scan?.status)
    }

    @Test fun failedScansAreThrottledAndClockChangesDoNotBypassForegroundInterval() = runTest {
        val f = Fixture(this)
        f.documents.failScan = true
        f.foreground(); runCurrent()
        f.time += SourceIndexRepository.FULL_INTERVAL
        f.foreground(); runCurrent()
        assertEquals(1, f.documents.scans)
        f.elapsed += 30_000
        f.foreground(); runCurrent()
        assertEquals(2, f.documents.scans)
        assertTrue(f.index.observe("vault").first().refreshFailed)
    }
}
