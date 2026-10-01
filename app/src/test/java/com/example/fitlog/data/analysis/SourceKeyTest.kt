package com.example.fitlog.data.analysis

import org.junit.Assert.*
import org.junit.Test

class SourceKeyTest {
    @Test fun identityKeepsVaultPathCaseUnicodeAndSpaces() {
        val key = SourceKey("vault-a", "daily/My Note.md")
        assertEquals("daily/My Note.md", key.relPath)
        assertNotEquals(key, SourceKey("vault-b", key.relPath))
        assertNotEquals(key, SourceKey("vault-a", "daily/my note.md"))
        assertNotEquals(SourceKey("vault-a", "\u00e9.md"), SourceKey("vault-a", "e\u0301.md"))
        assertEquals(" note.md ", SourceKey("vault-a", " note.md ").relPath)
    }

    @Test fun invalidPathsCannotBecomeSourceKeys() {
        listOf("", " ", "/daily/a.md", "daily\\a.md", "daily//a.md", "daily/", "../a.md", "daily/../a.md", "./a.md", "daily/./a.md").forEach {
            try { SourceKey("vault", it); fail("Accepted invalid path: $it") } catch (_: IllegalArgumentException) { }
        }
        try { SourceKey(" ", "a.md"); fail() } catch (_: IllegalArgumentException) { }
    }
}
