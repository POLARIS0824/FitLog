package com.example.fitlog.log

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.example.fitlog.R
import com.example.fitlog.data.analysis.*
import com.example.fitlog.data.index.SourceIndexSnapshot
import com.example.fitlog.data.vault.VaultConfigState
import com.example.fitlog.navigation.FitLogRoute
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
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
class BatchReviewViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val ready = batchCandidate()
    private val notice = batchCandidate("2026-10-08.md", group = SetGroupCandidate("squat", reps = 8, count = 1))
    private val partial = batchCandidate("2026-10-07.md", group = SetGroupCandidate("squat", reps = 8))
    private val summaries = MutableStateFlow(listOf(batchSummary(ready), batchSummary(notice), batchSummary(partial)))
    private val sources = MutableStateFlow(SourceIndexSnapshot(listOf(ready, notice, partial).map(::batchSource), null))
    private val config = MutableStateFlow<VaultConfigState>(VaultConfigState.Configured(Uri.parse("content://vault"), BATCH_VAULT))
    private val requests = mutableListOf<DiaryConfirmation>()
    private var save: suspend (DiaryConfirmation) -> DiaryConfirmationResult = { record(it) }
    private val saved = SavedStateHandle()

    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { store.clear(); Dispatchers.resetMain() }
    private fun vm() = BatchReviewViewModel(FitLogRoute.BatchReview(BATCH_VAULT, "content://vault"), saved, sources,
        object : DiaryVaultAnalysisReader { override fun observeSummaries(vaultId: String) = summaries }, config,
        confirm = { _, request -> requests += request; save(request) }, refreshIndex = {}).also { store.put("review", it) }
    private fun record(request: DiaryConfirmation): DiaryConfirmationResult = DiaryConfirmationResult.Confirmed(
        ConfirmedDiaryRecord(ConfirmedDiaryRow("diary", BATCH_VAULT, request.sourceKey.relPath, request.date.toString(),
            ready.analysis.parseKey.contentHash, 1, request.parseRunId, 42, false), emptyList()))

    @Test fun defaultsSelectOnlyReadyAndNewCandidatesNeverJoinSelectionAutomatically() = runTest(dispatcher) {
        val vm = vm(); runCurrent()
        assertEquals(setOf(ready.attempt.id), vm.selected)
        vm.toggle(vm.items.first { it.id == notice.attempt.id })
        assertEquals(setOf(ready.attempt.id, notice.attempt.id), vm.selected)
        val new = batchCandidate("2026-10-06.md")
        sources.value = sources.value.copy(sources = sources.value.sources + batchSource(new))
        summaries.value = summaries.value + batchSummary(new); runCurrent()
        assertFalse(new.attempt.id in vm.selected)
        vm.toggleReady()
        assertTrue(new.attempt.id in vm.selected); assertTrue(notice.attempt.id in vm.selected)
        vm.toggleReady()
        assertEquals(setOf(notice.attempt.id), vm.selected)
        vm.toggle(vm.items.first { it.id == partial.attempt.id })
        assertEquals(setOf(notice.attempt.id), vm.selected)
    }

    @Test fun restoredSelectionIsRetainedAndUpdatedOrStaleCandidatesAreRemoved() = runTest(dispatcher) {
        val first = vm(); runCurrent(); first.toggle(first.items.first { it.id == notice.attempt.id })
        store.clear(); val restored = vm(); runCurrent()
        assertEquals(setOf(ready.attempt.id, notice.attempt.id), restored.selected)
        sources.value = sources.value.copy(sources = sources.value.sources.map {
            if (it.path == notice.analysis.parseKey.sourceKey.relPath) it.copy(contentHash = "b".repeat(64)) else it
        }); runCurrent()
        assertEquals(setOf(ready.attempt.id), restored.selected)
        assertEquals(R.string.batch_review_selection_changed, restored.message)
        val newer = ready.copy(attempt = ready.attempt.copy(id = "newer"))
        summaries.value = summaries.value.map { if (it.sourceKey == ready.analysis.parseKey.sourceKey) batchSummary(newer) else it }
        runCurrent(); assertTrue(restored.selected.isEmpty())
    }

    @Test fun confirmationRetainsUnknownValuesAndRequiresLatestCandidateWithoutOverwritingSavedCorrections() = runTest(dispatcher) {
        val vm = vm(); runCurrent(); vm.toggle(vm.items.first { it.id == notice.attempt.id })
        vm.submit(); advanceUntilIdle()
        assertEquals(2, requests.size)
        assertTrue(requests.all { it.requireLatestCandidate && it.expectedConfirmedAt == null })
        assertNull(requests.first { it.parseRunId == notice.attempt.id }.sessions.single().exercises.single().sets.single().unit)
        assertEquals(2, vm.progress.confirmed); assertTrue(vm.selected.isEmpty())
        val newer = ready.copy(attempt = ready.attempt.copy(id = "new"))
        val old = (record(requests.first()) as DiaryConfirmationResult.Confirmed).diary.diary
        summaries.value = listOf(batchSummary(newer).copy(confirmed = old)); runCurrent()
        assertEquals(BatchReviewEligibility.ALREADY_CONFIRMED, vm.items.single().assessment.eligibility)
        vm.toggle(vm.items.single()); vm.submit(); runCurrent()
        assertEquals(2, requests.size)
    }

    @Test fun storageFailureKeepsUnfinishedSelectionAndDoesNotUndoEarlierSuccess() = runTest(dispatcher) {
        val vm = vm(); runCurrent(); vm.toggle(vm.items.first { it.id == notice.attempt.id })
        save = { if (it.parseRunId == notice.attempt.id) throw IOException() else record(it) }
        vm.submit(); advanceUntilIdle()
        assertEquals(1, vm.progress.confirmed); assertEquals(1, vm.progress.failed)
        assertEquals(setOf(notice.attempt.id), vm.selected); assertTrue(vm.progress.stopped)
        assertFalse(vm.saving)
    }

    @Test fun conflictsSkipAndVaultChangeCancelsBeforeUnfinishedSubmissions() = runTest(dispatcher) {
        val vm = vm(); runCurrent(); vm.toggle(vm.items.first { it.id == notice.attempt.id })
        save = { if (it.parseRunId == ready.attempt.id) DiaryConfirmationResult.Invalid(ConfirmationFailure.SOURCE_CHANGED) else record(it) }
        vm.submit(); advanceUntilIdle()
        assertEquals(1, vm.progress.skipped); assertEquals(1, vm.progress.confirmed)
        val next = batchCandidate("2026-10-06.md")
        sources.value = sources.value.copy(sources = sources.value.sources + batchSource(next))
        summaries.value = summaries.value + batchSummary(next); runCurrent()
        vm.toggle(vm.items.first { it.id == next.attempt.id })
        val gate = CompletableDeferred<Unit>(); save = { gate.await(); record(it) }
        vm.submit(); runCurrent(); config.value = VaultConfigState.NotConfigured; runCurrent()
        assertFalse(vm.activeVault); assertFalse(vm.saving); assertTrue(next.attempt.id in vm.selected)
        gate.complete(Unit); runCurrent()
    }
}
