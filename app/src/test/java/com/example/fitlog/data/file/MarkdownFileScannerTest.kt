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
}
