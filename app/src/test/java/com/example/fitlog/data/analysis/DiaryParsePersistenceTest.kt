package com.example.fitlog.data.analysis

import androidx.room.Room
import com.example.fitlog.data.analysis.adapter.*
import kotlinx.coroutines.*
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
class DiaryParsePersistenceTest {
    private lateinit var db: DiaryAnalysisDatabase
    private lateinit var repo: DiaryAnalysisRepository
    private var calls = 0
    private var response: DiaryModelResponse = DiaryModelResponse.Json("""{"schemaVersion":1,"sessions":[]}""")
    private val parser get() = JsonDiaryParser(DiaryModelSource { calls++; response })

    @Before fun before() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), DiaryAnalysisDatabase::class.java).build()
        repo = DiaryAnalysisRepository(db)
    }
    @After fun after() { db.close() }

    @Test fun everyExplicitCallMakesOneAttemptEvenForTheSameInput() = runBlocking {
        val input = fullInput("note")
        val first = repo.parse(input, parser)
        val second = repo.parse(input, parser)
        assertNotEquals(first.parseRunId, second.parseRunId)
        assertEquals(2, calls)
        val rows = db.analysis().runs(input.parseKey.sourceKey.vaultId, input.parseKey.sourceKey.relPath)
        assertEquals(2, rows.size)
        assertTrue(rows.all { it.status == ParseRunStatus.SUCCEEDED && it.finishedAt >= it.startedAt })
        assertEquals((response as DiaryModelResponse.Json).text, rows.last().rawModelJson)
        assertEquals(input.parseKey, repo.readParses(input.parseKey.sourceKey).latestCandidate?.analysis?.parseKey)
    }

    @Test fun malformedResponseIsStoredExactlyAndOnlyExplicitRetryCallsTheModelAgain() = runBlocking {
        response = DiaryModelResponse.Json("  {\n")
        val input = fullInput("note")
        val failed = repo.parse(input, parser)
        assertEquals(DiaryParseResult.Failure(DiaryParseFailure.MALFORMED_JSON), failed.result)
        val row = db.analysis().run(failed.parseRunId)!!
        assertEquals("  {\n", row.rawModelJson)
        assertEquals(ParseRunStatus.FAILED, row.status)
        assertNull(row.candidateJson)
        repo.readParses(input.parseKey.sourceKey)
        assertEquals(1, calls)
        response = DiaryModelResponse.Json("""{"schemaVersion":1,"sessions":[]}""")
        assertTrue(repo.parse(input, parser).result is DiaryParseResult.Success)
        assertEquals(2, calls)
    }

    @Test fun expectedNetworkFailureRemainsAStoredFailureWithNoAutomaticRetry() = runBlocking {
        response = DiaryModelResponse.Failure(DiaryParseFailure.NETWORK_ERROR)
        val run = repo.parse(fullInput("note"), parser)
        val row = db.analysis().run(run.parseRunId)!!
        assertEquals("NETWORK_ERROR", row.failureCode)
        assertNull(row.rawModelJson)
        assertEquals(1, calls)
    }

    @Test fun callerCancellationLeavesNoPersistedRunningAttemptAndCanBeRetried() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val paused = JsonDiaryParser(DiaryModelSource {
            calls++; started.complete(Unit); awaitCancellation()
        })
        val input = fullInput("note")
        val pending = launch { repo.parse(input, paused) }
        started.await()
        assertTrue(repo.readParses(input.parseKey.sourceKey).attempts.isEmpty())
        pending.cancelAndJoin()
        assertTrue(repo.readParses(input.parseKey.sourceKey).attempts.isEmpty())
        assertTrue(repo.parse(input, parser).result is DiaryParseResult.Success)
        assertEquals(2, calls)
    }

    @Test fun persistenceFailureIsReportedWithoutRepeatingTheModelRequest() = runBlocking {
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_run BEFORE INSERT ON parse_run BEGIN SELECT RAISE(ABORT, 'test failure'); END")
        val input = fullInput("note")
        try { repo.parse(input, parser); fail() } catch (_: DiaryAnalysisStorageException) { }
        assertEquals(1, calls)
        assertTrue(repo.readParses(input.parseKey.sourceKey).attempts.isEmpty())
    }

    @Test fun programmingErrorsAndWrongSourceResultsPropagateWithoutCreatingSuccesses() = runBlocking {
        val input = fullInput("note")
        val broken = RecordingDiaryParser { throw IllegalStateException("parser bug") }
        try { repo.parse(input, broken); fail() } catch (_: IllegalStateException) { }
        val wrong = RecordingDiaryParser {
            DiaryParseExecution(DiaryParseResult.Success(analyzeFixture(fullInput("other content"),
                """{"schemaVersion":1,"sessions":[]}""")), null)
        }
        try { repo.parse(input, wrong); fail() } catch (_: IllegalStateException) { }
        assertTrue(repo.readParses(input.parseKey.sourceKey).attempts.isEmpty())
    }

    @Test fun eachCallUsesItsOwnParserInsteadOfCapturingTheFirstConfiguration() = runBlocking {
        val input = fullInput("note")
        val a = JsonDiaryParser(DiaryModelSource { DiaryModelResponse.Failure(DiaryParseFailure.AUTHENTICATION_ERROR) })
        val b = JsonDiaryParser(DiaryModelSource { DiaryModelResponse.Failure(DiaryParseFailure.TIMEOUT) })
        assertEquals(DiaryParseResult.Failure(DiaryParseFailure.AUTHENTICATION_ERROR), repo.parse(input, a).result)
        assertEquals(DiaryParseResult.Failure(DiaryParseFailure.TIMEOUT), repo.parse(input, b).result)
        assertEquals("TIMEOUT", repo.readParses(input.parseKey.sourceKey).latestFailure?.failureCode)
    }

    @Test fun schemaMismatch() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val name = "schema.db"
        context.openOrCreateDatabase(name, 0, null).use {
            it.execSQL("CREATE TABLE old_confirmations (value TEXT NOT NULL)")
            it.execSQL("INSERT INTO old_confirmations VALUES ('must remain')")
            it.version = 1
        }
        val old = Room.databaseBuilder(context, DiaryAnalysisDatabase::class.java, name).build()
        try {
            val incompatible = DiaryAnalysisRepository(old)
            try { incompatible.parse(fullInput("note"), parser); fail() } catch (_: DiaryAnalysisStorageException) { }
            assertEquals(0, calls)
        } finally { old.close() }
        try {
            context.openOrCreateDatabase(name, 0, null).use {
                assertEquals(1, it.version)
                it.rawQuery("SELECT value FROM old_confirmations", null).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("must remain", cursor.getString(0))
                }
            }
        } finally { context.deleteDatabase(name) }
    }
}
