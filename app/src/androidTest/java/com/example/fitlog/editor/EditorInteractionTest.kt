package com.example.fitlog.editor

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.example.fitlog.R
import com.example.fitlog.data.vault.*
import com.example.fitlog.navigation.FitLogRoute
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Real BasicTextField input and formatting must share the platform undo stack. */
class EditorInteractionTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun button(id: Int) = compose.onNodeWithText(context.getString(id))

    @Test fun typingFormattingUndoRedoAndCancelledLink() {
        lateinit var vm: EditorViewModel
        compose.setContent {
            val store = remember { ViewModelStore() }
            vm = remember { EditorViewModel(FitLogRoute.Editor("test", date = "2026-09-29"), TestDocuments(), TestDrafts()).also { store.put("editor", it) } }
            DisposableEffect(Unit) { onDispose { store.clear() } }
            MaterialTheme { EditorScreen(vm, {}) }
        }
        compose.waitUntil { !vm.loading }
        compose.onNode(hasSetTextAction()).performClick().performTextInput("卧推💪")
        button(R.string.editor_format).performClick()
        button(R.string.editor_h2).performClick()
        compose.runOnIdle { assertEquals("## 卧推💪", vm.text.text.toString()) }
        button(R.string.editor_undo).performClick()
        compose.runOnIdle { assertEquals("卧推💪", vm.text.text.toString()) }
        button(R.string.editor_undo).performClick()
        compose.runOnIdle { assertEquals("", vm.text.text.toString()) }
        button(R.string.editor_redo).performClick()
        button(R.string.editor_redo).performClick()
        compose.runOnIdle { assertEquals("## 卧推💪", vm.text.text.toString()) }
        button(R.string.editor_format).performClick()
        button(R.string.editor_link).performClick()
        button(R.string.editor_cancel).performClick()
        compose.runOnIdle { assertEquals("## 卧推💪", vm.text.text.toString()) }
    }
}

private class TestDocuments : MarkdownDocuments {
    private var snapshot = MarkdownSnapshot(MarkdownFile("file", "2026-09-29.md", "2026-09-29.md", true), "", "", false)
    override suspend fun scan(vault: String) = MarkdownScan(emptyList(), false)
    override suspend fun find(vault: String, name: String): MarkdownFile? = null
    override suspend fun read(uri: String) = snapshot
    override suspend fun create(vault: String, name: String) = snapshot.file
    override suspend fun write(uri: String, text: String, bom: Boolean, expected: String): MarkdownSnapshot {
        snapshot = snapshot.copy(text = text, fingerprint = text)
        return snapshot
    }
}
private class TestDrafts : Drafts {
    override suspend fun read(vault: String, target: String): EditorDraft? = null
    override suspend fun save(draft: EditorDraft) = Unit
    override suspend fun remove(vault: String, target: String) = Unit
    override suspend fun backup(vault: String, target: String, bytes: ByteArray) = Unit
}
