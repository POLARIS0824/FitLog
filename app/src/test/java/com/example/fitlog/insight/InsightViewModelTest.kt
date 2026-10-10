package com.example.fitlog.insight

import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.example.fitlog.R
import com.example.fitlog.data.analysis.*
import com.example.fitlog.data.index.SourceIndexSnapshot
import com.example.fitlog.data.insight.*
import com.example.fitlog.data.vault.VaultConfigState
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
class InsightViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val models = ViewModelStore()
    private val record = insightRecord()
    private val config = MutableStateFlow<VaultConfigState>(configured(INSIGHT_VAULT))
    private val records = mutableMapOf(INSIGHT_VAULT to MutableStateFlow(listOf(record)),
        OTHER_INSIGHT_VAULT to MutableStateFlow(listOf(insightRecord("other.md", vaultId = OTHER_INSIGHT_VAULT))))
    private val indices = records.mapValues { (_, flow) -> MutableStateFlow(insightIndex(*flow.value.toTypedArray())) }
    private val aliases = mutableMapOf(INSIGHT_VAULT to MutableStateFlow(ExerciseAliases()),
        OTHER_INSIGHT_VAULT to MutableStateFlow(ExerciseAliases()))
    private var aliasReadFails = false
    private var saveFails = false
    private var saveGate: CompletableDeferred<Unit>? = null
    private val refreshed = mutableListOf<String>()
    private var indexSubscriptions = 0
    private var modelCount = 0
    private val aliasStore = object : ExerciseAliasStore {
        override fun observe(vaultId: String): Flow<ExerciseAliases> = if (aliasReadFails) flow { throw IOException() }
            else aliases.getValue(vaultId)
        override suspend fun save(vaultId: String, previousPrimaryKey: String?, replacement: ExerciseAliasGroup?) {
            saveGate?.await()
            if (saveFails) throw IOException()
            aliases.getValue(vaultId).value = aliases.getValue(vaultId).value.replacing(previousPrimaryKey, replacement)
        }
    }
    private val reader = object : ConfirmedDiaryReader {
        override fun observeConfirmedDiaries(vaultId: String) = records.getValue(vaultId)
    }

    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { models.clear(); Dispatchers.resetMain() }
    private fun configured(id: String) = VaultConfigState.Configured("content://vault/$id".toUri(), id)
    private fun vm(saved: SavedStateHandle = SavedStateHandle(), target: String? = null) = InsightViewModel(config,
        { id -> indexSubscriptions++; indices.getValue(id) }, reader, aliasStore, saved, { refreshed += it },
        targetVaultId = target, localDate = { INSIGHT_TODAY }).also { models.put("insight-${modelCount++}", it) }

    @Test fun openingAndReturningDoNotScanAndConfirmationChangesUpdateTheStatistics() = runTest(dispatcher) {
        val vm = vm(); runCurrent()
        assertEquals(1, vm.data!!.totals(InsightRange.ALL).sets)
        assertTrue(refreshed.isEmpty())
        records.getValue(INSIGHT_VAULT).value = listOf(insightRecord(sets = listOf(insightSet(60.0), insightSet(60.0))))
        runCurrent()
        assertEquals(2, vm.data!!.totals(InsightRange.ALL).sets)
        vm.resumed(); runCurrent(); assertTrue(refreshed.isEmpty())
        vm.refresh(); runCurrent(); assertEquals(listOf(INSIGHT_VAULT), refreshed)
    }

    @Test fun sourceLinksUseTheCurrentSafLocatorAndRecheckAvailabilityAtClickTime() = runTest(dispatcher) {
        val vm = vm(); runCurrent()
        val key = SourceKey(INSIGHT_VAULT, record.diary.relPath)
        val route = vm.diaryRoute(key)!!
        assertTrue(route.openAnalysis)
        assertEquals(insightSource(record).uri, route.document)
        assertEquals(INSIGHT_VAULT, route.vaultId)
        indices.getValue(INSIGHT_VAULT).value = insightIndex().copy(sources = emptyList())
        runCurrent()
        assertNull(vm.diaryRoute(key)); assertEquals(R.string.insight_source_unavailable, vm.message)
    }

    @Test fun manualRefreshScansOnceAndDoesNotScanOnLaterVaultSwitches() = runTest(dispatcher) {
        val vm = vm(); runCurrent()
        vm.refresh(); runCurrent()
        assertEquals(listOf(INSIGHT_VAULT), refreshed)
        config.value = configured(OTHER_INSIGHT_VAULT); runCurrent()
        assertEquals(OTHER_INSIGHT_VAULT, vm.data!!.vaultId)
        assertEquals(listOf(INSIGHT_VAULT), refreshed)
    }

    @Test fun vaultSwitchCancelsOldSubscriptionsAndCannotNavigateToOldRecords() = runTest(dispatcher) {
        val vm = vm(); runCurrent()
        config.value = configured(OTHER_INSIGHT_VAULT); runCurrent()
        records.getValue(INSIGHT_VAULT).value = listOf(record, insightRecord("old-late.md")); runCurrent()
        assertEquals(OTHER_INSIGHT_VAULT, vm.data!!.vaultId)
        assertEquals(listOf("other.md"), vm.data!!.diaries.map { it.key.relPath })
        assertNull(vm.diaryRoute(SourceKey(INSIGHT_VAULT, record.diary.relPath)))
        assertTrue(refreshed.isEmpty())
    }

    @Test fun exerciseDetailBoundToAnOldVaultShowsNoStatisticsAfterSwitch() = runTest(dispatcher) {
        val vm = vm(target = INSIGHT_VAULT); runCurrent()
        config.value = configured(OTHER_INSIGHT_VAULT); runCurrent()
        assertFalse(vm.activeVault); assertNull(vm.data)
    }

    @Test fun aliasReadFailureNeverFallsBackToUngroupedStatisticsAndRetriesOnlyOnRequest() = runTest(dispatcher) {
        aliasReadFails = true
        val vm = vm(); runCurrent()
        assertNull(vm.data); assertEquals(R.string.insight_read_failed, vm.error)
        val subscriptions = indexSubscriptions
        advanceTimeBy(60_000); runCurrent()
        assertEquals(subscriptions, indexSubscriptions); assertTrue(refreshed.isEmpty())
        aliasReadFails = false; vm.refresh(); runCurrent()
        assertNotNull(vm.data); assertNull(vm.error)
    }

    @Test fun failedAliasSaveDoesNotCloseTheEditorOrChangeGroupingAndManualRetrySucceeds() = runTest(dispatcher) {
        val vm = vm(); runCurrent()
        val group = ExerciseAliasGroup("Bench", listOf("bench", "卧推"))
        var closed = false
        saveFails = true; vm.saveAliases(null, group) { closed = true }; runCurrent()
        assertFalse(closed); assertEquals(R.string.insight_alias_save_failed, vm.aliasError)
        assertTrue(vm.data!!.aliases.groups.isEmpty()); assertFalse(vm.aliasSaving)
        saveFails = false; vm.saveAliases(null, group) { closed = true }; runCurrent()
        assertTrue(closed); assertEquals(listOf(group), vm.data!!.aliases.groups)
    }

    @Test fun duplicateSaveIsIgnoredAndOldVaultCompletionCannotCloseANewEditor() = runTest(dispatcher) {
        val vm = vm(); runCurrent()
        saveGate = CompletableDeferred()
        var callbacks = 0
        vm.saveAliases(null, ExerciseAliasGroup("Bench", listOf("bench"))) { callbacks++ }
        runCurrent(); assertTrue(vm.aliasSaving)
        vm.saveAliases(null, ExerciseAliasGroup("Other", listOf("other"))) { callbacks++ }
        config.value = configured(OTHER_INSIGHT_VAULT); runCurrent()
        saveGate!!.complete(Unit); runCurrent()
        assertEquals(0, callbacks)
        assertEquals("Bench", aliases.getValue(INSIGHT_VAULT).value.groups.single().primaryName)
        assertTrue(vm.data!!.aliases.groups.isEmpty())
    }

    @Test fun savedRangeAndInitialLoadingArePreservedWithoutUnconfiguredFlash() = runTest(dispatcher) {
        config.value = VaultConfigState.Loading
        val saved = SavedStateHandle()
        val vm = vm(saved); runCurrent()
        assertTrue(vm.loading); assertNull(vm.data)
        config.value = configured(INSIGHT_VAULT); runCurrent()
        vm.selectRange(InsightRange.ALL)
        assertEquals(InsightRange.ALL, vm(saved).range)
    }
}
