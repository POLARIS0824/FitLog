package com.example.fitlog.editor

import android.util.AtomicFile
import com.example.fitlog.data.vault.fingerprint
import com.example.fitlog.data.vault.requireVaultId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class EditorDraft(
    val vaultUri: String,
    val target: String,
    val document: String?,
    val name: String,
    val text: String,
    val selectionStart: Int,
    val selectionEnd: Int,
    val version: Long,
    val fingerprint: String?,
    val bom: Boolean,
    val originTarget: String = target,
    val directory: String,
    val displayPath: String? = null,
    val updatedAt: Long = 0,
    val recoveryId: String = "",
    val manualSave: Boolean = false,
    val restoredBackup: Boolean = false,
    val vaultId: String,
) {
    init { requireVaultId(vaultId) }
}

@Serializable
data class RecoveryBackup(val draft: EditorDraft, val pending: Boolean = true)

data class RecoveryEntry(
    val id: String,
    val draft: EditorDraft?,
    val backup: Boolean = false,
    val pending: Boolean = false,
    val damaged: Boolean = false,
)

internal fun EditorDraft.identity(): String = recoveryId.ifEmpty {
    fingerprint("$vaultId\n$originTarget".toByteArray(Charsets.UTF_8))
}

interface Drafts {
    suspend fun read(vaultId: String, target: String): EditorDraft?
    suspend fun save(draft: EditorDraft)
    suspend fun remove(vaultId: String, target: String)
    suspend fun entries(): List<RecoveryEntry> = emptyList()
    suspend fun entry(id: String): RecoveryEntry? = entries().firstOrNull { it.id == id }
    suspend fun deleteEntry(id: String) { error("Recovery enumeration unavailable") }
    suspend fun prepareBackup(draft: EditorDraft)
    suspend fun completeBackup(vaultId: String, document: String) = Unit
}

class EditorDraftStore(private val directory: File) : Drafts {
    private companion object { val lock = Mutex(); val json = Json { encodeDefaults = true } }
    private fun file(vaultId: String, target: String, suffix: String = ".json") =
        AtomicFile(File(directory, fingerprint("${requireVaultId(vaultId)}\n$target".toByteArray(Charsets.UTF_8)) + suffix))

    override suspend fun read(vaultId: String, target: String): EditorDraft? = withContext(Dispatchers.IO) {
        lock.withLock {
            val file = file(vaultId, target)
            if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) null
            else json.decodeFromString<EditorDraft>(file.openRead().use { it.readBytes().toString(Charsets.UTF_8) })
                .also { require(it.vaultId == vaultId && it.target == target) }
        }
    }

    private fun write(file: AtomicFile, bytes: ByteArray) {
        check(directory.exists() || directory.mkdirs())
        val stream = file.startWrite()
        try { stream.write(bytes); file.finishWrite(stream) }
        catch (e: Exception) { file.failWrite(stream); throw e }
        // AtomicFile reports some commit failures only through logging; never claim an unverified draft is durable.
        check(file.openRead().use { it.readBytes() }.contentEquals(bytes))
    }

    override suspend fun save(draft: EditorDraft) = withContext(Dispatchers.IO) {
        lock.withLock { write(file(draft.vaultId, draft.target), json.encodeToString(draft).toByteArray(Charsets.UTF_8)) }
    }
    override suspend fun remove(vaultId: String, target: String) = withContext(Dispatchers.IO) {
        lock.withLock { delete(file(vaultId, target)) }
    }
    override suspend fun prepareBackup(draft: EditorDraft) = withContext(Dispatchers.IO) {
        lock.withLock {
            val destination = file(draft.vaultId, requireNotNull(draft.document), ".backup.json")
            val old = if (destination.baseFile.exists() || File(destination.baseFile.path + ".bak").exists()) {
                json.decodeFromString<RecoveryBackup>(destination.openRead().use { it.readBytes().toString(Charsets.UTF_8) })
            } else null
            // An interrupted write's preimage is never replaced by a retry's damaged source.
            if (old?.pending != true) write(destination, json.encodeToString(RecoveryBackup(draft)).toByteArray(Charsets.UTF_8))
        }
    }

    override suspend fun completeBackup(vaultId: String, document: String) = withContext(Dispatchers.IO) {
        lock.withLock {
            val destination = file(vaultId, document, ".backup.json")
            val old = json.decodeFromString<RecoveryBackup>(destination.openRead().use { it.readBytes().toString(Charsets.UTF_8) })
            write(destination, json.encodeToString(old.copy(pending = false)).toByteArray(Charsets.UTF_8))
        }
    }

    private fun storedFiles(): List<File> {
        if (!directory.exists()) return emptyList()
        return requireNotNull(directory.listFiles()).map { f ->
            if (f.name.endsWith(".bak")) File(f.path.removeSuffix(".bak")) else f
        }.filter { it.name.endsWith(".json") }.distinctBy { it.name }
    }

    private fun readEntries(): List<Pair<File, RecoveryEntry>> {
        val files = storedFiles()
        val drafts = files.filter { it.name.endsWith(".json") && !it.name.endsWith(".backup.json") }.map { f ->
            f to runCatching {
                val d = json.decodeFromString<EditorDraft>(AtomicFile(f).openRead().use { it.readBytes().toString(Charsets.UTF_8) })
                require(file(d.vaultId, d.target).baseFile.name == f.name)
                RecoveryEntry("draft:${d.identity()}", d)
            }.getOrElse { RecoveryEntry("damaged:${f.name}", null, damaged = true) }
        }
        val backups = files.filter { it.name.endsWith(".backup.json") }.map { f ->
            f to runCatching {
                val b = json.decodeFromString<RecoveryBackup>(AtomicFile(f).openRead().use { it.readBytes().toString(Charsets.UTF_8) })
                require(file(b.draft.vaultId, requireNotNull(b.draft.document), ".backup.json").baseFile.name == f.name)
                RecoveryEntry("backup:${f.name}", b.draft, backup = true, pending = b.pending)
            }.getOrElse { RecoveryEntry("damaged:${f.name}", null, backup = true, damaged = true) }
        }
        return drafts + backups
    }

    override suspend fun entries(): List<RecoveryEntry> = withContext(Dispatchers.IO) {
        lock.withLock { readEntries().map { it.second }.groupBy { it.id }.values.map { copies ->
            copies.maxWith(compareBy<RecoveryEntry> { it.draft?.version ?: 0 }.thenBy { it.draft?.updatedAt ?: 0 })
        } }
    }

    override suspend fun deleteEntry(id: String) = withContext(Dispatchers.IO) {
        lock.withLock { readEntries().filter { it.second.id == id }.forEach { delete(AtomicFile(it.first)) } }
    }

    private fun delete(file: AtomicFile) {
        file.delete()
        check(listOf(file.baseFile, File(file.baseFile.path + ".bak"), File(file.baseFile.path + ".new")).none { it.exists() })
    }
}
