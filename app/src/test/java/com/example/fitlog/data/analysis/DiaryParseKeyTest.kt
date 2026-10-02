package com.example.fitlog.data.analysis

import com.example.fitlog.data.hash.CONTENT_TEXT_HASH_VERSION
import com.example.fitlog.data.hash.contentTextHash
import org.junit.Assert.*
import org.junit.Test

class DiaryParseKeyTest {
    @Test fun reuseRequiresEverySourceAndVersionDimension() {
        val source = SourceKey("00000000-0000-4000-8000-000000000001", "daily/note.md")
        val key = DiaryParseKey(source, contentTextHash("a\n"), CONTENT_TEXT_HASH_VERSION, "fixture-v1")
        assertEquals(key, DiaryParseKey(source, contentTextHash("\uFEFFa\r"), CONTENT_TEXT_HASH_VERSION, "fixture-v1"))
        assertNotEquals(key, key.copy(sourceKey = source.copy(vaultId = "00000000-0000-4000-8000-000000000002")))
        assertNotEquals(key, key.copy(sourceKey = source.copy(relPath = "daily/another.md")))
        assertNotEquals(key, key.copy(contentHash = contentTextHash("changed")))
        assertNotEquals(key, key.copy(hashVersion = 2))
        assertNotEquals(key, key.copy(extractorVersion = "fixture-v2"))
    }

    @Test fun incompleteVersionMetadataIsRejected() {
        val key = DiaryParseKey(SourceKey("00000000-0000-4000-8000-000000000001", "note.md"), contentTextHash(""), 1, "v1")
        try { key.copy(hashVersion = 0); fail() } catch (_: IllegalArgumentException) { }
        try { key.copy(extractorVersion = " "); fail() } catch (_: IllegalArgumentException) { }
        try { key.copy(contentHash = ""); fail() } catch (_: IllegalArgumentException) { }
    }
}
