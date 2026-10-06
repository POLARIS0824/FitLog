package com.example.fitlog.diary

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.example.fitlog.data.analysis.*
import com.example.fitlog.data.hash.contentTextHash
import com.example.fitlog.data.vault.*
import com.example.fitlog.navigation.FitLogRoute
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DiaryDetailViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val route = FitLogRoute.DiaryDetail("content://vault", "00000000-0000-4000-8000-000000000001",
        "content://document", "daily/note.md", "content://daily", "note.md")
    private val reader = Reader()
    private val documents = Documents()
    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { store.clear(); Dispatchers.resetMain() }

    private fun vm(saved: SavedStateHandle = SavedStateHandle()): DiaryDetailViewModel =
        DiaryDetailViewModel(route, documents, reader, saved, dispatcher).also { store.put("detail", it) }

    private fun records(text: String): DiaryParseRecords {
        val input = DiaryParseInput.fromSnapshot(SourceKey(route.vaultId, route.relPath), text, "test-v1")
        val attempt = DiaryParseAttempt("run", input.parseKey, ParseRunStatus.SUCCEEDED, 1, 2, null)
        return DiaryParseRecords(listOf(attempt), StoredDiaryCandidate(attempt, DiaryAnalysis(input.parseKey, emptyList(), emptyList(), emptyList())))
    }

    private fun confirmation(records: DiaryParseRecords): ConfirmedDiaryRecord {
        val key = records.latestCandidate!!.analysis.parseKey
        return ConfirmedDiaryRecord(ConfirmedDiaryRow("confirmed", route.vaultId, route.relPath, "2026-10-02",
            key.contentHash, key.hashVersion, "run", 3, false), emptyList())
    }

    @Test fun unparsedDiaryShowsExactOriginalAndNeverScansOrWrites() = runTest(dispatcher) {
        documents.text = "# note\r\n40kg 2x8\n"
        val vm = vm(); runCurrent()
        assertEquals(documents.text, vm.original?.text)
        assertEquals(ConfirmationFreshness.UNCONFIRMED, vm.confirmationStatus)
        assertTrue(vm.parses.attempts.isEmpty())
        assertFalse(vm.parsesReadFailed)
        assertEquals(listOf(vm.sourceKey, vm.sourceKey), reader.keys)
    }

    @Test fun refreshAfterExternalEditMarksBothSavedResultsStaleWithoutChangingThem() = runTest(dispatcher) {
        val saved = records("note")
        reader.parses.value = saved; reader.confirmed.value = confirmation(saved)
        val vm = vm(); runCurrent()
        assertEquals(DiaryResultFreshness.CURRENT, vm.candidateStatus)
        assertEquals(ConfirmationFreshness.CONFIRMED, vm.confirmationStatus)
        documents.text = "externally edited"
        vm.refresh()
        assertNull(vm.contentVersion)
        runCurrent()
        assertEquals(DiaryResultFreshness.NEEDS_UPDATE, vm.candidateStatus)
        assertEquals(ConfirmationFreshness.NEEDS_UPDATE, vm.confirmationStatus)
        assertEquals(saved, vm.parses)
        assertEquals(reader.confirmed.value, vm.confirmed)
    }

    @Test fun unreadableOriginalRetainsSavedResultsAndCanBeRetried() = runTest(dispatcher) {
        val saved = records("note")
        reader.parses.value = saved; reader.confirmed.value = confirmation(saved)
        val vm = vm(); runCurrent()
        documents.fail = true; vm.refresh(); runCurrent()
        assertTrue(vm.sourceReadFailed); assertNull(vm.original)
        assertNull(vm.editorRoute())
        assertEquals(DiaryResultFreshness.UNVERIFIABLE, vm.candidateStatus)
        assertEquals(ConfirmationFreshness.UNVERIFIABLE, vm.confirmationStatus)
        assertEquals(saved, vm.parses); assertNotNull(vm.confirmed)
        documents.fail = false; vm.refresh(); runCurrent()
        assertFalse(vm.sourceReadFailed)
        assertEquals(ConfirmationFreshness.CONFIRMED, vm.confirmationStatus)
    }

    @Test fun failedAnalysisReadLeavesOriginalAvailableAndRetryRestoresBothObservers() = runTest(dispatcher) {
        reader.fail = true
        val vm = vm(); runCurrent()
        assertTrue(vm.parsesReadFailed); assertTrue(vm.confirmationReadFailed)
        assertEquals("note", vm.original?.text)
        reader.fail = false; reader.parses.value = records("note")
        reader.confirmed.value = confirmation(reader.parses.value)
        vm.retryAnalysis(); runCurrent()
        assertFalse(vm.parsesReadFailed); assertFalse(vm.confirmationReadFailed)
        assertNotNull(vm.parses.latestCandidate); assertNotNull(vm.confirmed)
    }

    @Test fun corruptCandidateAndLatestFailureDoNotHideConfirmationOrOriginal() = runTest(dispatcher) {
        val saved = records("note")
        reader.confirmed.value = confirmation(saved)
        val failed = saved.attempts.single().copy(id = "failed", status = ParseRunStatus.FAILED, failureCode = "TIMEOUT")
        reader.parses.value = DiaryParseRecords(saved.attempts + failed, candidateReadFailed = true)
        val vm = vm(); runCurrent()
        assertTrue(vm.parses.candidateReadFailed)
        assertEquals(failed, vm.parses.latestFailure)
        assertNotNull(vm.confirmed); assertEquals("note", vm.original?.text)
    }

    @Test fun contentVersionNormalizesBomAndLineEndingsOnceAndIgnoresByteFingerprint() = runTest(dispatcher) {
        documents.text = "\uFEFFnote\r\ntext\r"
        documents.bom = true
        val vm = vm(); runCurrent()
        assertEquals(contentTextHash("\uFEFF\uFEFFnote\r\ntext\r"), vm.contentVersion?.contentHash)
        assertNotEquals(documents.snapshot().fingerprint, vm.contentVersion?.contentHash)
        assertEquals(documents.text, vm.original?.text)
    }

    @Test fun differentHashVersionsCannotClaimCurrentResults() = runTest(dispatcher) {
        val original = records("note")
        val candidate = original.latestCandidate!!
        val key = candidate.analysis.parseKey.copy(hashVersion = 2)
        val attempt = candidate.attempt.copy(parseKey = key)
        val differentVersion = DiaryParseRecords(listOf(attempt), StoredDiaryCandidate(attempt, candidate.analysis.copy(parseKey = key)))
        reader.parses.value = differentVersion; reader.confirmed.value = confirmation(differentVersion)
        val vm = vm(); runCurrent()
        assertEquals(DiaryResultFreshness.UNVERIFIABLE, vm.candidateStatus)
        assertEquals(ConfirmationFreshness.UNVERIFIABLE, vm.confirmationStatus)
    }

    @Test fun readOnlyFilesDisableEditingAndWritableRoutesKeepOriginalIdentity() = runTest(dispatcher) {
        documents.writable = false
        val vm = vm(); runCurrent()
        assertNull(vm.editorRoute())
        documents.writable = true; vm.refresh(); runCurrent()
        val editor = vm.editorRoute()!!
        assertEquals(route.document, editor.document)
        assertEquals(route.vaultId, editor.vaultId)
        assertEquals(route.directory, editor.directory)
        assertEquals(route.relPath, editor.displayPath)
        assertEquals(route.fileName, editor.fileName)
    }

    @Test fun selectedTabSurvivesViewModelRecreation() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val first = vm(saved); runCurrent(); first.selectTab(DiaryDetailTab.ANALYSIS)
        val second = vm(SavedStateHandle(mapOf("detailTab" to saved.get<String>("detailTab")))); runCurrent()
        assertEquals(DiaryDetailTab.ANALYSIS, second.tab)
        assertEquals(route, second.route)
    }

    @Test fun repeatedResumeIsSingleFlightAndClearedReadCannotPublishIntoAnotherDiary() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        documents.gate = gate
        val old = vm(); runCurrent(); old.refresh(); old.refresh(); runCurrent()
        assertEquals(1, documents.reads)
        store.clear()
        val newRoute = route.copy(vaultId = "00000000-0000-4000-8000-000000000002", document = "content://other")
        val newDocuments = Documents().apply { text = "other diary" }
        val newReader = Reader()
        val current = DiaryDetailViewModel(newRoute, newDocuments, newReader, SavedStateHandle(), dispatcher)
        store.put("other", current); runCurrent()
        gate.complete(Unit); runCurrent()
        assertNull(old.original)
        assertEquals("other diary", current.original?.text)
        assertTrue(newReader.keys.all { it.vaultId == newRoute.vaultId })
    }

    private class Reader : DiaryAnalysisReader {
        val parses = MutableStateFlow(DiaryParseRecords())
        val confirmed = MutableStateFlow<ConfirmedDiaryRecord?>(null)
        val keys = mutableListOf<SourceKey>()
        var fail = false
        override fun observeParses(sourceKey: SourceKey): Flow<DiaryParseRecords> {
            keys += sourceKey
            return if (fail) flow { throw IOException() } else parses
        }
        override fun observeConfirmed(sourceKey: SourceKey): Flow<ConfirmedDiaryRecord?> {
            keys += sourceKey
            return if (fail) flow { throw IOException() } else confirmed
        }
    }

    private class Documents : MarkdownDocuments {
        var text = "note"; var bom = false; var fail = false; var writable = true; var reads = 0
        var gate: CompletableDeferred<Unit>? = null
        fun snapshot() = MarkdownSnapshot(MarkdownFile("content://document", "note.md", "daily/note.md", writable,
            "content://daily"), text, "byte-fingerprint", bom)
        override suspend fun read(uri: String): MarkdownSnapshot {
            reads++
            // A provider callback may finish even after the owning route is destroyed.
            gate?.let { withContext(NonCancellable) { it.await() } }
            if (fail) throw IOException()
            return snapshot()
        }
        override suspend fun scan(vault: String): MarkdownScan = error("No scan expected")
        override suspend fun find(vault: String, name: String): MarkdownFile? = error("No lookup expected")
        override suspend fun create(vault: String, name: String): MarkdownFile = error("No create expected")
        override suspend fun write(uri: String, text: String, bom: Boolean, expected: String): MarkdownSnapshot = error("No write expected")
    }
}
