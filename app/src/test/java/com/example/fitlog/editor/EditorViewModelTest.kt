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

    @Test fun indexFailureDoesNotTurnSuccessfulMarkdownSaveIntoFailure() = runTest(dispatcher) {
        val vm = EditorViewModel(FitLogRoute.Editor("vault", date = "2026-09-29"), repository, drafts) { _, _, _ -> throw IOException() }
        store.put("editor", vm); runCurrent()
        type(vm, "saved locally"); runCurrent(); vm.saveNow(); runCurrent()
        assertEquals(EditorSaveState.Saved, vm.state)
        assertEquals("saved locally", repository.files.values.single().text)
        assertEquals(com.example.fitlog.R.string.index_save_failed, vm.notice)
    }

    @Test fun restoredBackupRequiresExplicitSaveAndChecksNewExternalChanges() = runTest(dispatcher) {
        repository.files["file"] = repository.snapshot("file", "old.md", "current")
        val draft = EditorDraft("vault", "recovered", "file", "old.md", "preimage", 0, 0, 1, "old-hash", false,
            directory = "vault", manualSave = true, restoredBackup = true)
        drafts.save(draft)
        val vm = EditorViewModel(FitLogRoute.Editor("vault", "file", recoveryId = "draft:${draft.identity()}"), repository, drafts)
        store.put("editor", vm); runCurrent(); advanceTimeBy(20000); runCurrent()
        assertEquals("preimage", vm.text.text.toString())
        assertFalse(vm.conflict)
        assertEquals(0, repository.writes)
        vm.onBackground(); runCurrent()
        assertEquals(0, repository.writes)
        repository.files["file"] = repository.snapshot("file", "old.md", "external")
        vm.saveNow(); runCurrent()
        assertTrue(vm.conflict)
        assertEquals("external", repository.files["file"]!!.text)
    }

    @Test fun completedRecoveryReopensSavedFileAfterDraftWasRemoved() = runTest(dispatcher) {
        val draft = EditorDraft("vault", "old-day.md", null, "old-day.md", "recover", 0, 0, 1, null, false, directory = "vault")
        drafts.save(draft)
        val route = FitLogRoute.Editor("vault", fileName = "old-day.md", recoveryId = "draft:${draft.identity()}")
        val state = androidx.lifecycle.SavedStateHandle()
        val first = EditorViewModel(route, repository, drafts, state)
        store.put("editor", first); runCurrent(); first.saveNow(); runCurrent()
        assertEquals(EditorSaveState.Saved, first.state)
        assertTrue(drafts.values.isEmpty())
        val recreated = EditorViewModel(route, repository, drafts, state)
        store.put("editor", recreated); runCurrent()
        assertEquals("recover", recreated.text.text.toString())
        assertEquals(EditorSaveState.Saved, recreated.state)
        assertNull(recreated.error)
    }

    @Test fun reauthorizationRechecksFingerprintWithoutDiscardingOrAutomaticallyWritingRecovery() = runTest(dispatcher) {
        val draft = EditorDraft("vault", "file", "file", "note.md", "mine", 0, 0, 1, "original", false, directory = "vault")
        drafts.save(draft)
        val vm = EditorViewModel(FitLogRoute.Editor("vault", "file", recoveryId = "draft:${draft.identity()}"), repository, drafts)
        store.put("editor", vm); runCurrent()
        assertTrue(vm.conflict)
        repository.files["file"] = repository.snapshot("file", "note.md", "original")
        vm.onReauthorized(); runCurrent()
        assertFalse(vm.conflict)
        assertEquals("mine", vm.text.text.toString())
        assertEquals(0, repository.writes)
        vm.saveNow(); runCurrent()
        assertEquals("mine", repository.files["file"]!!.text)
    }

    @Test fun exportSnapshotSurvivesProcessRecreationAndAutosaveCleanup() = runTest(dispatcher) {
        val route = FitLogRoute.Editor("vault", date = "2026-09-29")
        val state = androidx.lifecycle.SavedStateHandle()
        val first = EditorViewModel(route, repository, drafts, state)
        store.put("editor", first); runCurrent()
        type(first, "picker snapshot"); runCurrent()
        var launches = 0
        first.prepareExport { launches++ }; runCurrent()
        first.prepareExport { launches++ }; runCurrent()
        assertEquals(1, launches)
        type(first, "later text"); runCurrent()
        first.saveNow(); runCurrent()
        assertEquals("later text", repository.files.values.single().text)
        val restoredState = androidx.lifecycle.SavedStateHandle(state.keys().associateWith { state.get<Any?>(it) })
        store.clear()
        val recreated = EditorViewModel(route, repository, drafts, restoredState)
        store.put("editor", recreated); runCurrent()
        var exported: EditorDraft? = null
        recreated.export { exported = it }; runCurrent()
        assertEquals("picker snapshot", exported?.text)
        assertEquals("vault", exported?.directory)
        assertNotNull(drafts.entry("draft:${exported!!.identity()}"))
        assertEquals(com.example.fitlog.R.string.recovery_exported, recreated.notice)
    }

    @Test fun cancelledAndFailedEditorExportsKeepLocalSnapshots() = runTest(dispatcher) {
        val vm = vm(); runCurrent()
        type(vm, "keep me"); runCurrent()
        vm.prepareExport {}; runCurrent(); vm.cancelExport()
        val count = drafts.values.size
        vm.export { fail("Cancelled export must not write") }; runCurrent()
        assertEquals(count, drafts.values.size)
        vm.prepareExport {}; runCurrent()
        val beforeFailure = drafts.values.size
        vm.export { throw IOException() }; runCurrent()
        assertEquals(beforeFailure, drafts.values.size)
        assertEquals(com.example.fitlog.R.string.recovery_export_failed, vm.notice)
    }

    @Test fun restoringExportSnapshotDoesNotOverwriteNewerOrdinaryDraft() = runTest(dispatcher) {
        val first = vm(); runCurrent()
        type(first, "export snapshot"); runCurrent()
        first.prepareExport {}; runCurrent(); first.cancelExport()
        val exported = drafts.values.values.single { it.target.startsWith("export:") }
        val normalTarget = newDraftTarget(first.route)
        drafts.save(exported.copy(target = normalTarget, originTarget = normalTarget,
            recoveryId = "", text = "newer unsaved text"))
        store.clear()
        val restored = EditorViewModel(first.route.copy(recoveryId = "draft:${exported.identity()}"), repository, drafts)
        store.put("editor", restored); runCurrent()
        restored.onBackground(); runCurrent()
        assertEquals("export snapshot", restored.text.text.toString())
        assertEquals("newer unsaved text", drafts.read("vault", normalTarget)?.text)
        assertEquals("export snapshot", drafts.read("vault", exported.target)?.text)
        assertEquals(0, repository.writes)
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
    override suspend fun entries() = values.values.map { RecoveryEntry("draft:${it.identity()}", it) }
    override suspend fun deleteEntry(id: String) { values.entries.removeAll { "draft:${it.value.identity()}" == id } }
}
