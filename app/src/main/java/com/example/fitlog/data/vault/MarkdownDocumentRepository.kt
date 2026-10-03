package com.example.fitlog.data.vault

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Process
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import androidx.core.net.toUri

data class MarkdownFile(val uri: String, val name: String, val path: String, val writable: Boolean,
    val directory: String? = null, val lastModified: Long? = null, val size: Long? = null)
data class MarkdownSnapshot(val file: MarkdownFile, val text: String, val fingerprint: String, val bom: Boolean)
data class MarkdownScan(val files: List<MarkdownFile>, val partial: Boolean)
class DocumentConflict : IOException()
class NameCollision(val file: MarkdownFile) : IOException()
class UnexpectedDocumentName : IOException()

interface MarkdownDocuments {
    suspend fun scan(vault: String): MarkdownScan
    suspend fun find(vault: String, name: String): MarkdownFile?
    suspend fun read(uri: String): MarkdownSnapshot
    suspend fun create(vault: String, name: String): MarkdownFile
    suspend fun write(uri: String, text: String, bom: Boolean, expected: String): MarkdownSnapshot
}

/** All SAF operations stay on document URIs, never filesystem paths. */
class MarkdownDocumentRepository(context: Context) : MarkdownDocuments, DiaryDirectories {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver
    private companion object { val writes = Mutex() }

    private fun root(vault: String): Uri {
        val tree = vault.toUri()
        val id = try { DocumentsContract.getDocumentId(tree) }
            catch (_: IllegalArgumentException) { DocumentsContract.getTreeDocumentId(tree) }
        return DocumentsContract.buildDocumentUriUsingTree(tree, id)
    }

    override suspend fun resolveDirectory(vault: String, path: List<String>): String = withContext(Dispatchers.IO) {
        var directory = root(vault).toString()
        for (name in path) {
            require(name.isNotBlank() && name != "." && name != "..")
            directory = directories(directory).singleOrNull { it.name == name }?.uri ?: throw IOException()
        }
        // Query even the root: a stored setting is not proof of continued access.
        directories(directory)
        directory
    }

    override suspend fun directories(directory: String): List<DiaryDirectory> = withContext(Dispatchers.IO) {
        children(root(directory)).filter { it.second && !it.first.name.startsWith('.') }
            .map { DiaryDirectory(it.first.uri, it.first.name) }.sortedBy { it.name }
    }

    override suspend fun canCreate(directory: String): Boolean = withContext(Dispatchers.IO) {
        val uri = root(directory)
        val info = AndroidSafDirectoryAccessor(appContext).queryDirectoryInfo(uri)
        info?.isDirectory == true && info.supportsCreate &&
            appContext.checkUriPermission(uri, Process.myPid(), Process.myUid(), Intent.FLAG_GRANT_WRITE_URI_PERMISSION) == PackageManager.PERMISSION_GRANTED
    }

    private fun children(directory: Uri): List<Pair<MarkdownFile, Boolean>> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(directory, DocumentsContract.getDocumentId(directory))
        val writeGranted = appContext.checkUriPermission(directory, Process.myPid(), Process.myUid(),
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION) == PackageManager.PERMISSION_GRANTED
        return resolver.query(uri, arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_FLAGS,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_SIZE,
        ), null, null, null)?.use { cursor ->
            // Cloud providers may return a temporary incomplete listing while loading.
            if (cursor.extras.getBoolean(DocumentsContract.EXTRA_LOADING, false) ||
                cursor.extras.getString(DocumentsContract.EXTRA_ERROR) != null) throw IOException()
            buildList {
                while (cursor.moveToNext()) {
                    val child = DocumentsContract.buildDocumentUriUsingTree(directory, cursor.getString(0))
                    val name = cursor.getString(1) ?: throw IOException()
                    add(MarkdownFile(child.toString(), name, name,
                        cursor.getInt(3) and DocumentsContract.Document.FLAG_SUPPORTS_WRITE != 0 && writeGranted,
                        directory.toString(),
                        if (cursor.isNull(4)) null else cursor.getLong(4).takeIf { it > 0 },
                        if (cursor.isNull(5)) null else cursor.getLong(5).takeIf { it >= 0 }) to
                        (cursor.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR))
                }
            }
        } ?: throw IOException()
    }

    override suspend fun scan(vault: String): MarkdownScan = withContext(Dispatchers.IO) {
        val scanContext = currentCoroutineContext()
        val found = linkedMapOf<String, MarkdownFile>()
        val visited = mutableSetOf<String>()
        var partial = false
        fun visit(directory: Uri, path: String, isRoot: Boolean) {
            scanContext.ensureActive()
            if (!visited.add(directory.toString())) return
            val entries = try { children(directory) } catch (e: Exception) {
                if (e is CancellationException || isRoot) throw e
                partial = true
                return
            }
            entries.forEach { (file, isDirectory) ->
                scanContext.ensureActive()
                val relative = path + file.name
                if (isDirectory) {
                    if (!file.name.startsWith('.')) visit(Uri.parse(file.uri), "$relative/", false)
                } else if (file.name.substringAfterLast('.', "").lowercase() in setOf("md", "markdown")) {
                    found[file.uri] = file.copy(path = relative, directory = directory.toString())
                }
            }
        }
        visit(root(vault), "", true)
        MarkdownScan(found.values.sortedBy { it.path }, partial)
    }

    override suspend fun find(vault: String, name: String): MarkdownFile? = withContext(Dispatchers.IO) {
        children(root(vault)).firstOrNull { it.first.name == name }?.first
    }

    private fun metadata(uri: Uri): MarkdownFile = resolver.query(uri, arrayOf(
        DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_FLAGS,
        DocumentsContract.Document.COLUMN_LAST_MODIFIED, DocumentsContract.Document.COLUMN_SIZE,
    ), null, null, null)?.use { cursor ->
        if (!cursor.moveToFirst()) throw IOException()
        val name = cursor.getString(0) ?: throw IOException()
        MarkdownFile(uri.toString(), name, name,
            cursor.getInt(1) and DocumentsContract.Document.FLAG_SUPPORTS_WRITE != 0 &&
                appContext.checkUriPermission(uri, Process.myPid(), Process.myUid(), Intent.FLAG_GRANT_WRITE_URI_PERMISSION) == PackageManager.PERMISSION_GRANTED,
            lastModified = if (cursor.isNull(2)) null else cursor.getLong(2).takeIf { it > 0 },
            size = if (cursor.isNull(3)) null else cursor.getLong(3).takeIf { it >= 0 })
    } ?: throw IOException()

    override suspend fun read(uri: String): MarkdownSnapshot = withContext(Dispatchers.IO) {
        val document = Uri.parse(uri)
        val file = metadata(document)
        val bytes = resolver.openInputStream(document)?.use { it.readBytes() } ?: throw IOException()
        val bom = bytes.size >= 3 && bytes[0] == 0xef.toByte() && bytes[1] == 0xbb.toByte() && bytes[2] == 0xbf.toByte()
        val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes, if (bom) 3 else 0, bytes.size - if (bom) 3 else 0)).toString()
        MarkdownSnapshot(file, text, fingerprint(bytes), bom)
    }

    override suspend fun create(vault: String, name: String): MarkdownFile = writes.withLock {
        withContext(Dispatchers.IO) {
            val directory = root(vault)
            val existing = children(directory)
            existing.firstOrNull { it.first.name == name }?.let { throw NameCollision(it.first) }
            val uri = DocumentsContract.createDocument(resolver, directory, "text/markdown", name) ?: throw IOException()
            val file = metadata(uri)
            if (existing.any { it.first.uri == file.uri }) throw NameCollision(file)
            // Never write to a provider-renamed target without the user's knowledge.
            if (file.name != name) throw UnexpectedDocumentName()
            if (read(file.uri).text.isNotEmpty()) throw DocumentConflict()
            file
        }
    }

    override suspend fun write(uri: String, text: String, bom: Boolean, expected: String): MarkdownSnapshot = writes.withLock {
        withContext(Dispatchers.IO) {
            val before = read(uri)
            if (before.fingerprint != expected) throw DocumentConflict()
            if (!before.file.writable) throw SecurityException()
            val bytes = (if (bom) byteArrayOf(0xef.toByte(), 0xbb.toByte(), 0xbf.toByte()) else byteArrayOf()) + text.toByteArray(Charsets.UTF_8)
            resolver.openOutputStream(Uri.parse(uri), "wt")?.use { it.write(bytes); it.flush() } ?: throw IOException()
            val after = read(uri)
            if (after.fingerprint != fingerprint(bytes)) throw IOException()
            after
        }
    }
}

internal fun fingerprint(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
    .joinToString("") { "%02x".format(it) }
