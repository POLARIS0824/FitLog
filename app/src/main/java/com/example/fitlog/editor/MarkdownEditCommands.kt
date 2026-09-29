package com.example.fitlog.editor

import androidx.compose.ui.text.TextRange

/** Pure source edits. No parsing or serialization of the rest of the document. */
object MarkdownEditCommands {
    data class Edit(val start: Int, val end: Int, val replacement: String, val selection: TextRange)

    fun wrap(text: String, selection: TextRange, marker: String): Edit {
        val start = selection.min
        val end = selection.max
        return Edit(start, end, marker + text.substring(start, end) + marker,
            TextRange(start + marker.length, end + marker.length))
    }

    fun link(selection: TextRange, label: String, address: String): Edit {
        val safeLabel = label.replace("\\", "\\\\").replace("[", "\\[").replace("]", "\\]")
        val safeAddress = address.replace(" ", "%20").replace("(", "%28").replace(")", "%29")
        val value = "[$safeLabel]($safeAddress)"
        return Edit(selection.min, selection.max, value, TextRange(selection.min + value.length))
    }

    fun heading(text: String, selection: TextRange, level: Int): Edit = lines(text, selection) { rows ->
        val prefix = if (level == 0) "" else "#".repeat(level) + " "
        rows.map { prefix + it.replaceFirst(Regex("^#{1,6} +"), "") }
    }

    fun list(text: String, selection: TextRange, task: Boolean): Edit = lines(text, selection) { rows ->
        val taskPrefix = Regex("^- \\[[ xX]\\] ")
        val remove = rows.all { if (task) taskPrefix.containsMatchIn(it) else it.startsWith("- ") }
        rows.map { row ->
            when {
                remove -> if (task) row.replaceFirst(taskPrefix, "") else row.removePrefix("- ")
                task && taskPrefix.containsMatchIn(row) -> row
                task -> "- [ ] " + row.removePrefix("- ")
                row.startsWith("- ") -> row
                else -> "- $row"
            }
        }
    }

    private fun lines(text: String, selection: TextRange, transform: (List<String>) -> List<String>): Edit {
        val start = text.lastIndexOf('\n', selection.min - 1).let { it + 1 }
        val last = if (!selection.collapsed && selection.max > 0 && text[selection.max - 1] == '\n')
            selection.max - 1 else selection.max
        val end = text.indexOf('\n', last).let { if (it < 0) text.length else it }
        val original = text.substring(start, end)
        val rows = original.split('\n')
        val changed = transform(rows.map { it.removeSuffix("\r") })
        val replacement = changed.mapIndexed { i, row -> row + if (rows[i].endsWith('\r')) "\r" else "" }.joinToString("\n")
        val cursor = (selection.end + replacement.length - original.length).coerceIn(start, start + replacement.length)
        return Edit(start, end, replacement, if (selection.collapsed) TextRange(cursor) else TextRange(start, start + replacement.length))
    }
}
