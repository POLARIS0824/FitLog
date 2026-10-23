package com.example.fitlog.data.index

import com.example.fitlog.data.hash.ContentTextSnapshot
import com.example.fitlog.data.vault.MarkdownDocuments
import com.example.fitlog.data.vault.MarkdownSnapshot
import com.example.fitlog.data.vault.nextMarkdownReadOrder
import com.example.fitlog.model.source.IndexedScan
import com.example.fitlog.model.source.IndexedSource
import com.example.fitlog.model.source.SourceIndexSnapshot
import com.example.fitlog.model.source.requireVaultId
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface SourceIndex {
    fun activate(vaultId: String?)
    fun observe(vaultId: String): kotlinx.coroutines.flow.Flow<SourceIndexSnapshot>
    fun refresh(vaultId: String): Job
    suspend fun recordRead(vaultId: String, path: String, snapshot: MarkdownSnapshot)
}

/** One app-owned scan; explicit source reads share its commit gate. */
class SourceIndexRepository(
    private val documents: MarkdownDocuments,
    private val store: SourceIndexStore,
    private val computationDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val now: () -> Long = System::currentTimeMillis,
    private val scope: CoroutineScope,
    private val resolveVaultUri: suspend (vaultId: String) -> String,
) : SourceIndex {
    private data class Activity(val vaultId: String? = null, val busy: Boolean = false, val failed: Boolean = false)
    private val activity = MutableStateFlow(Activity())
    private val gate = Mutex()
    private var scanJob: Job? = null
    private var activeVault: String? = null
    private var opened = false

    /** Called by the app configuration observer, never by page navigation. */
    @Synchronized override fun activate(vaultId: String?) {
        vaultId?.let(::requireVaultId)
        if (activeVault != vaultId) {
            scanJob?.cancel()
            activeVault = vaultId
        }
        if (!opened && vaultId != null) {
            opened = true
            refresh(vaultId)
        }
    }

    override fun observe(vaultId: String) = combine(store.observe(requireVaultId(vaultId)), activity) { value, current ->
        value.copy(
            refreshing = current.vaultId == vaultId && current.busy,
            refreshFailed = current.vaultId == vaultId && current.failed,
        )
    }

    /** Explicit refreshes share the active scan; no follow-up scan is queued. */
    @Synchronized override fun refresh(vaultId: String): Job {
        requireVaultId(vaultId)
        scanJob?.takeIf { it.isActive }?.let { return it }
        val task = scope.launch(start = CoroutineStart.LAZY) {
            val job = currentCoroutineContext().job
            var failed = false
            try {
                gate.withLock {
                    activity.value = Activity(vaultId, busy = true)
                    try {
                        val treeUri = resolveVaultUri(vaultId)
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
                            val version = snapshot?.let { contentVersion(it) }
                            IndexedSource(vaultId, file.uri, snapshot?.file?.name ?: file.name,
                                file.path, file.directory, snapshot?.file?.writable ?: file.writable,
                                if (snapshot != null) IndexedSource.AVAILABLE else IndexedSource.READ_FAILED,
                                version?.hash, version?.hashVersion, observedAt = snapshot?.readAt ?: nextMarkdownReadOrder())
                        }.toMutableList()
                        val complete = !discovered.partial && !readFailed
                        if (complete) {
                            val seen = updates.mapTo(mutableSetOf()) { it.uri }
                            previous.filter { it.uri !in seen }.forEach {
                                updates += it.copy(status = IndexedSource.MISSING, observedAt = nextMarkdownReadOrder())
                            }
                        }
                        currentCoroutineContext().ensureActive()
                        store.commit(updates, IndexedScan(vaultId,
                            if (complete) IndexedScan.COMPLETE else IndexedScan.PARTIAL, now()))
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        failed = true
                        withContext(NonCancellable) {
                            runCatching { store.commit(emptyList(), IndexedScan(vaultId, IndexedScan.FAILED, now())) }
                        }
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

    override suspend fun recordRead(vaultId: String, path: String, snapshot: MarkdownSnapshot) {
        requireVaultId(vaultId)
        val version = contentVersion(snapshot)
        gate.withLock {
            val old = store.sources(vaultId).firstOrNull { it.uri == snapshot.file.uri && it.path == path } ?: return@withLock
            // A scan may have observed this source later than the caller did; never commit the older view.
            if (snapshot.readAt <= old.observedAt) return@withLock
            store.commit(listOf(old.copy(status = IndexedSource.AVAILABLE, writable = snapshot.file.writable,
                contentHash = version.hash, hashVersion = version.hashVersion, observedAt = snapshot.readAt)))
        }
    }

    private suspend fun contentVersion(snapshot: MarkdownSnapshot) = withContext(computationDispatcher) {
        ContentTextSnapshot.fromRawText((if (snapshot.bom) "\uFEFF" else "") + snapshot.text)
    }
}
