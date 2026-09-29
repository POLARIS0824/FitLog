package com.example.fitlog.data.vault

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import java.io.FileNotFoundException

/**
 * 目录元数据信息。
 */
data class DirectoryInfo(
    val isDirectory: Boolean,
    val supportsCreate: Boolean,
    val displayName: String? = null,
)

/**
 * SAF 目录底层操作抽象，便于单元测试替换验证状态映射。
 */
interface SafDirectoryAccessor {
    fun hasPersistedReadPermission(uri: Uri): Boolean
    fun hasPersistedWritePermission(uri: Uri): Boolean
    fun queryDirectoryInfo(uri: Uri): DirectoryInfo?
    fun takePersistablePermission(uri: Uri): Result<Unit> = Result.success(Unit)
}

/**
 * 基于 Android DocumentsContract 和 ContentResolver 的标准实现。
 */
class AndroidSafDirectoryAccessor(
    private val context: Context,
) : SafDirectoryAccessor {

    private val contentResolver get() = context.contentResolver

    override fun hasPersistedReadPermission(uri: Uri): Boolean {
        return contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isReadPermission
        }
    }

    override fun hasPersistedWritePermission(uri: Uri): Boolean {
        return contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isWritePermission
        }
    }

    override fun takePersistablePermission(uri: Uri): Result<Unit> {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        return runCatching {
            try {
                contentResolver.takePersistableUriPermission(uri, flags)
            } catch (_: SecurityException) {
                // Read-only providers can still be connected for browsing.
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    }

    override fun queryDirectoryInfo(uri: Uri): DirectoryInfo? {
        if (!DocumentsContract.isTreeUri(uri)) {
            return null
        }

        val documentId = try {
            try { DocumentsContract.getDocumentId(uri) }
            catch (_: IllegalArgumentException) { DocumentsContract.getTreeDocumentId(uri) }
        } catch (e: IllegalArgumentException) {
            return null
        }

        val documentUri = DocumentsContract.buildDocumentUriUsingTree(uri, documentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_FLAGS,
        )

        return try {
            contentResolver.query(documentUri, projection, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return null

                val nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeTypeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val flagsIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_FLAGS)

                val displayName = if (nameIndex >= 0) cursor.getString(nameIndex) else null
                val mimeType = if (mimeTypeIndex >= 0) cursor.getString(mimeTypeIndex) else null
                val flags = if (flagsIndex >= 0) cursor.getInt(flagsIndex) else 0

                val isDirectory = mimeType == DocumentsContract.Document.MIME_TYPE_DIR
                val supportsCreate = (flags and DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE) != 0

                DirectoryInfo(
                    isDirectory = isDirectory,
                    supportsCreate = supportsCreate,
                    displayName = displayName,
                )
            }
        } catch (e: SecurityException) {
            // 权限拒绝向外抛出，供上层捕获映射为 NeedsReauthorization
            throw e
        } catch (e: FileNotFoundException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}
