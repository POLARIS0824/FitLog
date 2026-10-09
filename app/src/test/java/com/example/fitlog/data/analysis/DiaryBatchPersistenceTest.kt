package com.example.fitlog.data.analysis

import androidx.room.Room
import com.example.fitlog.data.analysis.adapter.*
import com.example.fitlog.data.vault.*
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DiaryBatchPersistenceTest {
    private lateinit var db: DiaryAnalysisDatabase
    private lateinit var repo: DiaryAnalysisRepository
    private val key = SourceKey(BATCH_VAULT, "2026-10-09.md")
    private val input = DiaryParseInput.fromSnapshot(key, "squat", "test")
    private val date = LocalDate.of(2026, 10, 9)
    private val answer = """{"schemaVersion":1,"sessions":[{"date":"2026-10-09","exercises":[{"rawName":"squat","evidence":{"quote":"squat"},"groups":[{"weight":40,"unit":"KG","basis":"TOTAL","count":1,"reps":8}]}]}]}"""
    @Before fun before() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), DiaryAnalysisDatabase::class.java).build()
        repo = DiaryAnalysisRepository(db, now = { 42 })
    }
    @After fun after() { db.close() }
    private suspend fun parsed(text: String = answer): StoredDiaryParse = repo.parse(input,
        JsonDiaryParser(DiaryModelSource { DiaryModelResponse.Json(text) }))
    private fun request(run: StoredDiaryParse) = DiaryConfirmation.fromCandidate(run.parseRunId,
        (run.result as DiaryParseResult.Success).analysis, date)
    private fun record(result: DiaryConfirmationResult) = (result as DiaryConfirmationResult.Confirmed).diary

    @Test fun vaultSummaryKeepsLatestFailureLatestSuccessAndIndependentConfirmation() = runBlocking {
        parsed(); val success = parsed(); val confirmed = record(repo.confirm(request(success)))
        val failure = parsed("not json")
        val summary = repo.observeSummaries(BATCH_VAULT).first().single()
        assertEquals(failure.parseRunId, summary.latestAttempt!!.id)
        assertEquals(success.parseRunId, summary.candidate!!.attempt.id)
        assertEquals(confirmed.diary, summary.confirmed)
        assertTrue(repo.observeSummaries("00000000-0000-4000-8000-000000000002").first().isEmpty())
        db.openHelper.writableDatabase.execSQL("UPDATE parse_run SET candidateJson = 'broken' WHERE id = ?", arrayOf(success.parseRunId))
        val damaged = repo.observeSummaries(BATCH_VAULT).first().single()
        assertTrue(damaged.candidateReadFailed); assertNull(damaged.candidate)
        assertEquals(confirmed.diary, damaged.confirmed)
    }

    @Test fun transactionRejectsStaleConfirmationVersionAndTimeTokenAlwaysIncreases() = runBlocking {
        val run = parsed(); val request = request(run)
        val first = record(repo.confirm(request))
        assertEquals(DiaryConfirmationResult.Invalid(ConfirmationFailure.CONFIRMATION_CHANGED), repo.confirm(request))
        val second = record(repo.confirm(request.copy(expectedConfirmedAt = first.diary.confirmedAt)))
        assertEquals(43L, second.diary.confirmedAt)
        assertEquals(DiaryConfirmationResult.Invalid(ConfirmationFailure.CONFIRMATION_CHANGED),
            repo.confirm(request.copy(expectedConfirmedAt = first.diary.confirmedAt)))
        assertEquals(second, repo.confirmed(key))
    }

    @Test fun batchRequiresLatestWhileSingleReviewCanKeepItsExplicitImmutableCandidate() = runBlocking {
        val old = parsed(); val newer = parsed()
        assertEquals(DiaryConfirmationResult.Invalid(ConfirmationFailure.CANDIDATE_CHANGED),
            repo.confirm(request(old).copy(requireLatestCandidate = true)))
        assertNull(repo.confirmed(key))
        assertEquals(old.parseRunId, record(repo.confirm(request(old))).diary.parseRunId)
        assertEquals(newer.parseRunId, repo.readSummary(key).candidate!!.attempt.id)
    }

    @Test fun sourceChangeOrReadFailureCannotConfirmAndNormalizedLineEndingsCan() = runBlocking {
        val run = parsed(); val request = request(run)
        val documents = Documents("changed")
        assertEquals(DiaryConfirmationResult.Invalid(ConfirmationFailure.SOURCE_CHANGED),
            confirmCurrentDiary(request, "content://diary", documents, repo))
        assertNull(repo.confirmed(key))
        documents.unavailable = true
        assertEquals(DiaryConfirmationResult.Invalid(ConfirmationFailure.SOURCE_UNAVAILABLE),
            confirmCurrentDiary(request, "content://diary", documents, repo))
        documents.unavailable = false; documents.text = "\uFEFFsquat"
        assertTrue(confirmCurrentDiary(request, "content://diary", documents, repo) is DiaryConfirmationResult.Confirmed)
    }

    @Test fun dateConflictAndUnexpandedGroupsCannotUseBatchConfirmation() = runBlocking {
        val conflict = parsed(answer.replace("2026-10-09", "2026-10-08"))
        assertEquals(DiaryConfirmationResult.Invalid(ConfirmationFailure.INVALID_DATA), confirmCurrentDiary(
            request(conflict).copy(requireLatestCandidate = true), "content://diary", Documents("squat"), repo))
        val fragment = parsed(answer.replace(",\"count\":1", ""))
        assertEquals(DiaryConfirmationResult.Invalid(ConfirmationFailure.PARTIAL_RESULT_NOT_ACCEPTED), repo.confirm(request(fragment)))
        assertNull(repo.confirmed(key))
    }

    private class Documents(var text: String) : MarkdownDocuments {
        var unavailable = false
        override suspend fun read(uri: String): MarkdownSnapshot {
            if (unavailable) throw IOException()
            return MarkdownSnapshot(MarkdownFile(uri, "2026-10-09.md", "2026-10-09.md", false), text, "bytes", false)
        }
        override suspend fun scan(vault: String): MarkdownScan = error("No scan")
        override suspend fun find(vault: String, name: String): MarkdownFile? = error("No find")
        override suspend fun create(vault: String, name: String): MarkdownFile = error("No create")
        override suspend fun write(uri: String, text: String, bom: Boolean, expected: String): MarkdownSnapshot = error("No Markdown writes")
    }
}
