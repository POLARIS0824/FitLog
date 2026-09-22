package com.example.fitlog.data.local.converter

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.fitlog.data.local.LocalDateConverters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * [LocalDateConverters] 在 Android 设备/模拟器运行时下的兼容性测试。
 *
 * 验证：
 * 1. 常见日期、闰年闰日、年末跨年等日期的序列化与反序列化对称性；
 * 2. 异常输入降级为 Epoch（1970-01-01）的稳定性，特别确保在 minSdk 26+ 设备上
 *    不因字段或类缺少抛出 [NoSuchFieldError]；
 * 3. null 值的双向转换保持 null。
 */
@RunWith(AndroidJUnit4::class)
class LocalDateConvertersAndroidTest {

    private val converters = LocalDateConverters()

    @Test
    fun testStandardDateRoundTrip() {
        val testDates = listOf(
            LocalDate.of(2026, 9, 22),
            LocalDate.of(2024, 2, 29), // 闰年
            LocalDate.of(2025, 12, 31), // 年末
            LocalDate.of(2026, 1, 1),   // 年初
            LocalDate.of(2000, 1, 1),   // 世纪闰年
            LocalDate.of(1970, 1, 1),   // Epoch
        )

        for (date in testDates) {
            val serialized = converters.fromLocalDate(date)
            assertNotNull(serialized)
            val restored = converters.toLocalDate(serialized)
            assertEquals("Round trip failed for $date", date, restored)
        }
    }

    @Test
    fun testNullHandling() {
        assertNull(converters.fromLocalDate(null))
        assertNull(converters.toLocalDate(null))
    }

    @Test
    fun testCorruptedStringDegradesToEpochWithoutException() {
        val epoch = LocalDate.of(1970, 1, 1)
        val badInputs = listOf(
            "",
            "   ",
            "invalid-date",
            "2026-02-30", // 非法日期
            "2026/09/22", // 非 ISO 格式
            "2026-13-01", // 非法月份
        )

        for (bad in badInputs) {
            val result = converters.toLocalDate(bad)
            assertEquals("Expected epoch fallback for '$bad'", epoch, result)
        }
    }
}
