package com.example.myfitness.data.markdown

import com.example.myfitness.domain.model.ExerciseEntry
import com.example.myfitness.domain.model.WorkoutSet

/**
 * 纯 Kotlin 实现的 Markdown 训练日志解析器。
 *
 * 格式约定：
 * ```
 * ## 动作名称
 * - {weightKg} x {reps}
 * - {weightKg} x {reps}
 * ```
 */
object MarkdownParser {

    /**
     * 将 Markdown 文本解析为动作列表。
     *
     * @param content Markdown 格式的训练日志
     * @return 解析后的 [ExerciseEntry] 列表
     */
    fun parse(content: String): List<ExerciseEntry> {
        if (content.isBlank()) return emptyList()

        val entries = mutableListOf<ExerciseEntry>()
        var currentName: String? = null
        val currentSets = mutableListOf<WorkoutSet>()

        for (line in content.lineSequence()) {
            val trimmed = line.trim()
            when {
                trimmed.startsWith("## ") -> {
                    // 保存上一个动作
                    currentName?.let { name ->
                        if (currentSets.isNotEmpty()) {
                            entries.add(ExerciseEntry(name, currentSets.toList()))
                        }
                    }
                    currentName = trimmed.removePrefix("## ").trim()
                    currentSets.clear()
                }

                trimmed.startsWith("- ") -> {
                    val set = parseSetLine(trimmed.removePrefix("- ").trim())
                    set?.let { currentSets.add(it) }
                }
            }
        }

        // 保存最后一个动作
        currentName?.let { name ->
            if (currentSets.isNotEmpty()) {
                entries.add(ExerciseEntry(name, currentSets.toList()))
            }
        }

        return entries
    }

    /**
     * 将训练记录序列化为 Markdown 文本。
     *
     * @param exercises 动作及组列表
     * @return Markdown 格式的训练日志字符串
     */
    fun serialize(exercises: List<ExerciseEntry>): String {
        return buildString {
            for ((index, entry) in exercises.withIndex()) {
                if (index > 0) appendLine()
                appendLine("## ${entry.name}")
                for (set in entry.sets) {
                    appendLine("- ${set.weightKg} x ${set.reps}")
                }
            }
        }.trimEnd()
    }

    /**
     * 解析单组记录的文本行。
     *
     * @param line 格式如 "80.0 x 8" 的字符串
     * @return 解析后的 [WorkoutSet]，若格式错误则返回 null
     */
    private fun parseSetLine(line: String): WorkoutSet? {
        // 格式: "80.0 x 8" 或 "80 x 8"
        val parts = line.split("x", "X", "×", ignoreCase = true)
        if (parts.size != 2) return null

        val weight = parts[0].trim().toFloatOrNull() ?: return null
        val reps = parts[1].trim().toIntOrNull() ?: return null

        return WorkoutSet(weightKg = weight, reps = reps)
    }
}
