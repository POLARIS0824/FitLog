package com.example.fitlog.editor

import android.content.ContentResolver
import android.net.Uri
import com.example.fitlog.data.vault.fingerprint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

class RecoveryExporter(private val resolver: ContentResolver) {
    suspend fun export(uri: Uri, draft: EditorDraft) = withContext(Dispatchers.IO) {
        val bytes = (if (draft.bom) byteArrayOf(0xef.toByte(), 0xbb.toByte(), 0xbf.toByte()) else byteArrayOf()) +
            draft.text.toByteArray(Charsets.UTF_8)
        resolver.openOutputStream(uri, "wt")?.use { it.write(bytes); it.flush() } ?: throw IOException()
        val actual = resolver.openInputStream(uri)?.use { it.readBytes() } ?: throw IOException()
        if (fingerprint(bytes) != fingerprint(actual)) throw IOException()
    }
}
