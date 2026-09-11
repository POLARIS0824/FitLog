package com.example.fitlog.data.file

import java.time.LocalDate
import java.time.format.DateTimeParseException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [MarkdownFileScanner.parseDateFromFileName] 的纯 JVM 单元测试。
 *
 * 文件名日期解析是导入流程的第一道关：后缀大小写、非 ISO 日期、
 * 非法日期（如 2 月 30 日）都决定了文件进成功列表还是失败列表。
 */
class MarkdownFileScannerTest {

    private val scanner = MarkdownFileScanner()

    /** 标准 ISO 文件名解析成功。 */
    @Test
    fun `standard iso filename parses`() {
        assertEquals(
            LocalDate.of(2026, 5, 20),
            scanner.parseDateFromFileName("2026-05-20.md"),
        )
    }

    /** 后缀大小写不敏感（.MD/.Md/.mD）。 */
    @Test
    fun `suffix case insensitive`() {
        assertEquals(LocalDate.of(2026, 1, 2), scanner.parseDateFromFileName("2026-01-02.MD"))
        assertEquals(LocalDate.of(2026, 1, 2), scanner.parseDateFromFileName("2026-01-02.Md"))
        assertEquals(LocalDate.of(2026, 1, 2), scanner.parseDateFromFileName("2026-01-02.mD"))
    }

    /** 非 ISO 日期（缺零填充等）解析失败 → 归入失败列表。 */
    @Test(expected = DateTimeParseException::class)
    fun `non iso date throws`() {
        scanner.parseDateFromFileName("2026-5-7.md")
    }

    /** 非法日期（2 月 30 日）解析失败。 */
    @Test(expected = DateTimeParseException::class)
    fun `invalid calendar date throws`() {
        scanner.parseDateFromFileName("2026-02-30.md")
    }

    /** 完全无关的文件名解析失败。 */
    @Test
    fun `non date filename throws`() {
        var threw = false
        try {
            scanner.parseDateFromFileName("训练笔记.md")
        } catch (e: DateTimeParseException) {
            threw = true
        }
        assertTrue(threw)
    }

    // ──────────────────────────────────────
    // splitDatedSections：多天导出文件的节拆分（导出→导入回环）
    // ──────────────────────────────────────

    /** 导出格式的多天文件按节标题拆分，各节取标题日期、不含标题行。 */
    @Test
    fun `multi day export file splits by section headers`() {
        val content = """
            # 2026-08-30 训练
            - **卧推** 80kg x 8
            - **划船** 60kg x 10

            # 2026-09-01 训练
            - 感受：状态不错
            - **深蹲** 100kg x 5
        """.trimIndent()

        // 与 scanFolder 管线一致：先 preprocess 再拆分
        val sections = scanner.splitDatedSections(MarkdownParser.preprocess(content))!!

        assertEquals(2, sections.size)
        assertEquals(LocalDate.of(2026, 8, 30), sections[0].date)
        // preprocess 已剥离 "- " 前缀与 "**" 粗体
        assertEquals("卧推 80kg x 8\n划船 60kg x 10", sections[0].content)
        assertEquals(LocalDate.of(2026, 9, 1), sections[1].date)
        assertTrue(sections[1].content.contains("深蹲"))
        assertTrue("节体不含标题行", !sections[0].content.contains("#"))
    }

    /** 无节标题的文件返回 null（保持旧语义：文件名日期 + 全文一条记录）。 */
    @Test
    fun `file without section headers returns null`() {
        val content = """
            卧推 80kg x 8
            划船 60kg x 10
        """.trimIndent()

        assertEquals(null, scanner.splitDatedSections(content))
    }

    /** 首个节标题之前的散行并入第一节（手写文件标题前可能带正文）。 */
    @Test
    fun `preamble merges into first section`() {
        val content = """
            热身 15 分钟
            # 2026-09-01 训练
            - **卧推** 80kg x 8
        """.trimIndent()

        val sections = scanner.splitDatedSections(content)!!

        assertEquals(1, sections.size)
        assertEquals(LocalDate.of(2026, 9, 1), sections[0].date)
        assertTrue(sections[0].content.startsWith("热身 15 分钟"))
    }

    /** 形似节标题但日期非法的行视作普通正文行，不触发拆分。 */
    @Test
    fun `malformed header date treated as body line`() {
        val content = """
            # 2026-02-30 训练
            - **卧推** 80kg x 8
        """.trimIndent()

        // 唯一"标题"日期非法 → 不存在合法节标题 → 保持旧语义
        assertEquals(null, scanner.splitDatedSections(content))
    }

    /** 节体全空白的节不产出记录（避免"只有日期没有内容"的幽灵行）。 */
    @Test
    fun `blank body sections are skipped`() {
        val content = """
            # 2026-09-01 训练
            - **卧推** 80kg x 8

            # 2026-09-02 训练

            # 2026-09-03 训练
            - **深蹲** 100kg x 5
        """.trimIndent()

        val sections = scanner.splitDatedSections(content)!!

        assertEquals(2, sections.size)
        assertEquals(LocalDate.of(2026, 9, 1), sections[0].date)
        assertEquals(LocalDate.of(2026, 9, 3), sections[1].date)
    }

    /** 同日多次训练（导出同日多节）各成一条记录，节序互相独立。 */
    @Test
    fun `same date multiple sections each produce a record`() {
        val content = """
            # 2026-09-01 训练
            - **卧推** 80kg x 8

            # 2026-09-01 训练
            - **深蹲** 100kg x 5
        """.trimIndent()

        val sections = scanner.splitDatedSections(content)!!

        assertEquals(2, sections.size)
        assertEquals(LocalDate.of(2026, 9, 1), sections[0].date)
        assertEquals(LocalDate.of(2026, 9, 1), sections[1].date)
        assertTrue(sections[0].content.contains("卧推"))
        assertTrue(sections[1].content.contains("深蹲"))
    }
}
