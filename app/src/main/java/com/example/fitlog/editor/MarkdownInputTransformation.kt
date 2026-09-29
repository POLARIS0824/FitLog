@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.example.fitlog.editor

import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer

/** Apply Enter handling in the same input transaction so it remains one undo operation. */
class MarkdownInputTransformation(private val isComposing: () -> Boolean) : InputTransformation {
    override fun TextFieldBuffer.transformInput() {
        if (isComposing() || !originalSelection.collapsed || changes.changeCount != 1) return
        val before = changes.getOriginalRange(0)
        val after = changes.getRange(0)
        if (!before.collapsed || before.start != originalSelection.end) return
        val inserted = asCharSequence().subSequence(after.min, after.max).toString()
        val edit = MarkdownEditCommands.continueList(originalText.toString(), originalSelection, inserted) ?: return
        replace(edit.start, edit.end, edit.replacement)
        selection = edit.selection
    }
}
