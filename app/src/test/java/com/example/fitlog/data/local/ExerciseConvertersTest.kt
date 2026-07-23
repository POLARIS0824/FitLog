package com.example.fitlog.data.local

import com.example.fitlog.model.BodyPart
import com.example.fitlog.model.Equipment
import com.example.fitlog.model.Muscle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ExerciseConverters] 的单元测试。
 *
 * 验证各种复杂类型与 Room 兼容的底层字段序列化及反序列化对称性。
 */
class ExerciseConvertersTest {

    private val converters = ExerciseConverters()

    // ── Muscle 列表 ──

    @Test
    fun `muscle list conversion round trip`() {
        val original = listOf(Muscle.QUADRICEPS, Muscle.HAMSTRINGS, Muscle.GLUTES)
        val str = converters.fromMuscleList(original)
        assertEquals("QUADRICEPS,HAMSTRINGS,GLUTES", str)

        val restored = converters.toMuscleList(str)
        assertEquals(original, restored)
    }

    @Test
    fun `empty muscle list conversion`() {
        val str = converters.fromMuscleList(emptyList())
        assertEquals("", str)

        val restored = converters.toMuscleList(str)
        assertTrue(restored.isEmpty())
    }

    @Test
    fun `single muscle conversion`() {
        val original = listOf(Muscle.CHEST)
        val str = converters.fromMuscleList(original)
        assertEquals("CHEST", str)

        val restored = converters.toMuscleList(str)
        assertEquals(original, restored)
    }

    // ── BodyPart ──

    @Test
    fun `bodyPart conversion round trip`() {
        val original = BodyPart.UPPER_LEGS
        val str = converters.fromBodyPart(original)
        assertEquals("UPPER_LEGS", str)

        val restored = converters.toBodyPart(str)
        assertEquals(original, restored)
    }

    // ── Equipment ──

    @Test
    fun `equipment conversion round trip`() {
        val original = Equipment.BARBELL
        val str = converters.fromEquipment(original)
        assertEquals("BARBELL", str)

        val restored = converters.toEquipment(str)
        assertEquals(original, restored)
    }

    // ── String 列表（JSON） ──

    @Test
    fun `string list with spaces conversion round trip`() {
        val original = listOf(
            "躺在平板椅上 握住杠铃",
            "慢慢下放到胸口",
            "呼气 爆发力推起"
        )
        val serialized = converters.fromStringList(original)
        assertTrue(serialized.startsWith("["))
        assertTrue(serialized.endsWith("]"))

        val restored = converters.toStringList(serialized)
        assertEquals(original.size, restored.size)
        assertEquals(original[0], restored[0])
        assertEquals(original[1], restored[1])
        assertEquals(original[2], restored[2])
    }

    @Test
    fun `old space separated list fallback`() {
        val oldString = "Lie down push up"
        val restored = converters.toStringList(oldString)
        assertEquals(4, restored.size)
        assertEquals("Lie", restored[0])
        assertEquals("down", restored[1])
    }

    @Test
    fun `empty string list conversion`() {
        val restored = converters.toStringList("")
        assertTrue(restored.isEmpty())
    }
}
