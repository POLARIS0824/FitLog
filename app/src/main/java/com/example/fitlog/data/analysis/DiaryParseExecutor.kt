package com.example.fitlog.data.analysis

import com.example.fitlog.data.analysis.adapter.RecordingDiaryParser
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

data class StoredDiaryParse(val parseRunId: String, val result: DiaryParseResult, val reused: Boolean)

/** One consumer owns attempts; cancelling an individual waiter does not cancel shared application work. */
internal class DiaryParseExecutor(
    private val repository: DiaryAnalysisRepository,
    private val parser: RecordingDiaryParser,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : AutoCloseable {
    private class Request(val input: DiaryParseInput) {
        val response = CompletableDeferred<StoredDiaryParse>()
        var task: Deferred<StoredDiaryParse>? = null
        var cancelled = false
    }
    private val gate = Any()
    private val pending = mutableMapOf<DiaryParseKey, Request>()
    private val queue = Channel<Request>(Channel.UNLIMITED)
    private var stopped: Throwable? = null
    private val worker = scope.launch { consume() }

    suspend fun parse(input: DiaryParseInput): StoredDiaryParse {
        val response = synchronized(gate) {
            stopped?.let { throw it }
            check(worker.isActive) { "Diary parser executor is closed" }
            pending[input.parseKey]?.response ?: Request(input).also {
                check(queue.trySend(it).isSuccess)
                pending[input.parseKey] = it
            }.response
        }
        return response.await()
    }

    /** Explicitly cancels the shared request. A later submission may retry with a new attempt ID. */
    fun cancel(key: DiaryParseKey) = synchronized(gate) {
        pending.remove(key)?.let {
            it.cancelled = true
            it.task?.cancel()
            it.response.cancel()
        }
        Unit
    }

    private suspend fun consume() {
        var terminal: Throwable = CancellationException("Diary parser executor closed")
        try {
            repository.initialize()
            supervisorScope {
                for (request in queue) {
                    try {
                        val task = synchronized(gate) {
                            if (request.cancelled) null else async(start = CoroutineStart.LAZY) {
                                perform(request.input)
                            }.also { request.task = it }
                        }
                        if (task != null) {
                            task.start()
                            request.response.complete(task.await())
                        }
                    } catch (e: CancellationException) {
                        request.response.cancel(e)
                        currentCoroutineContext().ensureActive()
                        // Cancellation must still propagate to its waiter, but failed cleanup stops the queue.
                        synchronized(gate) { stopped }?.let { throw it }
                    } catch (e: Throwable) {
                        request.response.completeExceptionally(e)
                        // Do not spend money on later files after persistence or unexpected execution failures.
                        throw e
                    } finally {
                        synchronized(gate) {
                            if (pending[request.input.parseKey] === request) pending.remove(request.input.parseKey)
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            terminal = e
        } finally {
            synchronized(gate) {
                stopped = terminal
                queue.close(terminal)
                pending.values.forEach {
                    it.task?.cancel()
                    it.response.completeExceptionally(terminal)
                }
                pending.clear()
                // Release queued whole-file snapshots after shutdown.
                while (queue.tryReceive().isSuccess) Unit
            }
        }
    }

    private suspend fun perform(input: DiaryParseInput): StoredDiaryParse {
        val key = input.parseKey
        repository.successful(key)?.let {
            return StoredDiaryParse(it.id, DiaryParseResult.Success(repository.decodeCandidate(it)), true)
        }
        val id = newId()
        try {
            repository.startRun(ParseRunRow(id, key.sourceKey.vaultId, key.sourceKey.relPath, key.contentHash,
                key.hashVersion, key.extractorVersion, ParseRunStatus.RUNNING, now()))
            val execution = parser.execute(input)
            currentCoroutineContext().ensureActive()
            when (val result = execution.result) {
                is DiaryParseResult.Success -> {
                    check(result.analysis.parseKey == key) { "Parser returned a different content identity" }
                    repository.finishRun(id, ParseRunStatus.SUCCEEDED, now(), rawModelJson = execution.rawModelJson,
                        candidateJson = DiaryAnalysisCodec.encodeCandidate(result.analysis))
                }
                is DiaryParseResult.Failure -> repository.finishRun(id, ParseRunStatus.FAILED, now(),
                    result.reason.name, execution.rawModelJson)
            }
            return StoredDiaryParse(id, execution.result, false)
        } catch (e: Exception) {
            withContext(NonCancellable) {
                try {
                    repository.interruptRun(id, now(),
                        if (e is CancellationException) "CANCELLED" else "EXECUTION_INTERRUPTED")
                } catch (cleanup: Exception) {
                    e.addSuppressed(cleanup)
                    synchronized(gate) { stopped = cleanup }
                }
            }
            throw e
        }
    }

    override fun close() {
        synchronized(gate) {
            stopped = CancellationException("Diary parser executor closed")
            pending.values.forEach { it.task?.cancel() }
            worker.cancel()
        }
    }

    suspend fun awaitClosed() = worker.join()
}
