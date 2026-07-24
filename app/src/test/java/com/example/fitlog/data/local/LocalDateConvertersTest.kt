package com.example.fitlog.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * [LocalDateConverters] 的单元测试。
 * 验证 LocalDate 与数据库存储字符串（ISO-8601）之间的双向转换。
 */
class LocalDateConvertersTest {

    private val converters = LocalDateConverters()

    /**
     * 测试 LocalDate 序列化为 ISO-8601 字符串。
     */
    @Test
    fun testFromLocalDate() {
        val date = LocalDate.of(2026, 5, 20)
        assertEquals("2026-05-20", converters.fromLocalDate(date))
    }

    /**
     * 测试 ISO-8601 字符串反序列化为 LocalDate。
     */
    @Test
    fun testToLocalDate() {
        val restored = converters.toLocalDate("2026-05-20")
        assertEquals(LocalDate.of(2026, 5, 20), restored)
    }

    /**
     * 测试 null 输入在两个方向都返回 null（可空列支持）。
     */
    @Test
    fun testNullConversion() {
        assertNull(converters.fromLocalDate(null))
        assertNull(converters.toLocalDate(null))
    }

    /**
     * 测试转换的对称性：序列化后再反序列化得到原值。
     */
    @Test
    fun testRoundTripSymmetry() {
        val original = LocalDate.of(2026, 1, 1)
        val roundTripped = converters.toLocalDate(converters.fromLocalDate(original))
        assertEquals(original, roundTripped)
    }
}
