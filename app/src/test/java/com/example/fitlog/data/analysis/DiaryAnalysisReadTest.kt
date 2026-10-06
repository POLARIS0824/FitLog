package com.example.fitlog.data.analysis

import androidx.room.Room
import com.example.fitlog.data.analysis.adapter.*
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
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
class DiaryAnalysisReadTest {
    private lateinit var db: DiaryAnalysisDatabase
    private lateinit var repo: DiaryAnalysisRepository
    private lateinit var parser: JsonDiaryParser
    private var calls = 0
    private var diskName: String? = null
    private var response: DiaryModelResponse = DiaryModelResponse.Json("""{"schemaVersion":1,"sessions":[]}""")

    @Before fun before() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), DiaryAnalysisDatabase::class.java).build()
        repo = DiaryAnalysisRepository(db)
        parser = JsonDiaryParser(DiaryModelSource { calls++; response })
    }

    @After fun after() = runBlocking {
        db.close()
        diskName?.let { RuntimeEnvironment.getApplication().deleteDatabase(it) }
        Unit
    }

    @Test fun readsNeverStartExtractionAndRemainScopedToSourceIdentity() = runBlocking {
        val input = fullInput("note")
        assertTrue(repo.readParses(input.parseKey.sourceKey).attempts.isEmpty())
        assertTrue(repo.observeParses(input.parseKey.sourceKey).first().attempts.isEmpty())
        assertNull(repo.observeConfirmed(input.parseKey.sourceKey).first())
        assertEquals(0, calls)
        val run = repo.parse(input, parser)
        val records = repo.observeParses(input.parseKey.sourceKey).first()
        assertEquals(run.parseRunId, records.latestCandidate?.attempt?.id)
        assertEquals(1, calls)
        val otherVault = SourceKey(UUID.randomUUID().toString(), input.parseKey.sourceKey.relPath)
        val otherPath = input.parseKey.sourceKey.copy(relPath = "other.md")
        assertTrue(repo.readParses(otherVault).attempts.isEmpty())
        assertTrue(repo.readParses(otherPath).attempts.isEmpty())
        assertEquals(1, calls)
    }

    @Test fun recentFailurePreservesPreviousCandidateAndConfirmation() = runBlocking {
        val oldInput = fullInput("first")
        val old = repo.parse(oldInput, parser)
        val analysis = (old.result as DiaryParseResult.Success).analysis
        val confirmed = (repo.confirm(DiaryConfirmation.fromCandidate(old.parseRunId, analysis, LocalDate.of(2026, 10, 2)))
            as DiaryConfirmationResult.Confirmed).diary
        response = DiaryModelResponse.Failure(DiaryParseFailure.NETWORK_ERROR)
        val failed = repo.parse(fullInput("changed"), parser)
        val records = repo.readParses(oldInput.parseKey.sourceKey)
        assertEquals(failed.parseRunId, records.latestAttempt?.id)
        assertEquals(failed.parseRunId, records.latestFailure?.id)
        assertEquals(ParseRunStatus.FAILED, records.latestAttempt?.status)
        assertEquals(old.parseRunId, records.latestCandidate?.attempt?.id)
        assertEquals(analysis, records.latestCandidate?.analysis)
        assertEquals(confirmed, repo.observeConfirmed(oldInput.parseKey.sourceKey).first())
        assertEquals(2, calls)
    }

    @Test fun damagedCandidateDoesNotHideAttemptMetadataOrConfirmedRecord() = runBlocking {
        val input = fullInput("note")
        val run = repo.parse(input, parser)
        val analysis = (run.result as DiaryParseResult.Success).analysis
        val confirmation = repo.confirm(DiaryConfirmation.fromCandidate(run.parseRunId, analysis, LocalDate.of(2026, 10, 2)))
        db.openHelper.writableDatabase.execSQL("UPDATE parse_run SET candidateJson = ? WHERE id = ?", arrayOf("damaged", run.parseRunId))
        val records = repo.observeParses(input.parseKey.sourceKey).first()
        assertTrue(records.candidateReadFailed)
        assertNull(records.latestCandidate)
        assertEquals(ParseRunStatus.SUCCEEDED, records.latestAttempt?.status)
        assertEquals((confirmation as DiaryConfirmationResult.Confirmed).diary, repo.observeConfirmed(input.parseKey.sourceKey).first())
        assertEquals(1, calls)
    }

    @Test fun observersReportOnlyCompletedAttemptsWithoutModelCallsOfTheirOwn() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<DiaryModelResponse>()
        parser = JsonDiaryParser(DiaryModelSource { calls++; started.complete(Unit); release.await() })
        val input = fullInput("note")
        val pending = async { repo.parse(input, parser) }
        started.await()
        assertNull(repo.observeParses(input.parseKey.sourceKey).first().latestAttempt)
        release.complete(response)
        pending.await()
        val completed = repo.observeParses(input.parseKey.sourceKey).first()
        assertEquals(ParseRunStatus.SUCCEEDED, completed.latestAttempt?.status)
        assertNotNull(completed.latestCandidate)
        assertEquals(1, calls)
    }

    @Test fun diskReopenRetainsCandidateConfirmationAndFailure() = runBlocking {
        db.close()
        val context = RuntimeEnvironment.getApplication()
        // Robolectric includes the test name in its temp path; keep Windows SQLite paths short.
        val name = "read.db"
        diskName = name
        db = Room.databaseBuilder(context, DiaryAnalysisDatabase::class.java, name).build()
        repo = DiaryAnalysisRepository(db)
        parser = JsonDiaryParser(DiaryModelSource { calls++; response })
        val input = fullInput("first")
        val run = repo.parse(input, parser)
        val analysis = (run.result as DiaryParseResult.Success).analysis
        repo.confirm(DiaryConfirmation.fromCandidate(run.parseRunId, analysis, LocalDate.of(2026, 10, 2)))
        response = DiaryModelResponse.Failure(DiaryParseFailure.TIMEOUT)
        val failed = repo.parse(fullInput("second"), parser)
        db.close()
        db = Room.databaseBuilder(context, DiaryAnalysisDatabase::class.java, name).build()
        repo = DiaryAnalysisRepository(db)
        // Reading the reopened repository does not install or invoke a parser.
        val restored = repo.observeParses(input.parseKey.sourceKey).first()
        assertEquals(analysis, restored.latestCandidate?.analysis)
        assertEquals(failed.parseRunId, restored.latestFailure?.id)
        assertEquals(run.parseRunId, repo.observeConfirmed(input.parseKey.sourceKey).first()?.diary?.parseRunId)
        assertEquals(2, calls)
    }

    @Test fun equalTimestampsUseInsertionOrderForTheMostRecentAttempt() = runBlocking {
        val key = fullInput("note").parseKey
        fun row(id: String) = ParseRunRow(id, key.sourceKey.vaultId, key.sourceKey.relPath, key.contentHash,
            key.hashVersion, key.extractorVersion, ParseRunStatus.FAILED, 1, 2, "TIMEOUT")
        db.analysis().insertRun(row("z-first"))
        db.analysis().insertRun(row("a-second"))
        assertEquals("a-second", repo.readParses(key.sourceKey).latestAttempt?.id)
        assertEquals("a-second", repo.observeParses(key.sourceKey).first().latestAttempt?.id)
    }
}
