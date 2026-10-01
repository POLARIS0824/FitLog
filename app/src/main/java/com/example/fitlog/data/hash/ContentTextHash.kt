package com.example.fitlog.data.hash

import java.security.MessageDigest

const val CONTENT_TEXT_HASH_VERSION = 1

/** Normalizes raw content once; byte fingerprints used for write conflicts remain independent. */
fun normalizeContentText(rawText: String): String = normalizeLineEndings(rawText.removePrefix("\uFEFF"))

/** Evidence uses this without stripping a BOM from the start of an excerpt. */
fun normalizeLineEndings(text: String): String = text.replace("\r\n", "\n").replace("\r", "\n")

/** Text and digest always describe the same normalized UTF-16 snapshot. */
class ContentTextSnapshot private constructor(
    val text: String,
    val hash: String,
    val hashVersion: Int,
) {
    companion object {
        fun fromRawText(rawText: String): ContentTextSnapshot {
            val normalized = normalizeContentText(rawText)
            return ContentTextSnapshot(normalized, sha256Utf8(normalized), CONTENT_TEXT_HASH_VERSION)
        }
    }
}

fun contentTextHash(rawText: String): String = ContentTextSnapshot.fromRawText(rawText).hash

/** Already normalized text: encoding and digest only, never another normalization pass. */
private fun sha256Utf8(text: String): String = MessageDigest.getInstance("SHA-256")
    .digest(text.toByteArray(Charsets.UTF_8))
    .joinToString("") { "%02x".format(it.toInt() and 0xff) }
