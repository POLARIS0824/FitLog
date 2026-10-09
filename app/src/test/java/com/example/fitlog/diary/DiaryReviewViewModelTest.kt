package com.example.fitlog.diary

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.example.fitlog.R
import com.example.fitlog.data.analysis.*
import com.example.fitlog.data.vault.*
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
class DiaryReviewViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val route = FitLogRoute.DiaryDetail("content://vault", "00000000-0000-4000-8000-000000000001",
        "content://note", "2026-10-08.md", "content://vault", "2026-10-08.md")
    private val candidate = candidate()
    private val parses = MutableStateFlow(DiaryParseRecords(listOf(candidate.attempt), candidate))
    private val confirmed = MutableStateFlow<ConfirmedDiaryRecord?>(null)
    private val submitted = mutableListOf<DiaryConfirmation>()
    private var modelCalls = 0
    private var modelId = 0
    private var originalReadFails = false
    private val reader = object : DiaryAnalysisReader {
        override fun observeParses(sourceKey: SourceKey) = parses
        override fun observeConfirmed(sourceKey: SourceKey) = confirmed
        override suspend fun readCandidate(sourceKey: SourceKey, parseRunId: String): StoredDiaryCandidate? {
            if (originalReadFails) throw IOException()
            return candidate.takeIf { it.analysis.parseKey.sourceKey == sourceKey && it.attempt.id == parseRunId }
        }
    }
    private val documents = object : MarkdownDocuments {
        override suspend fun read(uri: String) = MarkdownSnapshot(MarkdownFile(uri, route.fileName, route.relPath, false), "bench", "bytes", false)
        override suspend fun scan(vault: String): MarkdownScan = error("No scan")
        override suspend fun find(vault: String, name: String): MarkdownFile? = error("No lookup")
        override suspend fun create(vault: String, name: String): MarkdownFile = error("No Markdown creation")
        override suspend fun write(uri: String, text: String, bom: Boolean, expected: String): MarkdownSnapshot = error("No Markdown write")
    }
    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { store.clear(); Dispatchers.resetMain() }

    private fun vm(saved: SavedStateHandle = SavedStateHandle(), save: suspend (DiaryConfirmation) -> DiaryConfirmationResult = {
        DiaryConfirmationResult.Confirmed(record(it))
    }) = DiaryDetailViewModel(route, documents, reader, saved, {
        modelCalls++; StoredDiaryParse("unused", DiaryParseResult.Success(candidate.analysis))
    }, dispatcher, confirmDiary = { submitted += it; save(it) }).also { store.put("review-${modelId++}", it) }

    private fun candidate(): StoredDiaryCandidate {
        val key = DiaryParseInput.fromSnapshot(SourceKey(route.vaultId, route.relPath), "bench", "test").parseKey
        val evidence = EvidenceQuote(quote = "bench")
        val exercise = ExerciseCandidate("bench", evidence, listOf(SetGroupCandidate(weight = 60.0,
            unit = WeightUnit.KG, basis = WeightBasis.TOTAL, count = 3, repsList = listOf(8, 8, 7))))
        val sets = listOf(8, 8, 7).mapIndexed { index, reps -> ExpandedSet(0, index,
            CandidateValue(60.0, CandidateOrigin.EXPLICIT), CandidateValue(WeightUnit.KG, CandidateOrigin.EXPLICIT),
            CandidateValue(WeightBasis.TOTAL, CandidateOrigin.EXPLICIT), CandidateValue(reps, CandidateOrigin.EXPLICIT), CandidateOrigin.EXPLICIT, 60.0) }
        val analysis = DiaryAnalysis(key, listOf(ValidatedSession("sessions[0]", null, null,
            listOf(ValidatedExercise("sessions[0].exercises[0]", exercise, evidence, sets)))), emptyList(), emptyList())
        return StoredDiaryCandidate(DiaryParseAttempt("original-run", key, ParseRunStatus.SUCCEEDED, 1, 2, null), analysis)
    }

    private fun record(request: DiaryConfirmation): ConfirmedDiaryRecord {
        val diary = ConfirmedDiaryRow("confirmed", route.vaultId, route.relPath, request.date.toString(),
            candidate.analysis.parseKey.contentHash, 1, request.parseRunId, 100, request.acceptedPartialResult)
        val sessions = request.sessions.mapIndexed { sessionIndex, session ->
            val sessionRow = ConfirmedSessionRow("session-$sessionIndex", diary.id, sessionIndex, session.notes, session.sourcePath)
            ConfirmedSessionRecord(sessionRow, session.exercises.mapIndexed { exerciseIndex, exercise ->
                val exerciseRow = ConfirmedExerciseRow("exercise-$exerciseIndex", sessionRow.id, exerciseIndex,
                    exercise.rawName, exercise.notes, exercise.sourcePath, exercise.evidence)
                ConfirmedExerciseRecord(exerciseRow, exercise.sets.mapIndexed { index, set ->
                    ConfirmedSetRow("set-$index", exerciseRow.id, index, set.weight, set.unit, set.basis, set.reps,
                        set.weight, set.groupIndex, set.setInGroup, set.userEdited)
                })
            })
        }
        return ConfirmedDiaryRecord(diary, sessions)
    }

    @Test fun editingOneSetPreservesOthersAndDoesNotConfirmOrRewriteCandidate() = runTest(dispatcher) {
        val vm = vm(); runCurrent()
        assertFalse(vm.canEdit) // Read-only Markdown does not prevent reviewing derived data.
        vm.updateWeight(DiarySetAddress(0, 0, 1), 62.3, WeightUnit.KG)
        vm.updateReps(DiarySetAddress(0, 0, 2), 6)
        val sets = vm.review!!.sessions.single().exercises.single().sets
        assertEquals(listOf(60.0, 62.3, 60.0), sets.map { it.weight })
        assertEquals(listOf(8, 8, 6), sets.map { it.reps })
        assertEquals(candidate, parses.value.latestCandidate)
        assertTrue(submitted.isEmpty()); assertNull(vm.confirmed)
        vm.parse(); runCurrent(); assertEquals(0, modelCalls)
    }

    @Test fun unchangedPickerValueDoesNotCreateAnUnsavedReview() = runTest(dispatcher) {
        val vm = vm(); runCurrent()
        vm.updateWeight(DiarySetAddress(0, 0, 0), 60.0, WeightUnit.KG)
        vm.updateReps(DiarySetAddress(0, 0, 0), 8)
        assertNull(vm.review)
        var left = false; vm.requestLeave { left = true }; assertTrue(left)
    }

    @Test fun unitOnlyCorrectionIsSavedWithTheWeightAndDoesNotChangeOtherSetsOrTheCandidate() = runTest(dispatcher) {
        val vm = vm(); runCurrent()
        vm.updateWeight(DiarySetAddress(0, 0, 1), 60.0, WeightUnit.LB)
        val sets = vm.review!!.sessions.single().exercises.single().sets
        assertEquals(listOf(WeightUnit.KG, WeightUnit.LB, WeightUnit.KG), sets.map { it.unit })
        assertEquals(listOf(60.0, 60.0, 60.0), sets.map { it.weight })
        assertTrue(sets[1].userEdited)
        assertEquals(candidate, parses.value.latestCandidate)
        assertTrue(submitted.isEmpty())
        vm.saveReview("2026-10-08", false); runCurrent()
        val saved = submitted.single().sessions.single().exercises.single().sets[1]
        assertEquals(60.0, saved.weight!!, 0.0); assertEquals(WeightUnit.LB, saved.unit)
        assertNull(vm.review)
    }

    @Test fun revertingBothWeightAndUnitRemovesTheUnsavedReviewAndEditMarker() = runTest(dispatcher) {
        val vm = vm(); runCurrent()
        val address = DiarySetAddress(0, 0, 0)
        vm.updateWeight(address, 132.5, WeightUnit.LB)
        vm.updateWeight(address, 60.0, WeightUnit.LB)
        assertTrue(vm.review!!.sessions.single().exercises.single().sets.first().userEdited)
        vm.updateWeight(address, 60.0, WeightUnit.KG)
        assertNull(vm.review)
    }

    @Test fun unfinishedWeightAndUnitCorrectionSurvivesRestorationTogether() = runTest(dispatcher) {
        val savedState = SavedStateHandle()
        val original = vm(savedState); runCurrent()
        val address = DiarySetAddress(0, 0, 1)
        original.updateWeight(address, 137.5, WeightUnit.LB)
        val restored = vm(savedState); runCurrent()
        val set = restored.review!!.sessions.single().exercises.single().sets[1]
        assertEquals(137.5, set.weight!!, 0.0); assertEquals(WeightUnit.LB, set.unit)
        assertTrue(set.userEdited)
        restored.updateWeight(address, 60.0, WeightUnit.KG)
        assertNull(restored.review)
    }

    @Test fun conversionAcrossSeparatePickerEditsRestoresTheExactParseWithoutLosingManualPrecision() = runTest(dispatcher) {
        val vm = vm(); runCurrent()
        val address = DiarySetAddress(0, 0, 0)
        val pounds = DiaryWeightValue(60.0, WeightUnit.KG).inUnit(WeightUnit.LB)
        vm.updateWeight(address, pounds.weight, pounds.unit, pounds.converted)
        val restored = DiaryWeightValue(pounds.weight, pounds.unit).inUnit(WeightUnit.KG)
        assertNotEquals(60.0, restored.weight)
        vm.updateWeight(address, restored.weight, restored.unit, restored.converted)
        assertNull(vm.review)
        vm.updateWeight(address, restored.weight, WeightUnit.KG)
        assertEquals(restored.weight, vm.review!!.sessions.single().exercises.single().sets.first().weight)
        assertTrue(vm.review!!.sessions.single().exercises.single().sets.first().userEdited)
    }

    @Test fun cancellingConfirmationClearsUntouchedReviewButKeepsActualCorrections() = runTest(dispatcher) {
        val state = SavedStateHandle(); val vm = vm(state); runCurrent()
        assertTrue(vm.beginReview()); vm.cancelReviewConfirmation()
        assertNull(vm.review); assertNull(state.get<String>("reviewDraft"))
        vm.updateReps(DiarySetAddress(0, 0, 0), 9)
        vm.cancelReviewConfirmation()
        assertEquals(9, vm.review!!.sessions.single().exercises.single().sets.first().reps)
        assertNotNull(state.get<String>("reviewDraft"))
    }

    @Test fun revertingAllValuesToTheParseRemovesEditMarkersAndUnsavedReview() = runTest(dispatcher) {
        val state = SavedStateHandle(); val vm = vm(state); runCurrent()
        val address = DiarySetAddress(0, 0, 0)
        vm.updateWeight(address, 70.0, WeightUnit.KG)
        assertTrue(vm.review!!.sessions.single().exercises.single().sets.first().userEdited)
        vm.updateReps(address, 9)
        vm.updateWeight(address, 60.0, WeightUnit.KG)
        assertTrue(vm.review!!.sessions.single().exercises.single().sets.first().userEdited)
        vm.updateReps(address, 8)
        assertNull(vm.review); assertNull(state.get<String>("reviewDraft"))
        vm.updateWeight(address, null, WeightUnit.KG); assertNotNull(vm.review)
        vm.updateWeight(address, 60.0, WeightUnit.KG); assertNull(vm.review)
        var left = false; vm.requestLeave { left = true }; assertTrue(left)
    }

    @Test fun revertingASavedCorrectionUsesItsOriginalParseAndClearsTheMarkerBeforeSaving() = runTest(dispatcher) {
        val request = DiaryConfirmation.fromCandidate(candidate.attempt.id, candidate.analysis, java.time.LocalDate.of(2026, 10, 8))
        val prior = record(request)
        val exercise = prior.sessions.single().exercises.single()
        confirmed.value = prior.copy(sessions = listOf(prior.sessions.single().copy(exercises = listOf(
            exercise.copy(sets = exercise.sets.mapIndexed { index, set -> if (index == 0) set.copy(weight = 70.0, userEdited = true) else set })))))
        val newer = candidate.copy(attempt = candidate.attempt.copy(id = "new-run"))
        parses.value = DiaryParseRecords(listOf(newer.attempt), newer)
        val vm = vm(); runCurrent()
        vm.updateWeight(DiarySetAddress(0, 0, 0), 60.0, WeightUnit.KG)
        assertFalse(vm.review!!.sessions.single().exercises.single().sets.first().userEdited)
        assertEquals("original-run", vm.review!!.parseRunId)
        vm.saveReview("2026-10-08", false); runCurrent()
        assertFalse(submitted.single().sessions.single().exercises.single().sets.first().userEdited)
        assertFalse(vm.confirmed!!.sessions.single().exercises.single().sets.first().userEdited)
    }

    @Test fun originalParseReadFailureKeepsConfirmationAndAllowsManualRetry() = runTest(dispatcher) {
        confirmed.value = record(DiaryConfirmation.fromCandidate(candidate.attempt.id, candidate.analysis, java.time.LocalDate.of(2026, 10, 8)))
        originalReadFails = true
        val vm = vm(); runCurrent()
        assertNotNull(vm.confirmed); assertTrue(vm.confirmationReadFailed); assertFalse(vm.canReview)
        assertEquals(0, modelCalls)
        originalReadFails = false; vm.retryAnalysis(); runCurrent()
        assertFalse(vm.confirmationReadFailed); assertTrue(vm.canReview); assertEquals(0, modelCalls)
    }

    @Test fun explicitSavePersistsReviewedValuesAndClearsSavedDraft() = runTest(dispatcher) {
        val state = SavedStateHandle(); val vm = vm(state); runCurrent()
        vm.updateWeight(DiarySetAddress(0, 0, 1), 62.3, WeightUnit.KG)
        vm.saveReview("2026-10-08", false); runCurrent()
        assertEquals(62.3, submitted.single().sessions.single().exercises.single().sets[1].weight!!, 0.0)
        assertEquals("original-run", submitted.single().parseRunId)
        assertNull(vm.review); assertNull(state.get<String>("reviewDraft"))
        assertEquals(R.string.detail_review_saved, vm.reviewMessage)
        assertEquals(62.3, vm.confirmed!!.sessions.single().exercises.single().sets[1].weight!!, 0.0)
    }

    @Test fun bodyweightIsSavedAndReopenedIndependentlyOfNumericWeightAndUnit() = runTest(dispatcher) {
        val vm = vm(); runCurrent()
        val address = DiarySetAddress(0, 0, 0)
        vm.updateWeight(address, null, null, basis = WeightBasis.BODYWEIGHT)
        val set = vm.review!!.sessions.single().exercises.single().sets.first()
        assertNull(set.weight); assertNull(set.unit)
        assertEquals(WeightBasis.BODYWEIGHT, set.basis); assertTrue(set.userEdited)
        vm.saveReview("2026-10-08", false); runCurrent()
        val saved = vm.confirmed!!.sessions.single().exercises.single().sets.first()
        assertNull(saved.weight); assertNull(saved.unit); assertEquals(WeightBasis.BODYWEIGHT, saved.basis)
        confirmed.value = vm.confirmed
        val reopened = vm(); runCurrent()
        assertTrue(reopened.beginReview())
        val restored = reopened.review!!.sessions.single().exercises.single().sets.first()
        assertEquals(set, restored)
        reopened.updateWeight(address, 0.0, WeightUnit.KG, basis = WeightBasis.UNKNOWN)
        val numeric = reopened.review!!.sessions.single().exercises.single().sets.first()
        assertEquals(0.0, numeric.weight!!, 0.0); assertEquals(WeightBasis.UNKNOWN, numeric.basis)
        assertEquals(60.0, candidate.analysis.sessions.single().exercises.single().sets.first().weight.value!!, 0.0)
    }

    @Test fun restoredDraftRetainsBodyweightAndBasisOnlyChangesCountAsEdits() = runTest(dispatcher) {
        val state = SavedStateHandle(); val original = vm(state); runCurrent()
        val address = DiarySetAddress(0, 0, 0)
        original.updateWeight(address, null, null, basis = WeightBasis.BODYWEIGHT)
        val restored = vm(SavedStateHandle(mapOf("reviewDraft" to state.get<String>("reviewDraft"))))
        runCurrent()
        assertEquals(WeightBasis.BODYWEIGHT, restored.review!!.sessions.single().exercises.single().sets.first().basis)
        restored.updateWeight(address, null, null, basis = WeightBasis.UNKNOWN)
        assertEquals(WeightBasis.UNKNOWN, restored.review!!.sessions.single().exercises.single().sets.first().basis)
        assertTrue(restored.review!!.sessions.single().exercises.single().sets.first().userEdited)
        restored.updateWeight(address, 60.0, WeightUnit.KG, basis = WeightBasis.TOTAL)
        assertNull(restored.review)
    }

    @Test fun defaultKgBelongsOnlyToTheReviewAndIsPersistedOnExplicitConfirmation() = runTest(dispatcher) {
        val session = candidate.analysis.sessions.single()
        val exercise = session.exercises.single()
        val unspecified = exercise.copy(sets = exercise.sets.map { it.copy(unit = CandidateValue(WeightUnit.UNKNOWN, CandidateOrigin.MISSING)) })
        val unknownCandidate = candidate.copy(analysis = candidate.analysis.copy(sessions = listOf(session.copy(exercises = listOf(unspecified)))))
        parses.value = DiaryParseRecords(listOf(unknownCandidate.attempt), unknownCandidate)
        val vm = vm(); runCurrent()
        assertNull(vm.review); assertTrue(submitted.isEmpty())
        assertTrue(vm.beginReview())
        val sets = vm.review!!.sessions.single().exercises.single().sets
        assertTrue(sets.all { it.unit == WeightUnit.KG && it.userEdited })
        assertTrue(unknownCandidate.analysis.sessions.single().exercises.single().sets.all { it.unit.value == WeightUnit.UNKNOWN })
        vm.saveReview("2026-10-08", false); runCurrent()
        assertTrue(submitted.single().sessions.single().exercises.single().sets.all { it.unit == WeightUnit.KG })
    }

    @Test fun failedSaveRetainsInputsAndManualRetryPreventsDuplicateWrites() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>(); var calls = 0
        val vm = vm(save = { calls++; if (calls == 1) { gate.await(); throw IOException() }
            DiaryConfirmationResult.Confirmed(record(it)) }); runCurrent()
        vm.updateReps(DiarySetAddress(0, 0, 0), 9)
        vm.saveReview("2026-10-08", false); vm.saveReview("2026-10-08", false); runCurrent()
        assertEquals(1, calls); assertTrue(vm.reviewSaving)
        gate.complete(Unit); runCurrent()
        assertEquals(9, vm.review!!.sessions.single().exercises.single().sets.first().reps)
        assertEquals(R.string.detail_review_save_failed, vm.reviewMessage)
        vm.saveReview("2026-10-08", false); runCurrent()
        assertEquals(2, calls); assertNull(vm.review)
    }

    @Test fun leavingRequiresExplicitDiscardAndCancelKeepsReview() = runTest(dispatcher) {
        val vm = vm(); runCurrent(); vm.updateReps(DiarySetAddress(0, 0, 0), 9)
        var left = false; vm.requestLeave { left = true }
        assertTrue(vm.leaveRequested); assertFalse(left)
        vm.cancelLeave(); assertNotNull(vm.review); assertFalse(left)
        vm.requestLeave { left = true }; vm.discardAndLeave()
        assertTrue(left); assertNull(vm.review)
    }

    @Test fun restoredCorrectionsStayBoundToTheirParseWhenANewerCandidateArrives() = runTest(dispatcher) {
        val state = SavedStateHandle(); val original = vm(state); runCurrent()
        original.updateWeight(DiarySetAddress(0, 0, 1), 62.3, WeightUnit.KG)
        val restoredState = SavedStateHandle(mapOf("reviewDraft" to state.get<String>("reviewDraft")))
        val newer = candidate.copy(attempt = candidate.attempt.copy(id = "new-run"))
        parses.value = DiaryParseRecords(listOf(newer.attempt), newer)
        val restored = vm(restoredState); runCurrent()
        assertEquals("original-run", restored.review!!.parseRunId)
        restored.saveReview("2026-10-08", false); runCurrent()
        assertEquals("original-run", submitted.single().parseRunId)
        assertEquals(62.3, submitted.single().sessions.single().exercises.single().sets[1].weight!!, 0.0)
    }

    @Test fun editingConfirmedResultKeepsEarlierUserCorrectionsAndOldParseIdentity() = runTest(dispatcher) {
        val request = DiaryConfirmation.fromCandidate(candidate.attempt.id, candidate.analysis, java.time.LocalDate.of(2026, 10, 8))
        val prior = record(request)
        val exercise = prior.sessions.single().exercises.single()
        confirmed.value = prior.copy(sessions = listOf(prior.sessions.single().copy(exercises = listOf(
            exercise.copy(sets = exercise.sets.mapIndexed { index, set -> if (index == 0) set.copy(weight = 70.0, userEdited = true) else set })))))
        parses.value = DiaryParseRecords(listOf(candidate.attempt.copy(id = "new-run")), candidate.copy(attempt = candidate.attempt.copy(id = "new-run")))
        val vm = vm(); runCurrent(); vm.updateReps(DiarySetAddress(0, 0, 1), 9)
        assertEquals(70.0, vm.review!!.sessions.single().exercises.single().sets.first().weight!!, 0.0)
        vm.saveReview("2026-10-08", false); runCurrent()
        assertEquals("original-run", submitted.single().parseRunId)
        assertEquals(70.0, submitted.single().sessions.single().exercises.single().sets.first().weight!!, 0.0)
    }

    @Test fun missingCountsRequireExplicitPartialAcceptanceAndNeverCreateSets() = runTest(dispatcher) {
        val originalExercise = candidate.analysis.sessions.single().exercises.single()
        val partial = candidate.copy(analysis = candidate.analysis.copy(sessions = listOf(candidate.analysis.sessions.single().copy(
            exercises = listOf(originalExercise.copy(candidate = originalExercise.candidate.copy(groups = listOf(
                SetGroupCandidate(weight = 38.0, unit = WeightUnit.KG, reps = 8))), sets = emptyList()))))))
        parses.value = DiaryParseRecords(listOf(partial.attempt), partial)
        val vm = vm(); runCurrent(); vm.saveReview("2026-10-08", false); runCurrent()
        assertEquals(R.string.detail_review_partial_required, vm.reviewMessage)
        assertTrue(vm.review!!.sessions.single().exercises.single().sets.isEmpty())
        assertEquals(38.0, vm.review!!.fragments.single().weight!!, 0.0)
        assertTrue(submitted.isEmpty())
        vm.saveReview("2026-10-08", true); runCurrent(); assertTrue(submitted.single().acceptedPartialResult)
    }

    @Test fun invalidDateAndExternalConfirmationChangesRetainReviewAndPreventReplacement() = runTest(dispatcher) {
        val vm = vm(); runCurrent(); vm.updateReps(DiarySetAddress(0, 0, 0), 9)
        vm.saveReview("2026-02-30", false); runCurrent()
        assertEquals(R.string.detail_review_date_invalid, vm.reviewMessage); assertTrue(submitted.isEmpty())
        confirmed.value = record(DiaryConfirmation.fromCandidate(candidate.attempt.id, candidate.analysis, java.time.LocalDate.of(2026, 10, 8)))
        runCurrent(); vm.saveReview("2026-10-08", false); runCurrent()
        assertEquals(R.string.detail_review_changed, vm.reviewMessage); assertNotNull(vm.review); assertTrue(submitted.isEmpty())
    }

    @Test fun unknownAndZeroWeightsStayDistinctAndInvalidValuesAreRejected() = runTest(dispatcher) {
        val vm = vm(); runCurrent()
        vm.updateWeight(DiarySetAddress(0, 0, 0), 0.0, WeightUnit.KG)
        vm.updateWeight(DiarySetAddress(0, 0, 1), null, WeightUnit.KG)
        vm.updateWeight(DiarySetAddress(0, 0, 2), Double.NaN, WeightUnit.KG)
        vm.updateReps(DiarySetAddress(0, 0, 2), 0)
        val sets = vm.review!!.sessions.single().exercises.single().sets
        assertEquals(0.0, sets.first().weight!!, 0.0); assertNull(sets[1].weight)
        assertEquals(60.0, sets[2].weight!!, 0.0); assertEquals(7, sets[2].reps)
    }

    @Test fun malformedSavedDraftReportsFailureWithoutCrashingOrReplacingConfirmedData() = runTest(dispatcher) {
        val vm = vm(SavedStateHandle(mapOf("reviewDraft" to "broken json"))); runCurrent()
        assertEquals(R.string.detail_review_restore_failed, vm.reviewMessage); assertFalse(vm.canReview)
        assertTrue(submitted.isEmpty())
    }

    @Test fun pickerShortcutsPreserveExactOutOfRangeValuesAndDoNotInventUnknownDefaults() {
        assertTrue(weightOptions(62.3).contains(62.3)); assertTrue(weightOptions(1100.1).contains(1100.1))
        assertNull(weightOptions(null).first()); assertNull(repOptions(null).first()); assertTrue(repOptions(101).contains(101))
        assertEquals(62.3, enteredWeight("62,3")!!, 0.0)
        assertNull(enteredWeight("Infinity")); assertNull(enteredWeight("-1")); assertNull(enteredReps("0"))
        assertNull(reviewDate("2026-02-30")); assertNull(reviewDate("0000-01-01"))
    }
}
