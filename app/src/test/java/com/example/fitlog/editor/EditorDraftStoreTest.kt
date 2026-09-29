package com.example.fitlog.editor

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
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
}
