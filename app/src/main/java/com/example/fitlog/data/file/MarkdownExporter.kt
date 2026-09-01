package com.example.fitlog.data.file

import com.example.fitlog.model.SetType
import com.example.fitlog.model.Workout
import java.time.LocalDate

/**
 * 训练记录 → Markdown 的导出序列化器。
 *
 * 与 [MarkdownParser]（导入链路的文本清洗器）保持格式对称：
 * 动作行使用 `- **名称** 重量kg x 次数` 的写法，热身组追加「（热身）」标记；
 * 导入存档行（仅原文、无结构化明细的记录）直接原样输出 rawContent。
 *
 * 单文件可承载多天记录（按日期升序、`# 日期 训练` 分节）——
 * 导出面向备份/迁移场景；单日文件（`yyyy-MM-dd.md` 命名）重新走导入
 * 链路时，节标题与动作行经 preprocess 清洗后即为原文存档。
 */
object MarkdownExporter {

    /**
     * 将全部训练记录序列化为单个 Markdown 文档。
     *
     * @param workouts 训练记录（内部按日期升序稳定排序，同日按 id 升序）
     * @return Markdown 文本；无记录时返回空串（调用方应避免以空内容触发写出）
     */
    fun export(workouts: List<Workout>): String =
        workouts
            .sortedWith(compareBy<Workout> { it.date }.thenBy { it.id })
            .joinToString(separator = "\n\n") { workout ->
                when {
                    workout.exercises.isNotEmpty() -> serializeStructured(workout)
                    !workout.rawContent.isNullOrBlank() -> serializeArchive(workout)
                    else -> ""
                }
            }
            .split("\n\n")
            .filter { it.isNotBlank() }
            .joinToString(separator = "\n\n")

    /** 结构化训练（训练会话落库，含动作与组明细）。 */
    private fun serializeStructured(workout: Workout): String {
        val header = "# ${workout.date} 训练"
        val meta = buildList {
            workout.feelings?.let { add("- 感受：$it") }
        }
        val exerciseLines = workout.exercises.flatMap { log ->
            log.sets.map { set ->
                val warmupMark = if (set.setType == SetType.WARMUP) "（热身）" else ""
                "- **${log.name}**${warmupMark} ${formatKg(set.weightKg)} x ${set.reps}"
            }
        }
        return (listOf(header) + meta + exerciseLines).joinToString("\n")
    }

    /** 导入存档（rawContent 原文，无结构化明细）。 */
    private fun serializeArchive(workout: Workout): String =
        buildString {
            append("# ${workout.date} 训练\n\n")
            append(workout.rawContent!!.trim())
        }

    /** 重量格式：整数去尾零（80kg 而非 80.0kg）。 */
    private fun formatKg(weightKg: Float): String =
        if (weightKg % 1f == 0f) "${weightKg.toInt()}kg" else "${weightKg}kg"

    /**
     * SAF 导出目标文件的默认建议名（当天日期，避免覆盖历史导出）。
     */
    fun suggestedFileName(today: LocalDate = LocalDate.now()): String =
        "fitlog-export-${today}.md"
}
