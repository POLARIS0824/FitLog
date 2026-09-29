@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.example.fitlog.editor

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.text.TextRange
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MarkdownEditCommandsTest {
    private fun apply(text: String, edit: MarkdownEditCommands.Edit) = text.replaceRange(edit.start, edit.end, edit.replacement)

    @Test fun headingDoesNotIncludeNextLineAtSelectionBoundary() {
        val value = "卧推\n深蹲\n备注"
        assertEquals("## 卧推\n深蹲\n备注", apply(value, MarkdownEditCommands.heading(value, TextRange(0, 3), 2)))
    }
    @Test fun emptyDocumentAndEmptySelection() {
        val result = MarkdownEditCommands.wrap("", TextRange(0), "**")
        assertEquals("****", result.replacement)
        assertEquals(TextRange(2), result.selection)
        assertEquals("# ", MarkdownEditCommands.heading("", TextRange(0), 1).replacement)
    }
    @Test fun taskListsPreserveCompletedItemsInMixedSelection() {
        val value = "- [x] 卧推\n深蹲"
        assertEquals("- [x] 卧推\n- [ ] 深蹲", apply(value, MarkdownEditCommands.list(value, TextRange(0, value.length), true)))
        val checked = "- [x] 卧推\n- [ ] 深蹲"
        assertEquals("卧推\n深蹲", apply(checked, MarkdownEditCommands.list(checked, TextRange(checked.length, 0), true)))
    }
    @Test fun crlfAndUnknownMarkdownAreUntouchedOutsideSelection() {
        val value = "---\r\n[[note]]\r\n卧推\r\n"
        val offset = value.indexOf("卧推")
        assertEquals("---\r\n[[note]]\r\n- 卧推\r\n", apply(value, MarkdownEditCommands.list(value, TextRange(offset), false)))
    }
    @Test fun formattingAndProgrammaticTypingShareUndoAndRestoreSelection() {
        val state = TextFieldState()
        state.edit { append("卧推💪"); selection = TextRange(length) }
        state.edit { selection = TextRange(0, length) }
        val originalSelection = state.selection
        val edit = MarkdownEditCommands.wrap(state.text.toString(), state.selection, "**")
        state.edit { replace(edit.start, edit.end, edit.replacement); selection = edit.selection }
        assertEquals("**卧推💪**", state.text.toString())
        state.undoState.undo()
        assertEquals("卧推💪", state.text.toString())
        assertEquals(originalSelection, state.selection)
        state.undoState.undo()
        assertEquals("", state.text.toString())
        state.undoState.redo(); state.undoState.redo()
        assertEquals("**卧推💪**", state.text.toString())
    }
}
