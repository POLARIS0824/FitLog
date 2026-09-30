package com.example.fitlog.editor

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.example.fitlog.data.vault.fingerprint
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], shadows = [AtomicFileRenameShadow::class])
class EditorDraftStoreTest {
    @get:Rule val folder = TemporaryFolder()
    @Test fun draftSurvivesNewStoreAndCannotCrossVaults() = runTest {
        val store = EditorDraftStore(folder.root)
        val draft = EditorDraft("vault-a", "today.md", null, "today.md", "卧推💪\r\n", 2, 4, 8, null, false)
        store.save(draft)
        val reopened = EditorDraftStore(folder.root)
        assertEquals(draft, reopened.read("vault-a", "today.md"))
        assertNull(reopened.read("vault-b", "today.md"))
        reopened.backup("vault-a", "today.md", "original".toByteArray())
        reopened.remove("vault-a", "today.md")
        assertNull(reopened.read("vault-a", "today.md"))
        assertEquals("original", folder.root.listFiles()!!.single { it.extension == "recovery" }.readText())
    }

    @Test fun enumerationDeduplicatesAliasesAndKeepsOriginalDirectoryAcrossDays() = runTest {
        val store = EditorDraftStore(folder.root)
        val draft = EditorDraft("vault", "old-day.md", "document", "old-day.md", "mine", 0, 0, 3, "old", false,
            directory = "content://provider/tree/root/document/sub", displayPath = "daily/old-day.md", updatedAt = 123)
        store.save(draft); store.save(draft.copy(target = "document"))
        val entry = store.entries().single()
        assertEquals(draft.directory, entry.draft!!.originalDirectory())
        assertEquals(123, entry.draft.updatedAt)
        store.deleteEntry(entry.id)
        assertTrue(store.entries().isEmpty())
    }

    @Test fun legacyMetadataIsReadWithoutGuessingDocumentParentAndDamageIsIsolated() = runTest {
        val name = fingerprint("vault\ncontent://provider/document/file".toByteArray()) + ".json"
        File(folder.root, name).writeText("""{"vault":"vault","target":"content://provider/document/file","document":"content://provider/document/file","name":"note.md","text":"old text","selectionStart":0,"selectionEnd":0,"version":1,"fingerprint":null,"bom":false}""")
        File(folder.root, "broken.json").writeText("{")
        val entries = EditorDraftStore(folder.root).entries()
        assertEquals(2, entries.size)
        assertEquals("old text", entries.single { !it.damaged }.draft!!.text)
        assertNull(entries.single { !it.damaged }.draft!!.originalDirectory())
        assertTrue(entries.single { it.damaged }.damaged)
    }

    @Test fun pendingBackupSurvivesRetryUntilVerifiedSuccess() = runTest {
        val store = EditorDraftStore(folder.root)
        val before = EditorDraft("vault", "file", "file", "note.md", "original", 0, 0, 0, "hash", false, directory = "vault")
        store.prepareBackup(before, "original".toByteArray())
        store.prepareBackup(before.copy(text = "truncated"), "truncated".toByteArray())
        assertEquals("original", store.entries().single().draft!!.text)
        assertTrue(store.entries().single().pending)
        store.completeBackup("vault", "file")
        assertFalse(store.entries().single().pending)
        store.prepareBackup(before.copy(text = "next preimage"), "next preimage".toByteArray())
        assertEquals("next preimage", store.entries().single().draft!!.text)
    }

    @Test fun unknownLegacyBackupRemainsReadableAndExportable() = runTest {
        File(folder.root, "unknown.recovery").writeText("保留正文")
        val entry = EditorDraftStore(folder.root).entries().single()
        assertTrue(entry.backup)
        assertEquals("保留正文", entry.draft!!.text)
        assertNull(entry.draft.originalDirectory())
    }
}
