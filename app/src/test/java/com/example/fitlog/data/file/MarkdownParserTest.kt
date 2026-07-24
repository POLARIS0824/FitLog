package com.example.fitlog.data.file

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [MarkdownParser] 的单元测试。
 *
 * MarkdownParser 是对训练日志 Markdown 文本的纯文本预处理器，
 * 测试验证各项清洗规则（列表标记、粗体、分隔符、空行）的正确性。
 */
class MarkdownParserTest {

    /**
     * 测试去除行首 `- ` 列表标记。
     */
    @Test
    fun testPreprocess_removesListMarkers() {
        val input = "- 杠铃卧推\n- 哑铃推举"
        val expected = "杠铃卧推\n哑铃推举"
        assertEquals(expected, MarkdownParser.preprocess(input))
    }

    /**
     * 测试去除 `**` 粗体标记。
     */
    @Test
    fun testPreprocess_removesBoldMarkers() {
        val input = "**杠铃卧推** 80kg"
        assertEquals("杠铃卧推 80kg", MarkdownParser.preprocess(input))
    }

    /**
     * 测试统一分隔符：`➕` 转换为 `+`。
     */
    @Test
    fun testPreprocess_convertsPlusEmoji() {
        val input = "80kg ➕ 10"
        assertEquals("80kg + 10", MarkdownParser.preprocess(input))
    }

    /**
     * 测试统一分隔符：`✖️`（U+2716 U+FE0F）和 `×` 都转换为 `x`。
     */
    @Test
    fun testPreprocess_convertsMultiplicationSigns() {
        assertEquals("80kg x 10", MarkdownParser.preprocess("80kg ✖️ 10"))
        assertEquals("80kg x 10", MarkdownParser.preprocess("80kg × 10"))
    }

    /**
     * 测试每行首尾空白被裁剪，空行被移除。
     */
    @Test
    fun testPreprocess_trimsLinesAndRemovesBlankLines() {
        val input = "  杠铃卧推  \n\n\n   哑铃推举   "
        val expected = "杠铃卧推\n哑铃推举"
        assertEquals(expected, MarkdownParser.preprocess(input))
    }

    /**
     * 测试空输入与纯空白输入都返回空字符串。
     */
    @Test
    fun testPreprocess_emptyOrBlankInput_returnsEmpty() {
        assertEquals("", MarkdownParser.preprocess(""))
        assertEquals("", MarkdownParser.preprocess("   \n\n  \n"))
    }

    /**
     * 测试 `- ` 列表标记仅在行首被移除，行内的 `-` 保留。
     */
    @Test
    fun testPreprocess_onlyLeadingListMarkerRemoved() {
        val input = "- 组间休息 - 90秒"
        assertEquals("组间休息 - 90秒", MarkdownParser.preprocess(input))
    }

    /**
     * 测试一份贴近真实使用场景的完整训练日志清洗。
     */
    @Test
    fun testPreprocess_realisticWorkoutLog() {
        val input = """
            # 2026-05-20 训练

            - **杠铃卧推** 80kg ✖️ 10
            - **杠铃卧推** 85kg × 8
            - 哑铃推举 20kg ➕ 辅助
        """.trimIndent()

        val expected = """
            # 2026-05-20 训练
            杠铃卧推 80kg x 10
            杠铃卧推 85kg x 8
            哑铃推举 20kg + 辅助
        """.trimIndent()

        assertEquals(expected, MarkdownParser.preprocess(input))
    }
}
