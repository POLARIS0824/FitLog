package com.example.fitlog.data.analysis

import androidx.room.Room
import com.example.fitlog.data.analysis.adapter.*
import java.util.Collections
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
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
class DiaryParseExecutorTest {
    private lateinit var db: DiaryAnalysisDatabase
    private lateinit var repo: DiaryAnalysisRepository
    private val actors = mutableListOf<DiaryParseExecutor>()
    private val emptyJson = """{"schemaVersion":1,"sessions":[]}"""

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), DiaryAnalysisDatabase::class.java).build()
        repo = DiaryAnalysisRepository(db)
    }
    @After fun tearDown() = runBlocking {
        actors.forEach { it.close() }
        actors.forEach { it.awaitClosed() }
        db.close()
    }
    private fun actor(source: DiaryModelSource): DiaryParseExecutor =
        actor(JsonDiaryParser(source))
    private fun actor(parser: RecordingDiaryParser): DiaryParseExecutor = DiaryParseExecutor(repo, parser).also { actors += it }

    @Test fun queuedFilesRunSeriallyAndIdenticalInFlightRequestsShareOneAttempt() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val calls = Collections.synchronizedList(mutableListOf<String>())
        val active = AtomicInteger()
        val maximum = AtomicInteger()
        val executor = actor(DiaryModelSource { text ->
            val count = active.incrementAndGet()
            maximum.updateAndGet { maxOf(it, count) }
            calls.add(text)
            try {
                if (text == "a") { entered.complete(Unit); release.await() }
                delay(5)
                DiaryModelResponse.Json(emptyJson)
            } finally { active.decrementAndGet() }
        })
        val a = fullInput("a")
        val first = async(start = CoroutineStart.UNDISPATCHED) { executor.parse(a) }
        entered.await()
        val duplicate = async(start = CoroutineStart.UNDISPATCHED) { executor.parse(a) }
        val b = async(start = CoroutineStart.UNDISPATCHED) { executor.parse(fullInput("b")) }
        val c = async(start = CoroutineStart.UNDISPATCHED) { executor.parse(fullInput("c")) }
        assertFalse(duplicate.isCompleted)
        release.complete(Unit)
        assertEquals(first.await().parseRunId, duplicate.await().parseRunId)
        b.await(); c.await()
        assertEquals(listOf("a", "b", "c"), calls.toList())
        assertEquals(1, maximum.get())
        assertEquals(3, db.analysis().runs(a.parseKey.sourceKey.vaultId, a.parseKey.sourceKey.relPath).size)
    }

    @Test fun reuseRequiresEveryFieldOfTheParseKeyAndNeverRepeatsASuccessfulRequest() = runBlocking {
        val requests = AtomicInteger()
        val executor = actor(DiaryModelSource { requests.incrementAndGet(); DiaryModelResponse.Json(emptyJson) })
        val input = fullInput("one\r\ntwo")
        val first = executor.parse(input)
        val sameContent = fullInput("\uFEFFone\ntwo")
        val cached = executor.parse(sameContent)
        assertEquals(first.parseRunId, cached.parseRunId)
        assertTrue(cached.reused)
        assertEquals(1, requests.get())
        val key = input.parseKey
        val variants = listOf(
            fullInput("changed"),
            DiaryParseInput.fromSnapshot(key.sourceKey.copy(relPath = "other.md"), input.text, key.extractorVersion),
            DiaryParseInput.fromSnapshot(key.sourceKey.copy(vaultId = UUID.randomUUID().toString()), input.text, key.extractorVersion),
            DiaryParseInput.fromSnapshot(key.sourceKey, input.text, "extractor-v2"),
        )
        variants.forEach { assertFalse(executor.parse(it).reused) }
        assertEquals(5, requests.get())
        assertNull(repo.successful(key.copy(hashVersion = key.hashVersion + 1)))
    }

    @Test fun malformedResponsesRemainExactFailuresAndTheSameKeyCanRetry() = runBlocking {
        val raw = "  { malformed response\n"
        var reply = raw
        val requests = AtomicInteger()
        val executor = actor(DiaryModelSource { requests.incrementAndGet(); DiaryModelResponse.Json(reply) })
        val input = fullInput("private diary text")
        val failed = executor.parse(input)
        assertEquals(DiaryParseResult.Failure(DiaryParseFailure.MALFORMED_JSON), failed.result)
        val failure = db.analysis().run(failed.parseRunId)!!
        assertEquals(ParseRunStatus.FAILED, failure.status)
        assertEquals(raw, failure.rawModelJson)
        assertNull(failure.candidateJson)
        assertEquals("MALFORMED_JSON", failure.failureCode)
        reply = " \n$emptyJson\n "
        val success = executor.parse(input)
        assertFalse(success.reused)
        assertNotEquals(failed.parseRunId, success.parseRunId)
        val stored = db.analysis().run(success.parseRunId)!!
        assertEquals(reply, stored.rawModelJson)
        assertFalse(stored.candidateJson!!.contains("private diary text"))
        assertEquals(2, requests.get())
        assertEquals(success.parseRunId, executor.parse(input).parseRunId)
        assertEquals(2, requests.get())
    }

    @Test fun expectedNetworkFailureDoesNotPreventLaterFilesFromRunning() = runBlocking {
        val executor = actor(DiaryModelSource { text ->
            if (text == "fail") DiaryModelResponse.Failure(DiaryParseFailure.NETWORK_ERROR)
            else DiaryModelResponse.Json(emptyJson)
        })
        val fail = async(start = CoroutineStart.UNDISPATCHED) { executor.parse(fullInput("fail")) }
        val success = async(start = CoroutineStart.UNDISPATCHED) { executor.parse(fullInput("ok")) }
        val storedFailure = fail.await()
        assertEquals(DiaryParseResult.Failure(DiaryParseFailure.NETWORK_ERROR), storedFailure.result)
        assertNull(db.analysis().run(storedFailure.parseRunId)!!.rawModelJson)
        assertTrue(success.await().result is DiaryParseResult.Success)
    }

    @Test fun startupRecoveryIsIdempotentAndDoesNotInterruptNewAttempts() = runBlocking {
        val key = fullInput("old").parseKey
        val old = ParseRunRow("old", key.sourceKey.vaultId, key.sourceKey.relPath, key.contentHash,
            key.hashVersion, key.extractorVersion, ParseRunStatus.RUNNING, 1)
        db.analysis().insertRun(old)
        repo.initialize()
        val recovered = db.analysis().run("old")!!
        assertEquals(ParseRunStatus.INTERRUPTED, recovered.status)
        assertEquals("PROCESS_INTERRUPTED", recovered.failureCode)
        assertNotNull(recovered.finishedAt)
        db.analysis().insertRun(old.copy(id = "new"))
        repo.initialize()
        assertEquals(recovered, db.analysis().run("old"))
        assertEquals(ParseRunStatus.RUNNING, db.analysis().run("new")!!.status)
        assertEquals(AnalysisStorageState.READY, repo.storageState.value)
        assertEquals(0, db.analysis().finishRun("old", ParseRunStatus.SUCCEEDED, 2, null, null, "stale"))
        assertEquals(ParseRunStatus.RUNNING, db.analysis().run("new")!!.status)
    }

    @Test fun initializationCompletesBeforeTheFirstModelCall() = runBlocking {
        val key = fullInput("old").parseKey
        db.analysis().insertRun(ParseRunRow("old", key.sourceKey.vaultId, key.sourceKey.relPath, key.contentHash,
            key.hashVersion, key.extractorVersion, ParseRunStatus.RUNNING, 1))
        val executor = actor(DiaryModelSource {
            assertEquals(ParseRunStatus.INTERRUPTED, db.analysis().run("old")!!.status)
            DiaryModelResponse.Json(emptyJson)
        })
        assertTrue(executor.parse(fullInput("new")).result is DiaryParseResult.Success)
    }

    @Test fun initializationFailureIsObservableAndNeverCallsTheModel() = runBlocking {
        val key = fullInput("old").parseKey
        db.analysis().insertRun(ParseRunRow("old", key.sourceKey.vaultId, key.sourceKey.relPath, key.contentHash,
            key.hashVersion, key.extractorVersion, ParseRunStatus.RUNNING, 1))
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_recovery BEFORE UPDATE ON parse_run BEGIN SELECT RAISE(ABORT, 'test recovery failure'); END")
        val calls = AtomicInteger()
        val executor = actor(DiaryModelSource { calls.incrementAndGet(); DiaryModelResponse.Json(emptyJson) })
        try { executor.parse(fullInput("new")); fail() } catch (_: DiaryAnalysisStorageException) { }
        assertEquals(0, calls.get())
        assertEquals(AnalysisStorageState.FAILED, repo.storageState.value)
        assertEquals(ParseRunStatus.RUNNING, db.analysis().run("old")!!.status)
    }

    @Test fun explicitCancellationRecordsInterruptionAndLaterRetryUsesANewId() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val calls = AtomicInteger()
        val executor = actor(DiaryModelSource {
            if (calls.incrementAndGet() == 1) { entered.complete(Unit); awaitCancellation() }
            DiaryModelResponse.Json(emptyJson)
        })
        val input = fullInput("cancel")
        val first = async { executor.parse(input) }
        entered.await()
        val firstId = db.analysis().runs(input.parseKey.sourceKey.vaultId, input.parseKey.sourceKey.relPath).single().id
        executor.cancel(input.parseKey)
        try { first.await(); fail() } catch (_: CancellationException) { }
        val retry = executor.parse(input)
        assertNotEquals(firstId, retry.parseRunId)
        assertEquals(ParseRunStatus.INTERRUPTED, db.analysis().run(firstId)!!.status)
        assertEquals("CANCELLED", db.analysis().run(firstId)!!.failureCode)
        assertEquals(ParseRunStatus.SUCCEEDED, db.analysis().run(retry.parseRunId)!!.status)
    }

    @Test fun cancellingAWaiterKeepsSharedWorkAliveAndCancellingQueuedWorkMakesNoAttempt() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val calls = AtomicInteger()
        val executor = actor(DiaryModelSource {
            calls.incrementAndGet(); entered.complete(Unit); release.await(); DiaryModelResponse.Json(emptyJson)
        })
        val input = fullInput("shared")
        val first = async { executor.parse(input) }
        entered.await()
        val second = async(start = CoroutineStart.UNDISPATCHED) { executor.parse(input) }
        val queuedInput = fullInput("queued")
        val queued = async(start = CoroutineStart.UNDISPATCHED) { executor.parse(queuedInput) }
        executor.cancel(queuedInput.parseKey)
        first.cancelAndJoin()
        release.complete(Unit)
        assertTrue(second.await().result is DiaryParseResult.Success)
        try { queued.await(); fail() } catch (_: CancellationException) { }
        assertEquals(1, calls.get())
        assertEquals(1, db.analysis().runs(input.parseKey.sourceKey.vaultId, input.parseKey.sourceKey.relPath).size)
    }

    @Test fun writeFailureStopsQueuedRequestsAndReportsPersistenceFailure() = runBlocking {
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_success BEFORE UPDATE OF status ON parse_run WHEN NEW.status = 'SUCCEEDED' BEGIN SELECT RAISE(ABORT, 'test persistence failure'); END")
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val calls = AtomicInteger()
        val executor = actor(DiaryModelSource {
            calls.incrementAndGet(); entered.complete(Unit); release.await(); DiaryModelResponse.Json(emptyJson)
        })
        supervisorScope {
            val first = async { executor.parse(fullInput("first")) }
            entered.await()
            val second = async(start = CoroutineStart.UNDISPATCHED) { executor.parse(fullInput("second")) }
            release.complete(Unit)
            listOf(first, second).forEach { request ->
                try { request.await(); fail() } catch (_: DiaryAnalysisStorageException) { }
            }
            try { executor.parse(fullInput("third")); fail() } catch (_: DiaryAnalysisStorageException) { }
        }
        assertEquals(1, calls.get())
        assertEquals(ParseRunStatus.INTERRUPTED, db.analysis().runs(fullInput("").parseKey.sourceKey.vaultId,
            fullInput("").parseKey.sourceKey.relPath).single().status)
    }

    @Test fun programmingErrorsPropagateWithoutBecomingEmptySuccessOrExpectedFailure() = runBlocking {
        val defect = IllegalStateException("programming defect")
        val executor = actor(RecordingDiaryParser { throw defect })
        try { executor.parse(fullInput("")); fail() } catch (e: IllegalStateException) {
            assertEquals(defect.message, e.message)
            // Coroutine stack-trace recovery may copy exceptions across await boundaries.
            assertTrue(generateSequence<Throwable>(e) { it.cause }.any { it === defect })
        }
        val key = fullInput("").parseKey.sourceKey
        assertEquals(ParseRunStatus.INTERRUPTED, db.analysis().runs(key.vaultId, key.relPath).single().status)
    }

    @Test fun failedCancellationCleanupPreservesCancellationAndStopsLaterModelCalls() = runBlocking {
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_interruption BEFORE UPDATE OF status ON parse_run WHEN NEW.status = 'INTERRUPTED' BEGIN SELECT RAISE(ABORT, 'test cleanup failure'); END")
        val entered = CompletableDeferred<Unit>()
        val calls = AtomicInteger()
        val executor = actor(DiaryModelSource {
            calls.incrementAndGet(); entered.complete(Unit); awaitCancellation()
        })
        supervisorScope {
            val input = fullInput("first")
            val first = async { executor.parse(input) }
            entered.await()
            val queued = async(start = CoroutineStart.UNDISPATCHED) { executor.parse(fullInput("second")) }
            executor.cancel(input.parseKey)
            try { first.await(); fail() } catch (_: CancellationException) { }
            try { queued.await(); fail() } catch (_: DiaryAnalysisStorageException) { }
        }
        assertEquals(1, calls.get())
        assertEquals(AnalysisStorageState.FAILED, repo.storageState.value)
        val key = fullInput("").parseKey.sourceKey
        assertEquals(ParseRunStatus.RUNNING, db.analysis().runs(key.vaultId, key.relPath).single().status)
    }

    @Test fun corruptSuccessfulCandidateReportsFailureWithoutSpendingOnAnotherModelCall() = runBlocking {
        val calls = AtomicInteger()
        val executor = actor(DiaryModelSource { calls.incrementAndGet(); DiaryModelResponse.Json(emptyJson) })
        val input = fullInput("")
        executor.parse(input)
        db.openHelper.writableDatabase.execSQL("UPDATE parse_run SET candidateJson = 'corrupt'")
        try { executor.parse(input); fail() } catch (_: DiaryAnalysisStorageException) { }
        assertEquals(1, calls.get())
    }

    @Test fun repositoryInstallsOnlyOneApplicationExecutor() {
        val parser = JsonDiaryParser(DiaryModelSource { DiaryModelResponse.Json(emptyJson) })
        val first = repo.executor(parser)
        actors += first
        assertSame(first, repo.executor(parser))
    }

    @Test fun lateInterruptionCleanupCannotReplaceAnAlreadyCommittedSuccess() = runBlocking {
        val executor = actor(DiaryModelSource { DiaryModelResponse.Json(emptyJson) })
        val parsed = executor.parse(fullInput("completed"))
        val completed = db.analysis().run(parsed.parseRunId)!!
        assertEquals(0, repo.interruptRun(parsed.parseRunId, System.currentTimeMillis(), "CANCELLED"))
        assertEquals(completed, db.analysis().run(parsed.parseRunId))
        assertEquals(AnalysisStorageState.READY, repo.storageState.value)
        assertTrue(executor.parse(fullInput("next")).result is DiaryParseResult.Success)
    }
}
