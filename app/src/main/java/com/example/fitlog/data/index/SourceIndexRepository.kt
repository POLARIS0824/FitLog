package com.example.fitlog.data.index

import android.content.Context
import com.example.fitlog.data.vault.MarkdownDocumentRepository
import com.example.fitlog.data.vault.MarkdownDocuments
import com.example.fitlog.data.vault.MarkdownSnapshot
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/** One app-scoped instance coordinates editor saves with scans. Markdown remains authoritative. */
class SourceIndexRepository(
    private val documents: MarkdownDocuments,
    private val store: SourceIndexStore,
    private val now: () -> Long = System::currentTimeMillis,
) {
    companion object {
        @Volatile private var instance: SourceIndexRepository? = null

        fun get(context: Context): SourceIndexRepository = instance ?: synchronized(this) {
            instance ?: SourceIndexRepository(
                MarkdownDocumentRepository(context.applicationContext),
                RoomSourceIndexStore(SourceIndexDatabase.get(context.applicationContext)),
            ).also { instance = it }
        }
    }

    private class Slot {
        val gate = Mutex()
        var generation = 0L
        var job: Job? = null
        var revision = 0L
        val saved = mutableMapOf<String, Long>()
    }
    private val slots = ConcurrentHashMap<String, Slot>()
    private fun slot(vault: String) = slots.getOrPut(vault) { Slot() }

    fun observe(vault: String) = store.observe(vault).map { value ->
        if (value.scan?.status == IndexedScan.SCANNING && slot(vault).job?.isActive != true)
            value.copy(scan = value.scan.copy(status = IndexedScan.INTERRUPTED)) else value
    }

    fun cancel(vault: String) {
        val slot = slot(vault)
        synchronized(slot) { slot.generation++; slot.job?.cancel(); slot.job = null }
    }

    suspend fun refresh(vault: String) {
        val slot = slot(vault)
        val job = currentCoroutineContext().job
        val token = synchronized(slot) {
            if (slot.job?.isActive == true) return
            slot.job = job
            ++slot.generation
        }
        val startRevision = slot.gate.withLock { slot.revision }
        fun checkCurrent() {
            job.ensureActive()
            if (slot.generation != token) throw CancellationException()
        }
        try {
            slot.gate.withLock { checkCurrent(); store.commit(emptyList(), IndexedScan(vault, IndexedScan.SCANNING)) }
            val discovered = documents.scan(vault)
            checkCurrent()
            var readFailed = false
            val updates = discovered.files.map { file ->
                checkCurrent()
                val snapshot = try { documents.read(file.uri) } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    readFailed = true
                    null
                }
                IndexedSource(vault, file.uri, snapshot?.file?.name ?: file.name, file.path, file.directory,
                    snapshot?.file?.writable ?: file.writable, snapshot?.fingerprint,
                    if (snapshot != null) now() else null,
                    if (snapshot != null) IndexedSource.AVAILABLE else IndexedSource.READ_FAILED)
            }
            slot.gate.withLock {
                checkCurrent()
                val previous = store.sources(vault).associateBy { it.uri }
                val seen = updates.mapTo(mutableSetOf()) { it.uri }
                val merged = updates.filter { (slot.saved[it.uri] ?: 0L) <= startRevision }.map { row ->
                    if (row.status == IndexedSource.READ_FAILED) row.copy(
                        fingerprint = previous[row.uri]?.fingerprint, verifiedAt = previous[row.uri]?.verifiedAt) else row
                }.toMutableList()
                if (!discovered.partial) previous.values.filter {
                    it.uri !in seen && (slot.saved[it.uri] ?: 0L) <= startRevision
                }.forEach { merged += it.copy(status = IndexedSource.MISSING) }
                checkCurrent()
                store.commit(merged, IndexedScan(vault,
                    if (discovered.partial || readFailed) IndexedScan.PARTIAL else IndexedScan.COMPLETE, now()))
            }
        } catch (e: Exception) {
            withContext(NonCancellable) {
                slot.gate.withLock {
                    // A cancelled generation may only close its own running scan, never a replacement scan.
                    if (slot.job == job || (slot.job == null && slot.generation == token + 1)) {
                        runCatching { store.commit(emptyList(), IndexedScan(vault,
                            if (e is CancellationException) IndexedScan.INTERRUPTED else IndexedScan.FAILED)) }
                    }
                }
            }
            throw e
        } finally {
            synchronized(slot) { if (slot.job == job) slot.job = null }
        }
    }

    suspend fun recordSaved(vault: String, snapshot: MarkdownSnapshot, directory: String, path: String?) {
        val slot = slot(vault)
        slot.gate.withLock {
            slot.saved[snapshot.file.uri] = ++slot.revision
            val old = store.sources(vault).firstOrNull { it.uri == snapshot.file.uri }
            store.commit(listOf(IndexedSource(vault, snapshot.file.uri, snapshot.file.name,
                path ?: old?.path ?: snapshot.file.name, directory, snapshot.file.writable,
                snapshot.fingerprint, now())))
        }
    }
}
