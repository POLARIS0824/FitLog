package com.example.fitlog.data.local

import com.example.fitlog.model.MuscleGroup
import com.example.fitlog.model.PrimaryMuscle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ExerciseConverters] 的单元测试。
 * 验证各种复杂类型与 Room 兼容的底层字段序列化及反序列化对称性，包括特殊的空格步骤列表和老旧版本兼容。
 */
class ExerciseConvertersTest {

    private val converters = ExerciseConverters()

    /**
     * 测试 PrimaryMuscle 枚举与 String 之间的双向转换。
     */
    @Test
    fun testPrimaryMuscleConversion() {
        val original = PrimaryMuscle.CHEST
        val str = converters.fromPrimaryMuscle(original)
        assertEquals("CHEST", str)

        val restored = converters.toPrimaryMuscle(str)
        assertEquals(original, restored)
    }

    /**
     * 测试 MuscleGroup 列表与以逗号分隔的 String 之间的双向转换。
     */
    @Test
    fun testMuscleGroupListConversion() {
        val original = listOf(MuscleGroup.LATISSIMUS_DORSI, MuscleGroup.BICEPS_BRACHII)
        val str = converters.fromMuscleGroupList(original)
        assertEquals("LATISSIMUS_DORSI,BICEPS_BRACHII", str)

        val restored = converters.toMuscleGroupList(str)
        assertEquals(original, restored)
    }

    /**
     * 测试空 MuscleGroup 列表的转换。
     */
    @Test
    fun testEmptyMuscleGroupListConversion() {
        val str = converters.fromMuscleGroupList(emptyList())
        assertEquals("", str)

        val restored = converters.toMuscleGroupList(str)
        assertTrue(restored.isEmpty())
    }

    /**
     * 测试字符串步骤列表（带有空格的长句子步骤）的 JSON 序列化与反序列化对称性。
     */
    @Test
    fun testStringListWithSpacesConversion() {
        val original = listOf(
            "躺在平板椅上 握住杠铃",
            "慢慢下放到胸口",
            "呼气 爆发力推起"
        )
        val serialized = converters.fromStringList(original)
        // 验证不再使用空格简单拼接，而是生成了 JSON
        assertTrue(serialized.startsWith("["))
        assertTrue(serialized.endsWith("]"))

        val restored = converters.toStringList(serialized)
        assertEquals(original.size, restored.size)
        assertEquals(original[0], restored[0])
        assertEquals(original[1], restored[1])
        assertEquals(original[2], restored[2])
    }

    /**
     * 测试对老版本（以空格拼接的字符串列表）反序列化的兼容性与降级降回 split(" ")。
     */
    @Test
    fun testOldSpaceSeparatedListFallback() {
        val oldString = "Lie down push up" // 老格式
        val restored = converters.toStringList(oldString)
        assertEquals(4, restored.size)
        assertEquals("Lie", restored[0])
        assertEquals("down", restored[1])
        assertEquals("push", restored[2])
        assertEquals("up", restored[3])
    }
}
