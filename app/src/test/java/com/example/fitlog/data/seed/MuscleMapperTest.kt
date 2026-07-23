package com.example.fitlog.data.seed

import com.example.fitlog.model.Muscle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [MuscleMapper] 的单元测试。
 *
 * 验证 exercises-dataset 中出现的所有肌肉名称字符串都能正确映射到 [Muscle] 枚举。
 */
class MuscleMapperTest {

    // ── target 字段的 19 个值 ──

    @Test
    fun `target values map correctly`() {
        assertEquals(Muscle.CORE, MuscleMapper.map("abs"))
        assertEquals(Muscle.CHEST, MuscleMapper.map("pectorals"))
        assertEquals(Muscle.BICEPS, MuscleMapper.map("biceps"))
        assertEquals(Muscle.GLUTES, MuscleMapper.map("glutes"))
        assertEquals(Muscle.SHOULDERS, MuscleMapper.map("delts"))
        assertEquals(Muscle.TRICEPS, MuscleMapper.map("triceps"))
        assertEquals(Muscle.UPPER_BACK, MuscleMapper.map("upper back"))
        assertEquals(Muscle.LATS, MuscleMapper.map("lats"))
        assertEquals(Muscle.CALVES, MuscleMapper.map("calves"))
        assertEquals(Muscle.QUADRICEPS, MuscleMapper.map("quads"))
        assertEquals(Muscle.FOREARMS, MuscleMapper.map("forearms"))
        assertEquals(Muscle.CARDIO, MuscleMapper.map("cardiovascular system"))
        assertEquals(Muscle.HAMSTRINGS, MuscleMapper.map("hamstrings"))
        assertEquals(Muscle.LOWER_BACK, MuscleMapper.map("spine"))
        assertEquals(Muscle.TRAPS, MuscleMapper.map("traps"))
        assertEquals(Muscle.ADDUCTORS, MuscleMapper.map("adductors"))
        assertEquals(Muscle.CHEST, MuscleMapper.map("serratus anterior"))
        assertEquals(Muscle.ABDUCTORS, MuscleMapper.map("abductors"))
        assertEquals(Muscle.NECK, MuscleMapper.map("levator scapulae"))
    }

    // ── secondary_muscles 中的额外值 ──

    @Test
    fun `secondary muscle values map correctly`() {
        assertEquals(Muscle.SHOULDERS, MuscleMapper.map("shoulders"))
        assertEquals(Muscle.HAMSTRINGS, MuscleMapper.map("hamstrings"))
        assertEquals(Muscle.FOREARMS, MuscleMapper.map("forearms"))
        assertEquals(Muscle.QUADRICEPS, MuscleMapper.map("quadriceps"))
        assertEquals(Muscle.CORE, MuscleMapper.map("core"))
        assertEquals(Muscle.CORE, MuscleMapper.map("obliques"))
        assertEquals(Muscle.CORE, MuscleMapper.map("abdominals"))
        assertEquals(Muscle.CORE, MuscleMapper.map("lower abs"))
        assertEquals(Muscle.HIP_FLEXORS, MuscleMapper.map("hip flexors"))
        assertEquals(Muscle.LOWER_BACK, MuscleMapper.map("lower back"))
        assertEquals(Muscle.UPPER_BACK, MuscleMapper.map("rhomboids"))
        assertEquals(Muscle.TRAPS, MuscleMapper.map("trapezius"))
        assertEquals(Muscle.SHOULDERS, MuscleMapper.map("deltoids"))
        assertEquals(Muscle.SHOULDERS, MuscleMapper.map("rear deltoids"))
        assertEquals(Muscle.BICEPS, MuscleMapper.map("brachialis"))
        assertEquals(Muscle.CALVES, MuscleMapper.map("soleus"))
        assertEquals(Muscle.CALVES, MuscleMapper.map("ankles"))
        assertEquals(Muscle.CALVES, MuscleMapper.map("ankle stabilizers"))
        assertEquals(Muscle.CALVES, MuscleMapper.map("feet"))
        assertEquals(Muscle.CALVES, MuscleMapper.map("shins"))
        assertEquals(Muscle.LATS, MuscleMapper.map("latissimus dorsi"))
        assertEquals(Muscle.UPPER_BACK, MuscleMapper.map("back"))
        assertEquals(Muscle.SHOULDERS, MuscleMapper.map("rotator cuff"))
        assertEquals(Muscle.CHEST, MuscleMapper.map("upper chest"))
        assertEquals(Muscle.FOREARMS, MuscleMapper.map("wrists"))
        assertEquals(Muscle.FOREARMS, MuscleMapper.map("wrist flexors"))
        assertEquals(Muscle.FOREARMS, MuscleMapper.map("wrist extensors"))
        assertEquals(Muscle.FOREARMS, MuscleMapper.map("grip muscles"))
        assertEquals(Muscle.FOREARMS, MuscleMapper.map("hands"))
        assertEquals(Muscle.ADDUCTORS, MuscleMapper.map("inner thighs"))
        assertEquals(Muscle.ADDUCTORS, MuscleMapper.map("groin"))
        assertEquals(Muscle.NECK, MuscleMapper.map("neck"))
        assertEquals(Muscle.NECK, MuscleMapper.map("sternocleidomastoid"))
    }

    // ── 边界情况 ──

    @Test
    fun `case insensitive mapping`() {
        assertEquals(Muscle.CHEST, MuscleMapper.map("CHEST"))
        assertEquals(Muscle.CHEST, MuscleMapper.map("Chest"))
        assertEquals(Muscle.QUADRICEPS, MuscleMapper.map("Quads"))
    }

    @Test
    fun `whitespace is trimmed`() {
        assertEquals(Muscle.CHEST, MuscleMapper.map("  chest  "))
        assertEquals(Muscle.BICEPS, MuscleMapper.map(" biceps "))
    }

    @Test
    fun `unknown value returns null`() {
        assertNull(MuscleMapper.map("unknown muscle"))
        assertNull(MuscleMapper.map(""))
        assertNull(MuscleMapper.map("xyz"))
    }
}
