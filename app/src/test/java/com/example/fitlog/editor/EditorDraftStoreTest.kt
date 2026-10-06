package com.example.fitlog.editor

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], shadows = [AtomicFileRenameShadow::class])
class EditorDraftStoreTest {
    @get:Rule val folder = TemporaryFolder()
    @Test fun draftSurvivesNewStoreAndCannotCrossVaults() = runTest {
        val store = EditorDraftStore(folder.root)
        val draft = EditorDraft("vault-a", "today.md", null, "today.md", "卧推💪\r\n", 2, 4, 8, null, false, directory = "vault-a", vaultId = "00000000-0000-4000-8000-000000000001")
        store.save(draft)
        val reopened = EditorDraftStore(folder.root)
        assertEquals(draft, reopened.read("00000000-0000-4000-8000-000000000001", "today.md"))
        assertNull(reopened.read("00000000-0000-4000-8000-000000000002", "today.md"))
        reopened.remove("00000000-0000-4000-8000-000000000001", "today.md")
        assertNull(reopened.read("00000000-0000-4000-8000-000000000001", "today.md"))
        assertTrue(reopened.entries().isEmpty())
    }

    @Test fun enumerationDeduplicatesAliasesAndKeepsOriginalDirectoryAcrossDays() = runTest {
        val store = EditorDraftStore(folder.root)
        val draft = EditorDraft("vault", "old-day.md", "document", "old-day.md", "mine", 0, 0, 3, "old", false,
            directory = "content://provider/tree/root/document/sub", displayPath = "daily/old-day.md", updatedAt = 123, vaultId = "00000000-0000-4000-8000-000000000001")
        store.save(draft); store.save(draft.copy(target = "document"))
        val entry = store.entries().single()
        assertEquals(draft.directory, entry.draft!!.directory)
        assertEquals(123, entry.draft.updatedAt)
        store.deleteEntry(entry.id)
        assertTrue(store.entries().isEmpty())
    }

    @Test fun damagedRecordDoesNotHideValidDraft() = runTest {
        val store = EditorDraftStore(folder.root)
        val valid = EditorDraft("content://test/tree/a", "file", "file", "note.md", "keep me", 0, 0, 1, null, false,
            directory = "content://test/tree/a", vaultId = "00000000-0000-4000-8000-000000000001")
        store.save(valid)
        val missingIdentity = kotlinx.serialization.json.Json.encodeToString(EditorDraft.serializer(), valid)
            .replace(",\"vaultId\":\"${valid.vaultId}\"", "")
        File(folder.root, "damaged.json").writeText(missingIdentity)
        val entries = store.entries()
        assertEquals(2, entries.size)
        assertEquals(valid, entries.single { !it.damaged }.draft)
        assertTrue(entries.single { it.damaged }.damaged)
    }

    @Test fun pendingBackupSurvivesRetryUntilVerifiedSuccess() = runTest {
        val store = EditorDraftStore(folder.root)
        val before = EditorDraft("vault", "file", "file", "note.md", "original", 0, 0, 0, "hash", false, directory = "vault", vaultId = "00000000-0000-4000-8000-000000000001")
        store.prepareBackup(before)
        store.prepareBackup(before.copy(text = "truncated"))
        assertEquals("original", store.entries().single().draft!!.text)
        assertTrue(store.entries().single().pending)
        store.completeBackup("00000000-0000-4000-8000-000000000001", "file")
        assertFalse(store.entries().single().pending)
        store.prepareBackup(before.copy(text = "next preimage"))
        assertEquals("next preimage", store.entries().single().draft!!.text)
    }
}
