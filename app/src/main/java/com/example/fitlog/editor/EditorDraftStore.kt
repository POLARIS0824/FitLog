package com.example.fitlog.editor

import android.util.AtomicFile
import com.example.fitlog.data.vault.fingerprint
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
    val vault: String,
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
)

interface Drafts {
    suspend fun read(vault: String, target: String): EditorDraft?
    suspend fun save(draft: EditorDraft)
    suspend fun remove(vault: String, target: String)
    suspend fun backup(vault: String, target: String, bytes: ByteArray)
}

class EditorDraftStore(private val directory: File) : Drafts {
    private val lock = Mutex()
    private fun file(vault: String, target: String, suffix: String = ".json") =
        AtomicFile(File(directory, fingerprint("$vault\n$target".toByteArray()) + suffix))

    override suspend fun read(vault: String, target: String): EditorDraft? = withContext(Dispatchers.IO) {
        lock.withLock {
            val file = file(vault, target)
            if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) null
            else Json.decodeFromString<EditorDraft>(file.openRead().use { it.readBytes().toString(Charsets.UTF_8) })
                .also { require(it.vault == vault && it.target == target) }
        }
    }

    private fun write(file: AtomicFile, bytes: ByteArray) {
        check(directory.exists() || directory.mkdirs())
        val stream = file.startWrite()
        try { stream.write(bytes); file.finishWrite(stream) }
        catch (e: Exception) { file.failWrite(stream); throw e }
    }

    override suspend fun save(draft: EditorDraft) = withContext(Dispatchers.IO) {
        lock.withLock { write(file(draft.vault, draft.target), Json.encodeToString(draft).toByteArray()) }
    }
    override suspend fun remove(vault: String, target: String) = withContext(Dispatchers.IO) {
        lock.withLock { file(vault, target).delete() }
    }
    override suspend fun backup(vault: String, target: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        lock.withLock { write(file(vault, target, ".recovery"), bytes) }
    }
}
