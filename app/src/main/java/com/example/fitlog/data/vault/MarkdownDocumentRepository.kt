package com.example.fitlog.data.vault

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLong
import androidx.core.net.toUri

data class MarkdownFile(val uri: String, val name: String, val path: String, val writable: Boolean,
    val directory: String? = null, val lastModified: Long? = null, val size: Long? = null)
data class MarkdownSnapshot(val file: MarkdownFile, val text: String, val fingerprint: String, val bom: Boolean,
    /** Logical read order, allocated before I/O; distinct even within the same millisecond. */
    val readAt: Long = nextMarkdownReadOrder())
data class MarkdownScan(val files: List<MarkdownFile>, val partial: Boolean)

private val lastMarkdownReadOrder = AtomicLong()

/** A process-wide order that continues increasing if the wall clock repeats or moves backwards. */
internal fun nextMarkdownReadOrder(now: Long = System.currentTimeMillis()): Long =
    lastMarkdownReadOrder.updateAndGet { previous -> maxOf(now, previous + 1) }

interface MarkdownDocuments {
    suspend fun scan(vault: String): MarkdownScan
    suspend fun read(uri: String): MarkdownSnapshot
}

/** All SAF operations stay on document URIs, never filesystem paths. */
class MarkdownDocumentRepository(context: Context,
    private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO) : MarkdownDocuments {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver

    private fun root(vault: String): Uri {
        val tree = vault.toUri()
        val id = try { DocumentsContract.getDocumentId(tree) }
            catch (_: IllegalArgumentException) { DocumentsContract.getTreeDocumentId(tree) }
        return DocumentsContract.buildDocumentUriUsingTree(tree, id)
    }

    private fun children(directory: Uri): List<Pair<MarkdownFile, Boolean>> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(directory, DocumentsContract.getDocumentId(directory))
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
                        false,
                        directory.toString(),
                        if (cursor.isNull(4)) null else cursor.getLong(4).takeIf { it > 0 },
                        if (cursor.isNull(5)) null else cursor.getLong(5).takeIf { it >= 0 }) to
                        (cursor.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR))
                }
            }
        } ?: throw IOException()
    }

    override suspend fun scan(vault: String): MarkdownScan = withContext(ioDispatcher) {
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

    private fun metadata(uri: Uri): MarkdownFile = resolver.query(uri, arrayOf(
        DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_FLAGS,
        DocumentsContract.Document.COLUMN_LAST_MODIFIED, DocumentsContract.Document.COLUMN_SIZE,
    ), null, null, null)?.use { cursor ->
        if (!cursor.moveToFirst()) throw IOException()
        val name = cursor.getString(0) ?: throw IOException()
        MarkdownFile(uri.toString(), name, name,
            false,
            lastModified = if (cursor.isNull(2)) null else cursor.getLong(2).takeIf { it > 0 },
            size = if (cursor.isNull(3)) null else cursor.getLong(3).takeIf { it >= 0 })
    } ?: throw IOException()

    override suspend fun read(uri: String): MarkdownSnapshot = withContext(ioDispatcher) {
        // Observation time is taken before the read: a slow read of older bytes must not look newer.
        val observedAt = nextMarkdownReadOrder()
        val document = Uri.parse(uri)
        val file = metadata(document)
        val bytes = resolver.openInputStream(document)?.use { it.readBytes() } ?: throw IOException()
        val bom = bytes.size >= 3 && bytes[0] == 0xef.toByte() && bytes[1] == 0xbb.toByte() && bytes[2] == 0xbf.toByte()
        val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes, if (bom) 3 else 0, bytes.size - if (bom) 3 else 0)).toString()
        MarkdownSnapshot(file, text, fingerprint(bytes), bom, observedAt)
    }


}

internal fun fingerprint(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
    .joinToString("") { "%02x".format(it) }
