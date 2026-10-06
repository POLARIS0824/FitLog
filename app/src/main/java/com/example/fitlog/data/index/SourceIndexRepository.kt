package com.example.fitlog.data.index

import android.content.Context
import android.net.Uri
import com.example.fitlog.data.vault.MarkdownDocumentRepository
import com.example.fitlog.data.vault.MarkdownDocuments
import com.example.fitlog.data.vault.MarkdownSnapshot
import com.example.fitlog.data.vault.VaultPreferences
import com.example.fitlog.data.vault.requireVaultId
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One app-owned scan; editor index updates wait until its snapshot has been committed. */
class SourceIndexRepository(
    private val documents: MarkdownDocuments,
    private val store: SourceIndexStore,
    private val now: () -> Long = System::currentTimeMillis,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val resolveVaultUri: suspend (vaultId: String) -> Uri,
) {
    companion object {
        @Volatile private var instance: SourceIndexRepository? = null
        fun get(context: Context): SourceIndexRepository = instance ?: synchronized(this) {
            instance ?: SourceIndexRepository(
                MarkdownDocumentRepository(context.applicationContext),
                RoomSourceIndexStore(SourceIndexDatabase.get(context.applicationContext)),
                resolveVaultUri = VaultPreferences(context.applicationContext)::getVaultUri,
            ).also { instance = it }
        }
    }

    private data class Activity(val vaultId: String? = null, val busy: Boolean = false, val failed: Boolean = false)
    private val activity = MutableStateFlow(Activity())
    private val gate = Mutex()
    private var scanJob: Job? = null
    private var activeVault: String? = null
    private var opened = false

    /** Called by the app configuration observer, never by page navigation. */
    @Synchronized fun activate(vaultId: String?) {
        vaultId?.let(::requireVaultId)
        if (activeVault != vaultId) {
            scanJob?.cancel()
            activeVault = vaultId
        }
        if (!opened) {
            opened = true
            vaultId?.let(::refresh)
        }
    }

    fun observe(vaultId: String) = combine(store.observe(requireVaultId(vaultId)), activity) { value, current ->
        value.copy(
            refreshing = current.vaultId == vaultId && current.busy,
            refreshFailed = current.vaultId == vaultId && current.failed,
        )
    }

    /** Explicit refreshes share the active scan; no follow-up scan is queued. */
    @Synchronized fun refresh(vaultId: String): Job {
        requireVaultId(vaultId)
        scanJob?.takeIf { it.isActive }?.let { return it }
        val task = scope.launch(start = CoroutineStart.LAZY) {
            val job = currentCoroutineContext().job
            var failed = false
            try {
                gate.withLock {
                    activity.value = Activity(vaultId, busy = true)
                    try {
                        val treeUri = resolveVaultUri(vaultId).toString()
                        val previous = store.sources(vaultId)
                        val discovered = documents.scan(treeUri)
                        currentCoroutineContext().ensureActive()
                        var readFailed = false
                        val updates = discovered.files.map { file ->
                            currentCoroutineContext().ensureActive()
                            val snapshot = try { documents.read(file.uri) } catch (e: Exception) {
                                if (e is CancellationException) throw e
                                readFailed = true
                                null
                            }
                            IndexedSource(vaultId, file.uri, snapshot?.file?.name ?: file.name,
                                file.path, file.directory, snapshot?.file?.writable ?: file.writable,
                                if (snapshot != null) IndexedSource.AVAILABLE else IndexedSource.READ_FAILED)
                        }.toMutableList()
                        val complete = !discovered.partial && !readFailed
                        if (complete) {
                            val seen = updates.mapTo(mutableSetOf()) { it.uri }
                            previous.filter { it.uri !in seen }.forEach { updates += it.copy(status = IndexedSource.MISSING) }
                        }
                        currentCoroutineContext().ensureActive()
                        store.commit(updates, IndexedScan(vaultId,
                            if (complete) IndexedScan.COMPLETE else IndexedScan.PARTIAL, now()))
                    } catch (e: Exception) {
                        failed = true
                        withContext(NonCancellable) {
                            runCatching { store.commit(emptyList(), IndexedScan(vaultId, IndexedScan.FAILED, now())) }
                        }
                        if (e is CancellationException) throw e
                    }
                }
            } finally {
                synchronized(this@SourceIndexRepository) {
                    if (scanJob == job) {
                        scanJob = null
                        activity.value = Activity(vaultId, failed = failed)
                    }
                }
            }
        }
        scanJob = task
        task.start()
        return task
    }

    suspend fun recordSaved(vaultId: String, snapshot: MarkdownSnapshot, directory: String, path: String?) {
        requireVaultId(vaultId)
        gate.withLock {
            val old = store.sources(vaultId).firstOrNull { it.uri == snapshot.file.uri }
            store.commit(listOf(IndexedSource(vaultId, snapshot.file.uri, snapshot.file.name,
                path ?: old?.path ?: snapshot.file.name, directory, snapshot.file.writable)))
        }
    }
}
