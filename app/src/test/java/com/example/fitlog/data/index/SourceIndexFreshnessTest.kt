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
        val index = SourceIndexRepository(documents, store, { time }, scope, { elapsed }, dirty, resolveVaultUri = { android.net.Uri.parse("content://test/tree/a") })
        fun foreground() = index.ensureFresh("00000000-0000-4000-8000-000000000001", SourceIndexRepository.Reason.Foreground)
        fun tick(delta: Long) { time += delta; elapsed += delta }
    }

    @Test fun navigationAndForegroundAreThrottledWhileUnchangedBodiesAreReused() = runTest {
        val f = Fixture(this)
        f.index.activate("00000000-0000-4000-8000-000000000001"); runCurrent()
        assertEquals(1, f.documents.reads)
        f.index.activate("00000000-0000-4000-8000-000000000001"); f.foreground(); runCurrent()
        assertEquals(1, f.documents.scans)
        f.tick(29_999); f.foreground(); runCurrent()
        assertEquals(1, f.documents.scans)
        f.tick(1); f.foreground(); runCurrent()
        assertEquals(2, f.documents.scans)
        assertEquals(1, f.documents.reads)
        // An ordinary page return never scans, even after the freshness interval.
        f.tick(SourceIndexRepository.VISIBLE_INTERVAL); f.index.activate("00000000-0000-4000-8000-000000000001"); runCurrent()
        assertEquals(2, f.documents.scans)
        f.index.ensureFresh("00000000-0000-4000-8000-000000000001", SourceIndexRepository.Reason.VisiblePeriodic); runCurrent()
        assertEquals(3, f.documents.scans)
    }

    @Test fun changedAndUnknownMetadataAreReadButRenameReusesFingerprint() = runTest {
        val f = Fixture(this)
        f.foreground(); runCurrent()
        f.documents.files = f.documents.files.map { it.copy(name = "renamed.md", path = "daily/renamed.md") }
        f.tick(30_000); f.foreground(); runCurrent()
        assertEquals(1, f.documents.reads)
        assertEquals("daily/renamed.md", f.store.sources("00000000-0000-4000-8000-000000000001").single().path)
        f.documents.body = "two"
        f.documents.files = f.documents.files.map { it.copy(lastModified = 20) }
        f.tick(30_000); f.foreground(); runCurrent()
        assertEquals(2, f.documents.reads)
        assertEquals("two", f.store.sources("00000000-0000-4000-8000-000000000001").single().fingerprint)
        f.documents.files = f.documents.files.map { it.copy(size = null) }
        repeat(2) { f.tick(30_000); f.foreground(); runCurrent() }
        assertEquals(4, f.documents.reads)
    }

    @Test fun fullVerificationDetectsSameMetadataChangesAndManualRefreshBypassesThrottle() = runTest {
        val f = Fixture(this)
        f.foreground(); runCurrent()
        f.documents.body = "two"
        f.tick(30_000); f.foreground(); runCurrent()
        assertEquals("one", f.store.sources("00000000-0000-4000-8000-000000000001").single().fingerprint)
        f.index.forceRefresh("00000000-0000-4000-8000-000000000001"); runCurrent()
        assertEquals("two", f.store.sources("00000000-0000-4000-8000-000000000001").single().fingerprint)
        f.documents.body = "end"
        f.tick(SourceIndexRepository.FULL_INTERVAL); f.foreground(); runCurrent()
        assertEquals("end", f.store.sources("00000000-0000-4000-8000-000000000001").single().fingerprint)
        assertEquals(f.time, f.store.observe("00000000-0000-4000-8000-000000000001").first().scan?.fullVerifiedAt)
    }

    @Test fun failuresPreserveVerifiedMetadataAndRetryUnreadableSources() = runTest {
        val f = Fixture(this)
        f.foreground(); runCurrent()
        f.documents.files = f.documents.files.map { it.copy(lastModified = 20) }
        f.documents.failRead = true
        f.tick(30_000); f.foreground(); runCurrent()
        val row = f.store.sources("00000000-0000-4000-8000-000000000001").single()
        assertEquals(10L, row.lastModified)
        assertEquals("one", row.fingerprint)
        assertEquals(IndexedSource.READ_FAILED, row.status)
        assertEquals(1_000L, f.store.observe("00000000-0000-4000-8000-000000000001").first().scan?.metadataCheckedAt)
        f.documents.failRead = false
        f.tick(30_000); f.foreground(); runCurrent()
        assertEquals(20L, f.store.sources("00000000-0000-4000-8000-000000000001").single().lastModified)
        assertEquals(3, f.documents.reads)
    }

    @Test fun emptyVaultIsCompletedAndOnlyCompleteEnumerationMarksMissing() = runTest {
        val f = Fixture(this)
        f.documents.files = emptyList()
        f.foreground(); runCurrent()
        assertEquals(IndexedScan.COMPLETE, f.store.observe("00000000-0000-4000-8000-000000000001").first().scan?.status)
        f.tick(30_000); f.foreground(); runCurrent()
        assertEquals(0, f.documents.reads)
        f.documents.files = listOf(MarkdownFile("new", "new.md", "sub/new.md", false, "sub", 1, 3))
        f.tick(30_000); f.foreground(); runCurrent()
        f.documents.files = emptyList(); f.documents.partial = true
        f.tick(30_000); f.foreground(); runCurrent()
        assertEquals(IndexedSource.AVAILABLE, f.store.sources("00000000-0000-4000-8000-000000000001").single().status)
        f.documents.partial = false; f.documents.failScan = true
        f.tick(30_000); f.foreground(); runCurrent()
        assertEquals(IndexedSource.AVAILABLE, f.store.sources("00000000-0000-4000-8000-000000000001").single().status)
        f.documents.failScan = false
        f.tick(30_000); f.foreground(); runCurrent()
        assertEquals(IndexedSource.MISSING, f.store.sources("00000000-0000-4000-8000-000000000001").single().status)
    }

    @Test fun concurrentAutomaticRequestsMergeAndForceQueuesOneFollowup() = runTest {
        val f = Fixture(this)
        val release = CompletableDeferred<Unit>()
        f.documents.beforeScan = { release.await() }
        f.foreground(); runCurrent()
        repeat(3) { f.foreground(); f.index.forceRefresh("00000000-0000-4000-8000-000000000001") }
        assertEquals(1, f.documents.scans)
        release.complete(Unit); runCurrent()
        assertEquals(2, f.documents.scans)
        assertFalse(f.index.observe("00000000-0000-4000-8000-000000000001").first().refreshing)
    }

    @Test fun queuedForceStillRunsIfCurrentScanFails() = runTest {
        val f = Fixture(this)
        val release = CompletableDeferred<Unit>()
        f.documents.beforeScan = {
            if (f.documents.scans == 1) { release.await(); throw IOException() }
        }
        f.foreground(); runCurrent()
        f.index.forceRefresh("00000000-0000-4000-8000-000000000001")
        release.complete(Unit); runCurrent()
        assertEquals(2, f.documents.scans)
        assertEquals(IndexedScan.COMPLETE, f.store.observe("00000000-0000-4000-8000-000000000001").first().scan?.status)
        assertFalse(f.index.observe("00000000-0000-4000-8000-000000000001").first().refreshFailed)
    }

    @Test fun coldStartUsesMetadataAndUnverifiedRowsAreReadOnce() = runTest {
        val f = Fixture(this)
        f.foreground(); runCurrent()
        val cold = SourceIndexRepository(f.documents, f.store, { f.time }, this, { f.elapsed }, resolveVaultUri = { android.net.Uri.parse("content://test/tree/a") })
        cold.activate("00000000-0000-4000-8000-000000000001"); runCurrent()
        assertEquals(2, f.documents.scans)
        assertEquals(1, f.documents.reads)
        val old = f.store.sources("00000000-0000-4000-8000-000000000001").single()
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
            f.index.recordSaved("00000000-0000-4000-8000-000000000001", MarkdownSnapshot(f.documents.files.single(), "two", "two", false), "vault", null)
            fail()
        } catch (_: IOException) { }
        assertTrue(f.dirty.contains("00000000-0000-4000-8000-000000000001"))
        f.store.fail = false; f.documents.body = "two"
        val restarted = SourceIndexRepository(f.documents, f.store, { f.time }, this, { f.elapsed }, f.dirty, resolveVaultUri = { android.net.Uri.parse("content://test/tree/a") })
        restarted.ensureFresh("00000000-0000-4000-8000-000000000001", SourceIndexRepository.Reason.Activation); runCurrent()
        assertEquals("two", f.store.sources("00000000-0000-4000-8000-000000000001").single().fingerprint)
        assertFalse(f.dirty.contains("00000000-0000-4000-8000-000000000001"))
    }

    @Test fun saveDuringScanWinsAndInvalidationQueuesFullRepair() = runTest {
        val f = Fixture(this)
        f.foreground(); runCurrent()
        val release = CompletableDeferred<Unit>()
        f.documents.beforeScan = { release.await() }
        f.tick(30_000); f.foreground(); runCurrent()
        f.store.fail = true
        try { f.index.recordSaved("00000000-0000-4000-8000-000000000001", MarkdownSnapshot(f.documents.files.single(), "two", "two", false), "vault", null) }
        catch (_: IOException) { }
        f.store.fail = false; f.documents.body = "two"
        release.complete(Unit); runCurrent()
        assertEquals(3, f.documents.scans)
        assertEquals("two", f.store.sources("00000000-0000-4000-8000-000000000001").single().fingerprint)
        assertFalse(f.dirty.contains("00000000-0000-4000-8000-000000000001"))
    }

    @Test fun switchingVaultCancelsIgnoredProviderResultsAndPageDisposalDoesNot() = runTest {
        val f = Fixture(this)
        val release = CompletableDeferred<Unit>()
        f.documents.beforeScan = { withContext(NonCancellable) { release.await() } }
        f.index.activate("00000000-0000-4000-8000-000000000001"); runCurrent()
        f.index.activate("00000000-0000-4000-8000-000000000001"); runCurrent()
        assertTrue(f.index.observe("00000000-0000-4000-8000-000000000001").first().refreshing)
        f.index.activate(null)
        release.complete(Unit); runCurrent()
        assertTrue(f.store.sources("00000000-0000-4000-8000-000000000001").isEmpty())
        assertEquals(IndexedScan.INTERRUPTED, f.index.observe("00000000-0000-4000-8000-000000000001").first().scan?.status)
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
        assertTrue(f.index.observe("00000000-0000-4000-8000-000000000001").first().refreshFailed)
    }
}
