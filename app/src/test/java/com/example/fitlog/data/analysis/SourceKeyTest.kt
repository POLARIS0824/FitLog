package com.example.fitlog.data.analysis

import org.junit.Assert.*
import org.junit.Test

class SourceKeyTest {
    @Test fun uriAndNonCanonicalValuesCannotBecomeVaultIdentities() {
        listOf("vault-a", "content://test/tree/a", "1-1-1-1-1", "00000000-0000-4000-8000-00000000000A").forEach {
            try { SourceKey(it, "note.md"); fail("Accepted invalid vault identity: $it") }
            catch (_: IllegalArgumentException) { }
        }
    }
    @Test fun identityKeepsVaultPathCaseUnicodeAndSpaces() {
        val key = SourceKey("00000000-0000-4000-8000-000000000001", "daily/My Note.md")
        assertEquals("daily/My Note.md", key.relPath)
        assertNotEquals(key, SourceKey("00000000-0000-4000-8000-000000000002", key.relPath))
        assertNotEquals(key, SourceKey("00000000-0000-4000-8000-000000000001", "daily/my note.md"))
        assertNotEquals(SourceKey("00000000-0000-4000-8000-000000000001", "\u00e9.md"), SourceKey("00000000-0000-4000-8000-000000000001", "e\u0301.md"))
        assertEquals(" note.md ", SourceKey("00000000-0000-4000-8000-000000000001", " note.md ").relPath)
    }

    @Test fun invalidPathsCannotBecomeSourceKeys() {
        listOf("", " ", "/daily/a.md", "daily\\a.md", "daily//a.md", "daily/", "../a.md", "daily/../a.md", "./a.md", "daily/./a.md").forEach {
            try { SourceKey("00000000-0000-4000-8000-000000000001", it); fail("Accepted invalid path: $it") } catch (_: IllegalArgumentException) { }
        }
        try { SourceKey(" ", "a.md"); fail() } catch (_: IllegalArgumentException) { }
    }
}
