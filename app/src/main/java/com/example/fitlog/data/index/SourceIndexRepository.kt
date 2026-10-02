package com.example.fitlog.data.index

import android.content.Context
import android.os.SystemClock
import com.example.fitlog.data.vault.MarkdownDocumentRepository
import com.example.fitlog.data.vault.MarkdownDocuments
import com.example.fitlog.data.vault.MarkdownSnapshot
import com.example.fitlog.data.vault.VaultPreferences
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/** App-owned synchronization; navigation only observes the derived index. */
class SourceIndexRepository(
    private val documents: MarkdownDocuments,
    private val store: SourceIndexStore,
    private val now: () -> Long = System::currentTimeMillis,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val elapsedNow: () -> Long = now,
    private val invalidations: IndexInvalidations = MemoryIndexInvalidations(),
    private val resolveVaultUri: suspend (String) -> String = { it },
) {
    enum class Reason { Activation, Foreground, VisiblePeriodic }
    companion object {
        const val FOREGROUND_INTERVAL = 30_000L
        const val VISIBLE_INTERVAL = 5 * 60_000L
        const val FULL_INTERVAL = 24 * 60 * 60_000L
        @Volatile private var instance: SourceIndexRepository? = null
        fun get(context: Context): SourceIndexRepository = instance ?: synchronized(this) {
            instance ?: SourceIndexRepository(
                MarkdownDocumentRepository(context.applicationContext),
                RoomSourceIndexStore(SourceIndexDatabase.get(context.applicationContext)),
                elapsedNow = SystemClock::elapsedRealtime,
                invalidations = PreferenceIndexInvalidations(context.applicationContext),
                resolveVaultUri = VaultPreferences(context.applicationContext)::getVaultUri,
            ).also { instance = it }
        }
    }
    private data class Activity(val busy: Boolean = false, val failed: Boolean = false)
    private class Slot {
        val gate = Mutex()
        var preparedUri: String? = null
        var generation = 0L
        var job: Job? = null
        var coordinator: Job? = null
        var pendingFull = false
        var pendingDirty = false
        var dirty = false
        var lastAttempt: Long? = null
        var revision = 0L
        val saved = mutableMapOf<String, Long>()
        val activity = MutableStateFlow(Activity())
    }
    private val slots = ConcurrentHashMap<String, Slot>()
    private fun slot(vault: String) = slots.getOrPut(vault) { Slot() }
    private var activeVault: String? = null

    @Synchronized fun activate(vault: String?, force: Boolean = false) {
        if (activeVault == vault) {
            if (force && vault != null) forceRefresh(vault)
            return
        }
        activeVault?.let(::cancel)
        activeVault = vault
        vault?.let { if (force) forceRefresh(it) else ensureFresh(it, Reason.Activation) }
    }

    private suspend fun prepareVault(vaultId: String): String = slot(vaultId).gate.withLock {
        val slot = slot(vaultId)
        slot.preparedUri ?: resolveVaultUri(vaultId).also { uri ->
            if (uri != vaultId) {
                val dirty = invalidations.contains(uri)
                if (dirty) invalidations.mark(vaultId)
                store.migrateVault(uri, vaultId)
                if (dirty) invalidations.clear(uri)
            }
            slot.preparedUri = uri
        }
    }

    fun observe(vault: String) = combine(flow {
        prepareVault(vault)
        emitAll(store.observe(vault))
    }, slot(vault).activity) { value, activity ->
        val scan = if (value.scan?.status == IndexedScan.SCANNING && !activity.busy &&
            synchronized(slot(vault)) { slot(vault).job?.isActive != true })
            value.scan.copy(status = IndexedScan.INTERRUPTED) else value.scan
        value.copy(scan = scan, refreshing = activity.busy, refreshFailed = activity.failed)
    }

    fun cancel(vault: String) {
        val slot = slot(vault)
        synchronized(slot) {
            slot.generation++
            if (slot.coordinator?.isActive == true || slot.job?.isActive == true) slot.lastAttempt = null
            slot.coordinator?.cancel(); slot.coordinator = null
            slot.job?.cancel(); slot.job = null
            slot.pendingFull = false; slot.pendingDirty = false
            slot.activity.value = Activity()
        }
    }

    fun ensureFresh(vault: String, reason: Reason): Job? = request(vault, reason, false)
    fun forceRefresh(vault: String): Job? = request(vault, Reason.Activation, true)

    private fun request(vault: String, reason: Reason, force: Boolean): Job? {
        val slot = slot(vault)
        synchronized(slot) {
            if (slot.coordinator?.isActive == true) {
                if (force) slot.pendingFull = true
                return slot.coordinator
            }
            val dirty = slot.dirty || invalidations.contains(vault)
            val interval = if (reason == Reason.Foreground) FOREGROUND_INTERVAL else VISIBLE_INTERVAL
            val elapsed = slot.lastAttempt?.let { elapsedNow() - it }
            if (!force && !dirty && elapsed != null && elapsed in 0 until interval) return null
            slot.activity.value = Activity(busy = true)
            val coordinator = scope.launch(start = CoroutineStart.LAZY) {
                val coordinatorJob = currentCoroutineContext().job
                var fullRequested = force || dirty
                try {
                    do {
                        var failed = false
                        try {
                            synchronized(slot) {
                                coordinatorJob.ensureActive()
                                if (slot.coordinator != coordinatorJob) throw CancellationException()
                                slot.lastAttempt = elapsedNow()
                            }
                            val scan = store.observe(vault).first().scan
                            val age = scan?.fullVerifiedAt?.let { now() - it }
                            val full = fullRequested || age == null || age < 0 || age >= FULL_INTERVAL
                            performScan(vault, full)
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            failed = true
                        }
                        synchronized(slot) {
                            coordinatorJob.ensureActive()
                            if (slot.coordinator != coordinatorJob) throw CancellationException()
                            fullRequested = slot.pendingFull || slot.pendingDirty
                            slot.pendingFull = false; slot.pendingDirty = false
                            if (!fullRequested) {
                                slot.coordinator = null
                                slot.activity.value = Activity(failed = failed)
                            }
                        }
                    } while (fullRequested)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    synchronized(slot) {
                        if (slot.coordinator == coordinatorJob)
                            slot.activity.value = Activity(failed = true)
                    }
                } finally {
                    synchronized(slot) {
                        if (slot.coordinator == coordinatorJob) {
                            slot.coordinator = null
                            slot.pendingFull = false; slot.pendingDirty = false
                            slot.activity.value = slot.activity.value.copy(busy = false)
                        }
                    }
                }
            }
            slot.coordinator = coordinator
            coordinator.start()
            return coordinator
        }
    }

    /** Compatibility for existing direct scan callers; UI uses the app-owned request APIs. */
    suspend fun refresh(vault: String) = performScan(vault, full = true)

    private suspend fun performScan(vault: String, full: Boolean) {
        val slot = slot(vault)
        val job = currentCoroutineContext().job
        val token = synchronized(slot) {
            if (slot.job?.isActive == true) return
            slot.job = job
            ++slot.generation
        }
        fun checkCurrent() {
            job.ensureActive()
            if (synchronized(slot) { slot.generation != token }) throw CancellationException()
        }
        var startRevision = 0L
        var priorScan: IndexedScan? = null
        try {
            val treeUri = prepareVault(vault)
            val previous = slot.gate.withLock {
                checkCurrent()
                startRevision = slot.revision
                priorScan = store.observe(vault).first().scan
                store.commit(emptyList(), IndexedScan(vault, IndexedScan.SCANNING,
                    metadataCheckedAt = priorScan?.metadataCheckedAt, fullVerifiedAt = priorScan?.fullVerifiedAt))
                store.sources(vault).associateBy { it.uri }
            }
            val discovered = documents.scan(treeUri)
            checkCurrent()
            var readFailed = false
            val updates = discovered.files.map { file ->
                checkCurrent()
                val old = previous[file.uri]
                val reuse = !full && old?.status == IndexedSource.AVAILABLE && old.fingerprint != null &&
                    file.lastModified != null && file.size != null &&
                    old.lastModified == file.lastModified && old.size == file.size
                if (reuse) old.copy(name = file.name, path = file.path, directory = file.directory,
                    writable = file.writable)
                else {
                    val snapshot = try { documents.read(file.uri) } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        readFailed = true
                        null
                    }
                    IndexedSource(vault, file.uri, snapshot?.file?.name ?: file.name, file.path, file.directory,
                        snapshot?.file?.writable ?: file.writable, snapshot?.fingerprint ?: old?.fingerprint,
                        if (snapshot != null) now() else old?.verifiedAt,
                        if (snapshot != null) IndexedSource.AVAILABLE else IndexedSource.READ_FAILED,
                        if (snapshot != null) snapshot.file.lastModified else old?.lastModified,
                        if (snapshot != null) snapshot.file.size else old?.size)
                }
            }
            slot.gate.withLock {
                checkCurrent()
                val current = store.sources(vault).associateBy { it.uri }
                val seen = updates.mapTo(mutableSetOf()) { it.uri }
                val merged = updates.filter { (slot.saved[it.uri] ?: 0L) <= startRevision }.toMutableList()
                if (!discovered.partial) current.values.filter {
                    it.uri !in seen && (slot.saved[it.uri] ?: 0L) <= startRevision
                }.forEach { merged += it.copy(status = IndexedSource.MISSING) }
                val complete = !discovered.partial && !readFailed
                checkCurrent()
                store.commit(merged, IndexedScan(vault,
                    if (complete) IndexedScan.COMPLETE else IndexedScan.PARTIAL, now(),
                    if (complete) now() else priorScan?.metadataCheckedAt,
                    if (complete && full) now() else priorScan?.fullVerifiedAt))
                if (complete && slot.revision == startRevision) {
                    invalidations.clear(vault)
                    synchronized(slot) { slot.dirty = false }
                }
            }
        } catch (e: Exception) {
            withContext(NonCancellable) {
                slot.gate.withLock {
                    if (synchronized(slot) { slot.job == job || (slot.job == null && slot.generation == token + 1) }) {
                        runCatching { store.commit(emptyList(), IndexedScan(vault,
                            if (e is CancellationException) IndexedScan.INTERRUPTED else IndexedScan.FAILED,
                            metadataCheckedAt = priorScan?.metadataCheckedAt, fullVerifiedAt = priorScan?.fullVerifiedAt)) }
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
        try { prepareVault(vault) }
        catch (e: Exception) {
            synchronized(slot) {
                slot.dirty = true
                if (slot.coordinator?.isActive == true) slot.pendingDirty = true
            }
            try { invalidations.mark(vault) } catch (markerFailure: Exception) { e.addSuppressed(markerFailure) }
            throw e
        }
        slot.gate.withLock {
            slot.saved[snapshot.file.uri] = ++slot.revision
            try {
                val old = store.sources(vault).firstOrNull { it.uri == snapshot.file.uri }
                store.commit(listOf(IndexedSource(vault, snapshot.file.uri, snapshot.file.name,
                    path ?: old?.path ?: snapshot.file.name, directory, snapshot.file.writable,
                    snapshot.fingerprint, now(), lastModified = snapshot.file.lastModified, size = snapshot.file.size)))
            } catch (e: Exception) {
                synchronized(slot) {
                    slot.dirty = true
                    if (slot.coordinator?.isActive == true) slot.pendingDirty = true
                }
                try { invalidations.mark(vault) } catch (markerFailure: Exception) { e.addSuppressed(markerFailure) }
                throw e
            }
        }
    }
}

/** Independent from Room so a failed index transaction can be repaired after process restart. */
interface IndexInvalidations {
    fun contains(vault: String): Boolean
    fun mark(vault: String)
    fun clear(vault: String)
}
class MemoryIndexInvalidations : IndexInvalidations {
    private val vaults = ConcurrentHashMap.newKeySet<String>()
    override fun contains(vault: String) = vault in vaults
    override fun mark(vault: String) { vaults.add(vault) }
    override fun clear(vault: String) { vaults.remove(vault) }
}
private class PreferenceIndexInvalidations(context: Context) : IndexInvalidations {
    private val preferences = context.getSharedPreferences("index-invalidations", Context.MODE_PRIVATE)
    override fun contains(vault: String) = preferences.getBoolean(vault, false)
    override fun mark(vault: String) { check(preferences.edit().putBoolean(vault, true).commit()) }
    override fun clear(vault: String) { check(preferences.edit().remove(vault).commit()) }
}
