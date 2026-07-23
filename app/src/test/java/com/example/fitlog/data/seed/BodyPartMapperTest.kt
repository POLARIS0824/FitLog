package com.example.fitlog.data.seed

import com.example.fitlog.model.BodyPart
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [BodyPartMapper] 的单元测试。
 *
 * 验证 exercises-dataset 的 10 种 body_part 值全部正确映射到 [BodyPart] 枚举。
 */
class BodyPartMapperTest {

    @Test
    fun `all dataset body_part values map correctly`() {
        assertEquals(BodyPart.CHEST, BodyPartMapper.map("chest"))
        assertEquals(BodyPart.BACK, BodyPartMapper.map("back"))
        assertEquals(BodyPart.SHOULDERS, BodyPartMapper.map("shoulders"))
        assertEquals(BodyPart.UPPER_ARMS, BodyPartMapper.map("upper arms"))
        assertEquals(BodyPart.LOWER_ARMS, BodyPartMapper.map("lower arms"))
        assertEquals(BodyPart.UPPER_LEGS, BodyPartMapper.map("upper legs"))
        assertEquals(BodyPart.LOWER_LEGS, BodyPartMapper.map("lower legs"))
        assertEquals(BodyPart.WAIST, BodyPartMapper.map("waist"))
        assertEquals(BodyPart.NECK, BodyPartMapper.map("neck"))
        assertEquals(BodyPart.CARDIO, BodyPartMapper.map("cardio"))
    }

    @Test
    fun `case insensitive mapping`() {
        assertEquals(BodyPart.CHEST, BodyPartMapper.map("CHEST"))
        assertEquals(BodyPart.UPPER_LEGS, BodyPartMapper.map("Upper Legs"))
    }

    @Test
    fun `unknown value returns fallback`() {
        assertEquals(BodyPart.CHEST, BodyPartMapper.map("unknown"))
    }
}
