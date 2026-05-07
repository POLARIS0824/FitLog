package com.example.myfitness.data.markdown

import com.example.myfitness.domain.model.ExerciseEntry
import com.example.myfitness.domain.model.WorkoutSet
import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownParserTest {

    @Test
    fun `parse empty string returns empty list`() {
        assertEquals(emptyList<ExerciseEntry>(), MarkdownParser.parse(""))
    }

    @Test
    fun `parse blank string returns empty list`() {
        assertEquals(emptyList<ExerciseEntry>(), MarkdownParser.parse("   \n  "))
    }

    @Test
    fun `parse single exercise with multiple sets`() {
        val content = """
            ## 卧推
            - 80.0 x 8
            - 80.0 x 7
            - 80.0 x 6
        """.trimIndent()

        val result = MarkdownParser.parse(content)

        assertEquals(1, result.size)
        assertEquals("卧推", result[0].name)
        assertEquals(
            listOf(
                WorkoutSet(80.0f, 8),
                WorkoutSet(80.0f, 7),
                WorkoutSet(80.0f, 6),
            ),
            result[0].sets,
        )
    }

    @Test
    fun `parse multiple exercises`() {
        val content = """
            ## 深蹲
            - 100 x 5
            - 100 x 5
            ## 硬拉
            - 120 x 3
        """.trimIndent()

        val result = MarkdownParser.parse(content)

        assertEquals(2, result.size)
        assertEquals("深蹲", result[0].name)
        assertEquals(2, result[0].sets.size)
        assertEquals("硬拉", result[1].name)
        assertEquals(1, result[1].sets.size)
    }

    @Test
    fun `parse handles uppercase X separator`() {
        val content = """
            ## 推举
            - 60 X 10
        """.trimIndent()

        val result = MarkdownParser.parse(content)

        assertEquals(1, result.size)
        assertEquals(WorkoutSet(60.0f, 10), result[0].sets[0])
    }

    @Test
    fun `parse handles multiplication sign separator`() {
        val content = """
            ## 推举
            - 60 × 10
        """.trimIndent()

        val result = MarkdownParser.parse(content)

        assertEquals(1, result.size)
        assertEquals(WorkoutSet(60.0f, 10), result[0].sets[0])
    }

    @Test
    fun `parse ignores invalid set lines`() {
        val content = """
            ## 卧推
            - 80 x 8
            invalid line
            - 80 x 7
        """.trimIndent()

        val result = MarkdownParser.parse(content)

        assertEquals(2, result[0].sets.size)
        assertEquals(WorkoutSet(80.0f, 8), result[0].sets[0])
        assertEquals(WorkoutSet(80.0f, 7), result[0].sets[1])
    }

    @Test
    fun `parse skips exercise without sets`() {
        val content = """
            ## 卧推
            - 80 x 8
            ## 空动作
            ## 深蹲
            - 100 x 5
        """.trimIndent()

        val result = MarkdownParser.parse(content)

        assertEquals(2, result.size)
        assertEquals("卧推", result[0].name)
        assertEquals("深蹲", result[1].name)
    }

    @Test
    fun `parse handles integer weight`() {
        val content = """
            ## 引体向上
            - 0 x 10
        """.trimIndent()

        val result = MarkdownParser.parse(content)

        assertEquals(WorkoutSet(0.0f, 10), result[0].sets[0])
    }

    @Test
    fun `serialize empty list returns empty string`() {
        assertEquals("", MarkdownParser.serialize(emptyList()))
    }

    @Test
    fun `serialize single exercise`() {
        val exercises = listOf(
            ExerciseEntry(
                name = "卧推",
                sets = listOf(WorkoutSet(80.0f, 8), WorkoutSet(80.0f, 7)),
            ),
        )

        val result = MarkdownParser.serialize(exercises)

        assertEquals(
            """
                ## 卧推
                - 80.0 x 8
                - 80.0 x 7
            """.trimIndent(),
            result,
        )
    }

    @Test
    fun `serialize multiple exercises`() {
        val exercises = listOf(
            ExerciseEntry(name = "深蹲", sets = listOf(WorkoutSet(100.0f, 5))),
            ExerciseEntry(name = "硬拉", sets = listOf(WorkoutSet(120.0f, 3))),
        )

        val result = MarkdownParser.serialize(exercises)

        assertEquals(
            """
                ## 深蹲
                - 100.0 x 5

                ## 硬拉
                - 120.0 x 3
            """.trimIndent(),
            result,
        )
    }

    @Test
    fun `roundtrip parse after serialize returns equivalent data`() {
        val original = listOf(
            ExerciseEntry(
                name = "卧推",
                sets = listOf(WorkoutSet(80.0f, 8), WorkoutSet(82.5f, 6)),
            ),
            ExerciseEntry(
                name = "深蹲",
                sets = listOf(WorkoutSet(100.0f, 5)),
            ),
        )

        val serialized = MarkdownParser.serialize(original)
        val parsed = MarkdownParser.parse(serialized)

        assertEquals(original, parsed)
    }
}
