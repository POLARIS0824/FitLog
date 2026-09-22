package com.example.fitlog.data.file

import com.example.fitlog.model.ExerciseLog
import com.example.fitlog.model.SetLog
import com.example.fitlog.model.SetType
import com.example.fitlog.model.Workout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * [MarkdownExporter] 的单元测试：导出格式与导入链路（MarkdownParser 清洗）
 * 的格式约定对称，存档行原样保留。
 */
class MarkdownExporterTest {

    @Test
    fun `structured workout serializes exercise lines`() {
        val workout = Workout(
            id = 1,
            userId = 0,
            date = LocalDate.of(2026, 5, 20),
            feelings = "状态不错",
            exercises = listOf(
                ExerciseLog(
                    name = "杠铃卧推",
                    exerciseKey = "barbell-bench-press",
                    sets = listOf(
                        SetLog(weightKg = 80f, reps = 10),
                        SetLog(weightKg = 85f, reps = 8),
                    ),
                ),
            ),
        )

        val md = MarkdownExporter.export(listOf(workout))

        assertTrue(md.contains("# 2026-05-20 训练"))
        assertTrue(md.contains("- 感受：状态不错"))
        assertTrue(md.contains("- 开始时间：空"))
        assertTrue(md.contains("- 结束时间：空"))
        assertTrue(md.contains("- **杠铃卧推** 80kg x 10"))
        assertTrue(md.contains("- **杠铃卧推** 85kg x 8"))
    }

    @Test
    fun `structured workout with timestamps serializes ISO offset datetime and time window`() {
        val zone = java.time.ZoneId.of("Asia/Shanghai")
        val startEpoch = java.time.ZonedDateTime.of(2026, 5, 20, 23, 30, 0, 0, zone).toInstant().toEpochMilli()
        val endEpoch = java.time.ZonedDateTime.of(2026, 5, 21, 1, 15, 0, 0, zone).toInstant().toEpochMilli()

        val workout = Workout(
            id = 1,
            userId = 0,
            date = LocalDate.of(2026, 5, 20),
            feelings = "练到深夜",
            startedAt = startEpoch,
            endedAt = endEpoch,
            exercises = listOf(
                ExerciseLog(
                    name = "杠铃卧推",
                    exerciseKey = "barbell-bench-press",
                    sets = listOf(SetLog(weightKg = 80f, reps = 10)),
                ),
            ),
        )

        val md = MarkdownExporter.export(listOf(workout), zoneId = zone)

        assertTrue(md.contains("# 2026-05-20 训练"))
        assertTrue(md.contains("- 感受：练到深夜"))
        assertTrue(md.contains("- 时间：23:30–01:15"))
        assertTrue(md.contains("- 开始时间：2026-05-20T23:30:00+08:00"))
        assertTrue(md.contains("- 结束时间：2026-05-21T01:15:00+08:00"))
    }

    @Test
    fun `warmup sets are marked and decimal weight keeps one digit`() {
        val workout = Workout(
            id = 1,
            userId = 0,
            date = LocalDate.of(2026, 5, 20),
            feelings = null,
            exercises = listOf(
                ExerciseLog(
                    name = "哑铃飞鸟",
                    sets = listOf(
                        SetLog(weightKg = 10f, reps = 12, setType = SetType.WARMUP),
                        SetLog(weightKg = 7.5f, reps = 15),
                    ),
                ),
            ),
        )

        val md = MarkdownExporter.export(listOf(workout))

        // 热身标记在组尾（不黏在动作名内：重导入的动作名匹配不受污染）
        assertTrue(md.contains("- **哑铃飞鸟** 10kg x 12（热身组）"))
        assertTrue(md.contains("- **哑铃飞鸟** 7.5kg x 15"))
    }

    @Test
    fun `archive workout keeps raw content verbatim`() {
        val workout = Workout(
            id = 1,
            userId = 0,
            date = LocalDate.of(2026, 5, 21),
            feelings = null,
            exercises = emptyList(),
            rawContent = "# 2026-05-21 训练\n硬拉 140kg x 5",
        )

        val md = MarkdownExporter.export(listOf(workout))

        assertTrue(md.contains("# 2026-05-21 训练"))
        assertTrue(md.contains("硬拉 140kg x 5"))
    }

    @Test
    fun `multiple workouts sort by date ascending`() {
        val later = archivedWorkout(id = 2, date = LocalDate.of(2026, 5, 22), content = "later")
        val earlier = archivedWorkout(id = 1, date = LocalDate.of(2026, 5, 7), content = "earlier")

        val md = MarkdownExporter.export(listOf(later, earlier))

        assertTrue(md.indexOf("earlier") < md.indexOf("later"))
    }

    @Test
    fun `empty input yields empty document`() {
        assertEquals("", MarkdownExporter.export(emptyList()))
    }

    @Test
    fun `suggested file name is dated`() {
        assertEquals(
            "fitlog-export-2026-09-02.md",
            MarkdownExporter.suggestedFileName(LocalDate.of(2026, 9, 2)),
        )
    }

    private fun archivedWorkout(id: Long, date: LocalDate, content: String) = Workout(
        id = id,
        userId = 0,
        date = date,
        feelings = null,
        exercises = emptyList(),
        rawContent = content,
    )
}
