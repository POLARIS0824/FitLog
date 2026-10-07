package com.example.fitlog.data.analysis

import androidx.room.Room
import com.example.fitlog.data.analysis.adapter.*
import com.example.fitlog.data.index.SourceIndexDatabase
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DiaryAnalysisPersistenceTest {
    private lateinit var db: DiaryAnalysisDatabase
    private lateinit var repo: DiaryAnalysisRepository
    private lateinit var parser: JsonDiaryParser
    private var response = """{"schemaVersion":1,"sessions":[]}"""
    private val date = LocalDate.of(2026, 10, 2)

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), DiaryAnalysisDatabase::class.java).build()
        repo = DiaryAnalysisRepository(db)
        parser = JsonDiaryParser(DiaryModelSource { DiaryModelResponse.Json(response) })
    }

    @After fun tearDown() = runBlocking {
        db.close()
    }

    private suspend fun parsed(input: DiaryParseInput = fullInput("")): Pair<StoredDiaryParse, DiaryAnalysis> {
        val result = repo.parse(input, parser)
        return result to (result.result as DiaryParseResult.Success).analysis
    }

    private fun DiaryConfirmationResult.record() = (this as DiaryConfirmationResult.Confirmed).diary

    @Test fun emptyExtractionCreatesAConfirmationUnitWithoutInventingARestDay() = runBlocking {
        val (run, analysis) = parsed()
        val record = repo.confirm(DiaryConfirmation.fromCandidate(run.parseRunId, analysis, date)).record()
        assertTrue(record.sessions.isEmpty())
        assertEquals(date.toString(), record.diary.date)
        assertFalse(record.diary.acceptedPartialResult)
        assertEquals(ConfirmationFreshness.CONFIRMED,
            confirmationFreshness(record.diary, DiaryContentVersion.from(fullInput(""))))
    }

    @Test fun multipleSessionsAndExerciseOnlyActivitiesKeepTheirOrderAndNotes() = runBlocking {
        val input = fullInput("bench\nabs")
        response = """{"schemaVersion":1,"sessions":[{"date":"2026-10-02","notes":"first","exercises":[{"rawName":"bench","evidence":{"segmentId":"diary","quote":"bench"}}]},{"date":"2026-10-02","notes":"second","exercises":[{"rawName":"abs","evidence":{"segmentId":"diary","quote":"abs"}}]}]}"""
        val (run, analysis) = parsed(input)
        val record = repo.confirm(DiaryConfirmation.fromCandidate(run.parseRunId, analysis, date)).record()
        assertEquals(listOf(0, 1), record.sessions.map { it.session.position })
        assertEquals(listOf("first", "second"), record.sessions.map { it.session.notes })
        assertEquals(listOf("bench", "abs"), record.sessions.flatMap { it.exercises }.map { it.exercise.rawName })
        assertTrue(record.sessions.flatMap { it.exercises }.all { it.sets.isEmpty() })
    }

    @Test fun confirmationDerivesGroupEditFlagsAndRecomputesEditedWeights() = runBlocking {
        val quote = "bench 40kg 1x8 + 1x6"
        response = candidateJson(exercise(quote,
            SetGroupCandidate("40kg 1x8", 40.0, WeightUnit.KG, WeightBasis.PER_SIDE,
                reps = 8, count = 1, inferredFields = listOf("weight")),
            SetGroupCandidate("1x6", reps = 6, count = 1)))
        val (run, analysis) = parsed(fullInput(quote))
        val review = DiaryConfirmation.fromCandidate(run.parseRunId, analysis, date)
        val session = review.sessions.single()
        val exercise = session.exercises.single()
        val first = exercise.sets.first()
        val changed = first.copy(weight = 50.0, unit = WeightUnit.LB)
        val manual = ReviewedSet(weight = 10.0, unit = WeightUnit.KG, basis = WeightBasis.TOTAL)
        val submitted = review.copy(sessions = listOf(session.copy(exercises = listOf(
            exercise.copy(sets = listOf(changed, exercise.sets.last().copy(userEdited = true), manual))))))
        val sets = repo.confirm(submitted).record().sessions.single().exercises.single().sets
        assertEquals(50.0 * 0.45359237, sets.first().weightKg!!, 0.000001)
        assertEquals(WeightBasis.PER_SIDE, sets.first().basis)
        assertTrue(sets.first().userEdited)
        assertFalse(sets[1].userEdited)
        assertNull(sets[1].weightKg)
        assertTrue(sets.last().userEdited)
        assertNull(sets.last().reps)
        assertEquals(CandidateOrigin.MISSING, analysis.sessions.single().exercises.single().sets[1].weight.origin)
    }

    @Test fun missingValuesRemainNullAndDoNotReuseCandidateUnitConversions() = runBlocking {
        response = candidateJson(exercise("bench 40kg 1x8", SetGroupCandidate("40kg 1x8",
            40.0, WeightUnit.KG, WeightBasis.TOTAL, reps = 8, count = 1)))
        val (run, analysis) = parsed(fullInput("bench 40kg 1x8"))
        val review = DiaryConfirmation.fromCandidate(run.parseRunId, analysis, date)
        val session = review.sessions.single()
        val exercise = session.exercises.single()
        val set = exercise.sets.single().copy(weight = null, unit = null, reps = null)
        val record = repo.confirm(review.copy(sessions = listOf(session.copy(exercises = listOf(
            exercise.copy(sets = listOf(set))))))).record()
        val stored = record.sessions.single().exercises.single().sets.single()
        assertNull(stored.weight); assertNull(stored.unit); assertNull(stored.reps); assertNull(stored.weightKg)
        assertTrue(stored.userEdited)
    }

    @Test fun replacingConfirmationKeepsDiaryIdWithoutHistory() = runBlocking {
        response = fixture("user-sample.expected.json")
        val (run, analysis) = parsed(fullInput(fixture("user-sample.md")))
        val review = DiaryConfirmation.fromCandidate(run.parseRunId, analysis, date)
        val first = repo.confirm(review).record()
        val second = repo.confirm(review.copy(sessions = emptyList(), date = date.minusDays(1))).record()
        assertEquals(first.diary.id, second.diary.id)
        assertTrue(second.sessions.isEmpty())
        db.openHelper.readableDatabase.query("SELECT name FROM sqlite_master WHERE name = 'confirmation_snapshot'").use {
            assertEquals(0, it.count)
        }
        assertNotNull(db.analysis().run(first.diary.parseRunId))
    }

    @Test fun reparsingCannotOverwriteUserCorrectionsUntilAnotherExplicitConfirmation() = runBlocking {
        response = candidateJson(exercise("bench 40kg 1x8", SetGroupCandidate("40kg 1x8",
            40.0, WeightUnit.KG, WeightBasis.TOTAL, reps = 8, count = 1)))
        val (oldRun, oldAnalysis) = parsed(fullInput("bench 40kg 1x8"))
        val originalReview = DiaryConfirmation.fromCandidate(oldRun.parseRunId, oldAnalysis, date)
        val session = originalReview.sessions.single()
        val exercise = session.exercises.single()
        val correctedSet = exercise.sets.single().copy(weight = 42.0)
        val corrected = repo.confirm(originalReview.copy(sessions = listOf(session.copy(exercises = listOf(
            exercise.copy(sets = listOf(correctedSet))))))).record()
        response = candidateJson(exercise("bench 50kg 1x8", SetGroupCandidate("50kg 1x8",
            50.0, WeightUnit.KG, WeightBasis.TOTAL, reps = 8, count = 1)))
        val (newRun, newAnalysis) = parsed(fullInput("bench 50kg 1x8"))
        assertEquals(corrected, repo.confirmed(originalReview.sourceKey))
        val second = repo.confirm(DiaryConfirmation.fromCandidate(newRun.parseRunId, newAnalysis, date)).record()
        assertEquals(50.0, second.sessions.single().exercises.single().sets.single().weight!!, 0.0)
        assertNotNull(db.analysis().run(oldRun.parseRunId))
        assertNotNull(db.analysis().run(newRun.parseRunId))
    }

    @Test fun movingExercisesPreservesUnchangedValuesAndRejectsDuplicateReferences() = runBlocking {
        response = candidateJson(exercise("bench 40kg 1x8", SetGroupCandidate("40kg 1x8",
            40.0, WeightUnit.KG, WeightBasis.TOTAL, reps = 8, count = 1)))
        val (run, analysis) = parsed(fullInput("bench 40kg 1x8"))
        val originalReview = DiaryConfirmation.fromCandidate(run.parseRunId, analysis, date)
        val exercise = originalReview.sessions.single().exercises.single()
        val manualSession = ReviewedSession(listOf(exercise), notes = "reorganized by user")
        val moved = repo.confirm(originalReview.copy(sessions = listOf(manualSession))).record()
        assertNull(moved.sessions.single().session.sourcePath)
        assertFalse(moved.sessions.single().exercises.single().sets.single().userEdited)
        assertEquals(DiaryConfirmationResult.Invalid(ConfirmationFailure.INVALID_DATA), repo.confirm(
            originalReview.copy(sessions = listOf(manualSession, manualSession))))
        assertEquals(moved, repo.confirmed(originalReview.sourceKey))
    }

    @Test fun partialExtractionNeedsExplicitAcceptanceAndPreservesThatFact() = runBlocking {
        response = """{"schemaVersion":1,"sessions":[{"exercises":[{"rawName":""}]}]}"""
        val (run, analysis) = parsed()
        assertTrue(analysis.hasErrors)
        val review = DiaryConfirmation.fromCandidate(run.parseRunId, analysis, date)
        assertEquals(DiaryConfirmationResult.Invalid(ConfirmationFailure.PARTIAL_RESULT_NOT_ACCEPTED), repo.confirm(review))
        assertNull(repo.confirmed(review.sourceKey))
        val confirmed = repo.confirm(review.copy(acceptedPartialResult = true)).record()
        assertTrue(confirmed.diary.acceptedPartialResult)
        assertTrue(confirmed.sessions.single().exercises.isEmpty())
    }

    @Test fun optionalAndWrongSourceExcerptsNeverPreventConfirmationOfTraining() = runBlocking {
        response = """{"schemaVersion":1,"sessions":[{"exercises":[
            {"rawName":"bench","groups":[{"weight":40,"unit":"KG","count":1,"reps":8}]},
            {"rawName":"abs","evidence":{"segmentId":"unknown","quote":"model description"}}
        ]}]}"""
        val (run, analysis) = parsed(fullInput("bench 40kg 1x8\nabs"))
        assertFalse(analysis.hasErrors)
        val review = DiaryConfirmation.fromCandidate(run.parseRunId, analysis, date)
        assertTrue(review.sessions.single().exercises.all { it.evidence == null })
        val stored = repo.confirm(review).record().sessions.single().exercises
        assertTrue(stored.all { it.exercise.evidence == null })
        assertEquals(40.0, stored.first().sets.single().weightKg!!, 0.0)
    }

    @Test fun deviceResponsePersistsAllSetsAndRawJsonWithoutRequiringPartialAcceptance() = runBlocking {
        response = fixture("device-2026-09-29.response.json")
        val input = fullInput(fixture("device-2026-09-29.md"))
        val (run, analysis) = parsed(input)
        assertFalse(analysis.hasErrors)
        assertEquals(response, db.analysis().run(run.parseRunId)!!.rawModelJson)
        assertEquals(analysis, repo.readParses(input.parseKey.sourceKey).latestCandidate!!.analysis)
        val record = repo.confirm(DiaryConfirmation.fromCandidate(run.parseRunId, analysis, date)).record()
        assertFalse(record.diary.acceptedPartialResult)
        assertEquals(listOf(5, 4, 3), record.sessions.single().exercises.map { it.sets.size })
    }

    @Test fun failedOrDifferentSourceAttemptsCannotBeConfirmed() = runBlocking {
        val input = fullInput("")
        response = "not json"
        val failed = repo.parse(input, parser)
        val request = DiaryConfirmation(input.parseKey.sourceKey, failed.parseRunId, date, emptyList())
        assertEquals(DiaryConfirmationResult.Invalid(ConfirmationFailure.INVALID_PARSE_RUN), repo.confirm(request))
        response = """{"schemaVersion":1,"sessions":[]}"""
        val (run, _) = parsed()
        assertEquals(DiaryConfirmationResult.Invalid(ConfirmationFailure.SOURCE_MISMATCH), repo.confirm(request.copy(
            parseRunId = run.parseRunId, sourceKey = input.parseKey.sourceKey.copy(relPath = "other.md"))))
    }

    @Test fun invalidFinalValuesAndForgedCandidateCoordinatesAreRejected() = runBlocking {
        val (run, analysis) = parsed()
        val base = DiaryConfirmation.fromCandidate(run.parseRunId, analysis, date)
        listOf(
            ReviewedSet(weight = Double.NaN),
            ReviewedSet(weight = -1.0),
            ReviewedSet(reps = 0),
            ReviewedSet(groupIndex = 0, setInGroup = 0),
        ).forEach { set ->
            val request = base.copy(sessions = listOf(ReviewedSession(listOf(ReviewedExercise("manual", listOf(set))))))
            assertEquals(DiaryConfirmationResult.Invalid(ConfirmationFailure.INVALID_DATA), repo.confirm(request))
        }
        assertNull(repo.confirmed(base.sourceKey))
    }

    @Test fun transactionFailureAfterDeletionRollsEverythingBack() = runBlocking {
        response = candidateJson(exercise("bench", name = "bench"))
        val (run, analysis) = parsed(fullInput("bench"))
        val review = DiaryConfirmation.fromCandidate(run.parseRunId, analysis, date)
        val old = repo.confirm(review).record()
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_exercise BEFORE INSERT ON confirmed_exercise BEGIN SELECT RAISE(ABORT, 'test failure'); END")
        try { repo.confirm(review); fail() } catch (_: DiaryAnalysisStorageException) { }
        assertEquals(old, repo.confirmed(review.sourceKey))
    }

    @Test fun changedOrUnavailableContentDoesNotDiscardReviewOrRelabelItsHash() = runBlocking {
        val input = fullInput("old text")
        val (run, analysis) = parsed(input)
        val review = DiaryConfirmation.fromCandidate(run.parseRunId, analysis, date)
        val stored = repo.confirm(review).record().diary
        assertEquals(input.parseKey.contentHash, stored.contentHash)
        assertEquals(ConfirmationFreshness.NEEDS_UPDATE, confirmationFreshness(stored,
            DiaryContentVersion.from(fullInput("new text"))))
        assertEquals(ConfirmationFreshness.UNVERIFIABLE, confirmationFreshness(stored, null))
        assertEquals(ConfirmationFreshness.UNVERIFIABLE, confirmationFreshness(stored,
            DiaryContentVersion(stored.contentHash, stored.hashVersion + 1)))
        assertEquals(ConfirmationFreshness.UNCONFIRMED, confirmationFreshness(null, null))
        assertEquals(ConfirmationFreshness.CONFIRMED, confirmationFreshness(stored,
            DiaryContentVersion.from(fullInput("\uFEFFold text"))))
    }

    @Test fun sourceUniquenessForeignKeysAndPositionConstraintsAreEnforced() = runBlocking {
        val (run, analysis) = parsed()
        val record = repo.confirm(DiaryConfirmation.fromCandidate(run.parseRunId, analysis, date)).record()
        try { db.analysis().insertDiary(record.diary.copy(id = UUID.randomUUID().toString())); fail() } catch (_: Exception) { }
        try { db.analysis().insertSession(ConfirmedSessionRow("orphan", "missing", 0, null, null)); fail() } catch (_: Exception) { }
        db.analysis().insertSession(ConfirmedSessionRow("first", record.diary.id, 0, null, null))
        try { db.analysis().insertSession(ConfirmedSessionRow("second", record.diary.id, 0, null, null)); fail() } catch (_: Exception) { }
        val sql = db.openHelper.writableDatabase
        try { sql.execSQL("DELETE FROM parse_run WHERE id = ?", arrayOf(run.parseRunId)); fail() } catch (_: Exception) { }
        assertNotNull(db.analysis().run(run.parseRunId))
    }

    @Test fun differentFilesAndVaultsRemainDistinctEvenOnTheSameDate() = runBlocking {
        val a = fullInput("")
        val b = DiaryParseInput.fromSnapshot(a.parseKey.sourceKey.copy(relPath = "other.md"), "", "fixture-v1")
        val c = DiaryParseInput.fromSnapshot(a.parseKey.sourceKey.copy(vaultId = UUID.randomUUID().toString()), "", "fixture-v1")
        val records = listOf(a, b, c).map { input ->
            val (run, analysis) = parsed(input)
            repo.confirm(DiaryConfirmation.fromCandidate(run.parseRunId, analysis, date)).record()
        }
        assertEquals(3, records.map { it.diary.id }.distinct().size)
        assertNotNull(repo.confirmed(a.parseKey.sourceKey))
        assertNotNull(repo.confirmed(c.parseKey.sourceKey))
    }

    @Test fun confirmationSurvivesReopenAndSourceIndexRecreation() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val name = "analysis-${UUID.randomUUID()}.db"
        val indexName = "index-${UUID.randomUUID()}.db"
        fun open() = Room.databaseBuilder(context, DiaryAnalysisDatabase::class.java, name).build()
        val disk = open()
        val diskRepo = DiaryAnalysisRepository(disk)
        val diskParser = JsonDiaryParser(DiaryModelSource { DiaryModelResponse.Json(fixture("user-sample.expected.json")) })
        val input = fullInput(fixture("user-sample.md"))
        var expected: ConfirmedDiaryRecord? = null
        try {
            val run = diskRepo.parse(input, diskParser)
            val review = DiaryConfirmation.fromCandidate(run.parseRunId, (run.result as DiaryParseResult.Success).analysis, date)
            diskRepo.confirm(review)
            expected = diskRepo.confirm(review).record()
        } finally { disk.close() }
        val index = Room.databaseBuilder(context, SourceIndexDatabase::class.java, indexName).build()
        index.openHelper.writableDatabase
        index.close()
        context.deleteDatabase(indexName)
        val recreatedIndex = Room.databaseBuilder(context, SourceIndexDatabase::class.java, indexName).build()
        recreatedIndex.openHelper.writableDatabase
        recreatedIndex.close()
        val reopened = open()
        try {
            val reopenedRepo = DiaryAnalysisRepository(reopened)
            assertEquals(expected, reopenedRepo.confirmed(input.parseKey.sourceKey))
            val run = reopened.analysis().run(expected.diary.parseRunId)!!
            assertEquals(fixture("user-sample.expected.json"), run.rawModelJson)
            assertEquals(14, reopenedRepo.confirmed(input.parseKey.sourceKey)!!.sessions.flatMap { it.exercises }.sumOf { it.sets.size })
        } finally { reopened.close(); context.deleteDatabase(name); context.deleteDatabase(indexName) }
    }
}
