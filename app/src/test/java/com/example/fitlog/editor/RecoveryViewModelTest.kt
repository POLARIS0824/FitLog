package com.example.fitlog.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.example.fitlog.navigation.FitLogRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class RecoveryViewModelTest {
    @Test fun storedUuidResolvesItsOriginalUriAndKeepsOriginalLocation() = runTest(dispatcher) {
        drafts.save(draft)
        val id = "00000000-0000-4000-8000-000000000001"
        val vm = RecoveryViewModel(SavedStateHandle(), drafts) { requested ->
            assertEquals(id, requested); android.net.Uri.parse(draft.vaultUri)
        }.also { viewModels.put("recovery", it) }
        vm.refresh(); runCurrent(); vm.select(vm.entries.single().id)
        var route: FitLogRoute.Editor? = null
        vm.restore { route = it }; runCurrent()
        assertEquals(id, route?.vaultId)
        assertEquals(draft.vaultUri, route?.vaultUri)
        assertEquals(draft.directory, route?.directory)
        assertEquals(draft.name, route?.fileName)
    }

    @Test fun mismatchedLocatorCannotAssociateARecoveryWithAnotherVault() = runTest(dispatcher) {
        drafts.save(draft.copy(vaultId = "00000000-0000-4000-8000-000000000001"))
        val vm = RecoveryViewModel(SavedStateHandle(), drafts) { android.net.Uri.parse("content://test/tree/another") }
            .also { viewModels.put("recovery", it) }
        vm.refresh(); runCurrent(); vm.select(vm.entries.single().id)
        vm.restore { fail("Mismatched identity must not navigate") }; runCurrent()
        assertNotNull(vm.notice)
        assertEquals(1, drafts.values.size)
    }
    private val dispatcher = StandardTestDispatcher()
    private val viewModels = ViewModelStore()
    private val drafts = RecoveryMemory()
    private val draft = EditorDraft("old-vault", "old-day.md", null, "old-day.md", "我的日记", 0, 0, 1, null, false,
        directory = "old-directory", displayPath = "daily/old-day.md", updatedAt = 123, vaultId = "00000000-0000-4000-8000-000000000001")
    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { viewModels.clear(); Dispatchers.resetMain() }
    private fun vm(): RecoveryViewModel = RecoveryViewModel(SavedStateHandle(), drafts) { requested ->
        assertEquals(draft.vaultId, requested); android.net.Uri.parse(draft.vaultUri)
    }.also { viewModels.put("recovery", it) }

    @Test fun disconnectedVaultAndOldDateRestoreUseStoredIdentity() = runTest(dispatcher) {
        drafts.save(draft)
        val vm = vm(); vm.refresh(); runCurrent(); vm.select(vm.entries.single().id)
        var route: FitLogRoute.Editor? = null
        vm.restore { route = it }; runCurrent()
        assertEquals("old-vault", route?.vaultUri)
        assertEquals("old-directory", route?.directory)
        assertEquals("old-day.md", route?.fileName)
        assertEquals("daily/old-day.md", route?.displayPath)
    }

    @Test fun exportCancelFailureAndSuccessAllPreserveOriginalDraft() = runTest(dispatcher) {
        drafts.save(draft)
        val vm = vm(); vm.refresh(); runCurrent(); vm.select(vm.entries.single().id)
        vm.prepareExport({}, "fallback.md"); vm.cancelExport()
        assertEquals(1, drafts.values.size)
        vm.prepareExport({}, "fallback.md"); vm.export { throw IOException() }; runCurrent()
        assertNotNull(vm.notice); assertEquals(1, drafts.values.size)
        vm.prepareExport({}, "fallback.md")
        var body = ""
        vm.export { body = it.text }; runCurrent()
        assertEquals(draft.text, body); assertEquals(1, drafts.values.size)
    }

    @Test fun damagedEntryCannotNavigateAndBackupCreatesSeparateManualDraft() = runTest(dispatcher) {
        drafts.values["unknown"] = RecoveryEntry("unknown", null, backup = true, damaged = true)
        val vm = vm(); vm.refresh(); runCurrent(); vm.select("unknown")
        vm.restore { fail("Must not guess a location") }; runCurrent()
        assertNotNull(vm.notice)
        drafts.values["backup"] = RecoveryEntry("backup", draft.copy(document = "file"), backup = true)
        vm.refresh(); runCurrent(); vm.select("backup")
        var route: FitLogRoute.Editor? = null
        vm.restore { route = it }; runCurrent()
        val created = drafts.values[route!!.recoveryId]!!.draft!!
        assertTrue(created.manualSave)
        assertTrue(created.restoredBackup)
        assertEquals("file", created.document)
        assertTrue(drafts.values.containsKey("backup"))
    }

    @Test fun pickerResultWaitsForLifecycleRefreshRatherThanBeingDropped() = runTest(dispatcher) {
        drafts.save(draft)
        val vm = vm(); vm.refresh(); runCurrent(); vm.select(vm.entries.single().id)
        vm.prepareExport({}, "fallback.md")
        val gate = CompletableDeferred<Unit>()
        drafts.beforeEntries = { gate.await() }
        vm.refresh(); runCurrent()
        var exported = false
        vm.export { exported = true }; runCurrent()
        assertFalse(exported)
        gate.complete(Unit); runCurrent()
        assertTrue(exported)
        assertEquals(1, drafts.values.size)
    }

    @Test fun leavingAndReselectingDuringReadInvalidatesRestore() = runTest(dispatcher) {
        drafts.save(draft)
        val vm = vm(); vm.refresh(); runCurrent()
        val id = vm.entries.single().id
        vm.select(id)
        val gate = CompletableDeferred<Unit>()
        drafts.beforeEntries = { gate.await() }
        var opened = false
        vm.restore { opened = true }; runCurrent()
        vm.select(null); vm.select(id)
        gate.complete(Unit); runCurrent()
        assertFalse(opened)
        assertFalse(vm.busy)
    }

    @Test fun leavingDuringBackupCopyPreservesDraftWithoutNavigating() = runTest(dispatcher) {
        drafts.values["backup"] = RecoveryEntry("backup", draft.copy(document = "file"), backup = true)
        val vm = vm(); vm.refresh(); runCurrent(); vm.select("backup")
        val gate = CompletableDeferred<Unit>()
        drafts.beforeSave = { gate.await() }
        var opened = false
        vm.restore { opened = true }; runCurrent()
        assertTrue(vm.busy)
        vm.select(null)
        gate.complete(Unit); runCurrent()
        assertFalse(opened)
        assertFalse(vm.busy)
        assertEquals(2, drafts.values.size)
    }
}

private class RecoveryMemory : Drafts {
    var beforeEntries: suspend () -> Unit = {}
    var beforeSave: suspend () -> Unit = {}
    val values = mutableMapOf<String, RecoveryEntry>()
    override suspend fun read(vaultId: String, target: String) = values.values.mapNotNull { it.draft }.firstOrNull { it.vaultId == vaultId && it.target == target }
    override suspend fun save(draft: EditorDraft) { beforeSave(); val id = "draft:${draft.identity()}"; values[id] = RecoveryEntry(id, draft) }
    override suspend fun remove(vaultId: String, target: String) { values.entries.removeAll { it.value.draft?.let { d -> d.vaultId == vaultId && d.target == target } == true } }
    override suspend fun prepareBackup(draft: EditorDraft) = Unit
    override suspend fun entries(): List<RecoveryEntry> { beforeEntries(); return values.values.toList() }
    override suspend fun deleteEntry(id: String) { values.remove(id) }
}
