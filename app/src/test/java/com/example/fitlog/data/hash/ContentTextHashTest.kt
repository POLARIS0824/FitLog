package com.example.fitlog.data.hash

import com.example.fitlog.data.vault.fingerprint
import org.junit.Assert.*
import org.junit.Test

class ContentTextHashTest {
    @Test fun lfCrLfCrAndMixedNewlinesHaveOneContentVersion() {
        val expected = contentTextHash("a\nb\nc\n")
        listOf("a\r\nb\r\nc\r\n", "a\rb\rc\r", "a\r\nb\rc\n").forEach {
            assertEquals(expected, contentTextHash(it))
        }
    }

    @Test fun onlyOneLeadingBomIsRemovedAndSnapshotIsStable() {
        val raw = "\uFEFF\uFEFFa\r\nb\uFEFFc\r"
        val first = ContentTextSnapshot.fromRawText(raw)
        val again = ContentTextSnapshot.fromRawText(raw)
        assertEquals("\uFEFFa\nb\uFEFFc\n", first.text)
        assertEquals(first.text, again.text)
        assertEquals(first.hash, again.hash)
        assertEquals(contentTextHash(raw), first.hash)
        assertEquals(CONTENT_TEXT_HASH_VERSION, first.hashVersion)
        assertNotEquals(contentTextHash("a\nbc\n"), first.hash)
        assertEquals(contentTextHash("a"), contentTextHash("\uFEFFa"))
        assertNotEquals(contentTextHash("a"), contentTextHash("a\uFEFF"))
    }

    @Test fun meaningfulWhitespaceNotationAndUnicodeRemainSignificant() {
        assertNotEquals(contentTextHash("training"), contentTextHash("training "))
        assertNotEquals(contentTextHash("40kg 2x7"), contentTextHash("40kg 2\u00d77"))
        assertNotEquals(contentTextHash("\u00e9"), contentTextHash("e\u0301"))
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", contentTextHash(""))
    }

    @Test fun byteFingerprintStillDetectsBomAndNewlineChanges() {
        val lf = "training\n".toByteArray(Charsets.UTF_8)
        val crlf = "training\r\n".toByteArray(Charsets.UTF_8)
        val bom = "\uFEFFtraining\n".toByteArray(Charsets.UTF_8)
        assertNotEquals(fingerprint(lf), fingerprint(crlf))
        assertNotEquals(fingerprint(lf), fingerprint(bom))
        assertEquals(fingerprint(lf), contentTextHash("training\n"))
    }
}
