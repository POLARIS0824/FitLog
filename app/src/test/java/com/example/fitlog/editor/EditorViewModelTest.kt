package com.example.fitlog.editor

import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.ViewModelStore
import com.example.fitlog.data.vault.*
import com.example.fitlog.navigation.FitLogRoute
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EditorViewModelTest {
    @Test fun newDraftKeysSeparateFoldersAndKeepLegacyRootDrafts() {
        val vault = "content://test/tree/root"
        val root = "content://test/tree/root/document/root"
        val base = FitLogRoute.Editor(vault, date = "2026-09-30")
        assertEquals("2026-09-30.md", newDraftTarget(base))
        assertEquals(newDraftTarget(base), newDraftTarget(base.copy(directory = root)))
        assertNotEquals(newDraftTarget(base.copy(directory = "$root%2Fa")), newDraftTarget(base.copy(directory = "$root%2Fb")))
    }
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { store.clear(); Dispatchers.resetMain() }
    private val repository = FakeDocuments()
    private val drafts = FakeDrafts()
    private fun vm(document: String? = null, vault: String = "vault"): EditorViewModel {
        val vm = EditorViewModel(FitLogRoute.Editor(vault, document, "2026-09-29"), repository, drafts)
        store.put("editor", vm)
        return vm
    }
    private fun type(vm: EditorViewModel, value: String) {
        vm.text.edit { replace(0, length, value) }
        Snapshot.sendApplyNotifications()
    }
    @Test fun newFileNotCreatedUntilSaveAndCollisionNeverOverwritten() = runTest(dispatcher) {
        val vm = vm(); runCurrent()
        assertEquals(0, repository.created)
        type(vm, "卧推"); runCurrent()
        repository.files["existing"] = repository.snapshot("existing", "2026-09-29.md", "external")
        vm.saveNow(); runCurrent()
        assertNotNull(vm.collision)
        assertEquals("external", repository.files["existing"]!!.text)
        assertEquals("卧推", vm.text.text.toString())
        assertEquals(0, repository.writes)
    }
    @Test fun saveOfOldSnapshotDoesNotMarkNewInputSaved() = runTest(dispatcher) {
        val vm = vm(); runCurrent()
        type(vm, "first"); runCurrent()
        val gate = CompletableDeferred<Unit>(); repository.gate = gate
        vm.saveNow(); runCurrent()
        assertEquals(EditorSaveState.Saving, vm.state)
        type(vm, "second"); runCurrent()
        gate.complete(Unit); runCurrent()
        assertEquals("first", repository.files.values.single().text)
        assertEquals(EditorSaveState.Unsaved, vm.state)
        advanceTimeBy(1600); runCurrent()
        assertEquals("second", repository.files.values.single().text)
        assertEquals(EditorSaveState.Saved, vm.state)
    }
    @Test fun externalChangePausesAutomaticSaving() = runTest(dispatcher) {
        repository.files["file"] = repository.snapshot("file", "old.md", "original")
        val vm = vm("file"); runCurrent()
        type(vm, "mine"); runCurrent()
        repository.files["file"] = repository.snapshot("file", "old.md", "external")
        vm.saveNow(); runCurrent()
        assertTrue(vm.conflict)
        type(vm, "more"); runCurrent(); advanceTimeBy(15000); runCurrent()
        assertEquals(0, repository.writes)
        assertEquals("more", drafts.values["vault:file"]!!.text)
    }
    @Test fun sourceMissingStillOffersRecoverableDraft() = runTest(dispatcher) {
        drafts.values["vault:file"] = EditorDraft("vault", "file", "file", "old.md", "recover me", 0, 0, 2, "old", false)
        val vm = vm("file"); runCurrent()
        assertNotNull(vm.recovery)
        vm.restoreDraft(); runCurrent()
        assertEquals("recover me", vm.text.text.toString())
        assertTrue(vm.conflict)
    }
    @Test fun readOnlyAndDraftVaultIsolation() = runTest(dispatcher) {
        repository.files["file"] = repository.snapshot("file", "old.md", "original").let { it.copy(file = it.file.copy(writable = false)) }
        drafts.values["other:file"] = EditorDraft("other", "file", "file", "old.md", "other vault", 0, 0, 1, "original", false)
        val vm = vm("file"); runCurrent()
        assertFalse(vm.canEdit)
        assertNull(vm.recovery)
        vm.saveNow(); runCurrent()
        assertEquals(0, repository.writes)
    }
    @Test fun failedWriteRetainsDraftAndDoesNotRetryForever() = runTest(dispatcher) {
        val vm = vm(); runCurrent()
        repository.fail = true
        type(vm, "important"); runCurrent(); vm.saveNow(); runCurrent()
        assertEquals(EditorSaveState.Failed, vm.state)
        assertEquals("important", drafts.values["vault:2026-09-29.md"]!!.text)
        val count = repository.writes
        advanceTimeBy(20000); runCurrent()
        assertEquals(count, repository.writes)
        repository.fail = false
        vm.saveNow(); runCurrent()
        assertEquals(EditorSaveState.Saved, vm.state)
    }
}

private class FakeDocuments : MarkdownDocuments {
    val files = linkedMapOf<String, MarkdownSnapshot>()
    var created = 0
    var writes = 0
    var fail = false
    var gate: CompletableDeferred<Unit>? = null
    fun snapshot(uri: String, name: String, text: String) = MarkdownSnapshot(MarkdownFile(uri, name, name, true), text, text, false)
    override suspend fun scan(vault: String) = MarkdownScan(files.values.map { it.file }, false)
    override suspend fun find(vault: String, name: String) = files.values.firstOrNull { it.file.name == name }?.file
    override suspend fun read(uri: String) = files[uri] ?: throw IOException()
    override suspend fun create(vault: String, name: String): MarkdownFile {
        find(vault, name)?.let { throw NameCollision(it) }
        val uri = "new-${++created}"
        val snapshot = snapshot(uri, name, ""); files[uri] = snapshot
        return snapshot.file
    }
    override suspend fun write(uri: String, text: String, bom: Boolean, expected: String): MarkdownSnapshot {
        writes++; gate?.await()
        if (fail) throw IOException()
        val before = read(uri)
        if (before.fingerprint != expected) throw DocumentConflict()
        return snapshot(uri, before.file.name, text).also { files[uri] = it }
    }
}
private class FakeDrafts : Drafts {
    val values = mutableMapOf<String, EditorDraft>()
    override suspend fun read(vault: String, target: String) = values["$vault:$target"]
    override suspend fun save(draft: EditorDraft) { values["${draft.vault}:${draft.target}"] = draft }
    override suspend fun remove(vault: String, target: String) { values.remove("$vault:$target") }
    override suspend fun backup(vault: String, target: String, bytes: ByteArray) = Unit
}
