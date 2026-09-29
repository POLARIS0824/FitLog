package com.example.fitlog.editor

import androidx.compose.ui.text.TextRange

/** Pure source edits. No parsing or serialization of the rest of the document. */
object MarkdownEditCommands {
    data class Edit(val start: Int, val end: Int, val replacement: String, val selection: TextRange)

    private val listPrefix = Regex("""^([ \t]*)(?:([-+*]) (?:\[([ xX])\] )?|([0-9]{1,9})([.)]) )(.*)$""")

    fun orderedList(text: String, selection: TextRange): Edit = lines(text, selection) { rows ->
        val remove = rows.all { Regex("""^[ \t]*[0-9]+[.)] """).containsMatchIn(it) }
        rows.mapIndexed { index, row ->
            val indent = row.takeWhile { it == ' ' || it == '\t' }
            val content = row.drop(indent.length).replaceFirst(Regex("""^(?:[0-9]+[.)] |[-+*] (?:\[[ xX]\] )?)"""), "")
            indent + (if (remove) "" else "${index + 1}. ") + content
        }
    }

    fun indent(text: String, selection: TextRange, outdent: Boolean): Edit = lines(text, selection) { rows ->
        rows.map { row ->
            if (!outdent) "  $row"
            else if (row.startsWith('\t')) row.drop(1)
            else row.drop(row.take(2).takeWhile { it == ' ' }.length)
        }
    }

    /** Coordinates refer to the buffer after a single newline has been inserted. */
    fun continueList(text: String, selection: TextRange, inserted: String = "\n"): Edit? {
        if (!selection.collapsed || inserted !in listOf("\n", "\r\n")) return null
        val cursor = selection.end
        val start = text.lastIndexOf('\n', cursor - 1) + 1
        // Markdown examples in fenced code blocks must remain literal text.
        var fence: Char? = null
        text.substring(0, start).lineSequence().forEach { line ->
            val marker = line.trimStart().take(3)
            if (marker == "```" || marker == "~~~") {
                if (fence == null) fence = marker[0] else if (fence == marker[0]) fence = null
            }
        }
        if (fence != null) return null
        val match = listPrefix.matchEntire(text.substring(start, cursor)) ?: return null
        val (indent, bullet, task, number, delimiter, content) = match.destructured
        val tail = text.substring(cursor).substringBefore('\n').removeSuffix("\r")
        if (content.isBlank() && tail.isBlank()) {
            return Edit(start, cursor + inserted.length, indent, TextRange(start + indent.length))
        }
        val marker = if (number.isNotEmpty()) "${number.toLong() + 1}$delimiter "
            else bullet + " " + if (task.isNotEmpty()) "[ ] " else ""
        val newline = if (inserted == "\r\n" || text.contains("\r\n")) "\r\n" else "\n"
        val replacement = newline + indent + marker
        return Edit(cursor, cursor + inserted.length, replacement, TextRange(cursor + replacement.length))
    }

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
        // Keep selection direction and logical character positions across per-line prefix edits.
        fun mapOffset(offset: Int): Int {
            if (offset <= start && offset < selection.min) return offset
            if (offset >= end) return offset + replacement.length - original.length
            var oldStart = start
            var newStart = start
            for (i in rows.indices) {
                val old = rows[i]
                val new = changed[i] + if (old.endsWith('\r')) "\r" else ""
                if (offset <= oldStart + old.length) {
                    var suffix = 0
                    while (suffix < old.length && suffix < new.length && old[old.lastIndex - suffix] == new[new.lastIndex - suffix]) suffix++
                    val oldPrefix = old.length - suffix
                    val newPrefix = new.length - suffix
                    val column = offset - oldStart
                    return newStart + if (column >= oldPrefix) newPrefix + column - oldPrefix else minOf(column, newPrefix)
                }
                oldStart += old.length + 1
                newStart += new.length + 1
            }
            return start + replacement.length
        }
        return Edit(start, end, replacement, TextRange(mapOffset(selection.start), mapOffset(selection.end)))
    }
}
