package com.example.fitlog.data.seed

import com.example.fitlog.model.Equipment
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [EquipmentMapper] 的单元测试。
 *
 * 验证 exercises-dataset 中出现的所有器械名称字符串都能正确映射到 [Equipment] 枚举。
 */
class EquipmentMapperTest {

    @Test
    fun `all dataset equipment values map correctly`() {
        assertEquals(Equipment.BARBELL, EquipmentMapper.map("barbell"))
        assertEquals(Equipment.BARBELL, EquipmentMapper.map("olympic barbell"))
        assertEquals(Equipment.DUMBBELL, EquipmentMapper.map("dumbbell"))
        assertEquals(Equipment.EZ_BAR, EquipmentMapper.map("ez barbell"))
        assertEquals(Equipment.CABLE, EquipmentMapper.map("cable"))
        assertEquals(Equipment.MACHINE, EquipmentMapper.map("leverage machine"))
        assertEquals(Equipment.MACHINE, EquipmentMapper.map("sled machine"))
        assertEquals(Equipment.MACHINE, EquipmentMapper.map("hammer"))
        assertEquals(Equipment.SMITH_MACHINE, EquipmentMapper.map("smith machine"))
        assertEquals(Equipment.BODYWEIGHT, EquipmentMapper.map("body weight"))
        assertEquals(Equipment.KETTLEBELL, EquipmentMapper.map("kettlebell"))
        assertEquals(Equipment.RESISTANCE_BAND, EquipmentMapper.map("band"))
        assertEquals(Equipment.RESISTANCE_BAND, EquipmentMapper.map("resistance band"))
        assertEquals(Equipment.MEDICINE_BALL, EquipmentMapper.map("medicine ball"))
        assertEquals(Equipment.STABILITY_BALL, EquipmentMapper.map("stability ball"))
        assertEquals(Equipment.BOSU_BALL, EquipmentMapper.map("bosu ball"))
        assertEquals(Equipment.ROPE, EquipmentMapper.map("rope"))
        assertEquals(Equipment.ROLLER, EquipmentMapper.map("roller"))
        assertEquals(Equipment.ROLLER, EquipmentMapper.map("wheel roller"))
        assertEquals(Equipment.ROLLER, EquipmentMapper.map("foam roll"))
        assertEquals(Equipment.ASSISTED, EquipmentMapper.map("assisted"))
        assertEquals(Equipment.WEIGHTED, EquipmentMapper.map("weighted"))
        assertEquals(Equipment.TRAP_BAR, EquipmentMapper.map("trap bar"))
        assertEquals(Equipment.CARDIO_MACHINE, EquipmentMapper.map("stationary bike"))
        assertEquals(Equipment.CARDIO_MACHINE, EquipmentMapper.map("elliptical machine"))
        assertEquals(Equipment.CARDIO_MACHINE, EquipmentMapper.map("stepmill machine"))
        assertEquals(Equipment.CARDIO_MACHINE, EquipmentMapper.map("skierg machine"))
        assertEquals(Equipment.CARDIO_MACHINE, EquipmentMapper.map("upper body ergometer"))
        assertEquals(Equipment.CARDIO_MACHINE, EquipmentMapper.map("tire"))
    }

    @Test
    fun `unknown value returns OTHER`() {
        assertEquals(Equipment.OTHER, EquipmentMapper.map("unknown equipment"))
        assertEquals(Equipment.OTHER, EquipmentMapper.map(""))
    }

    @Test
    fun `case insensitive mapping`() {
        assertEquals(Equipment.BARBELL, EquipmentMapper.map("BARBELL"))
        assertEquals(Equipment.DUMBBELL, EquipmentMapper.map("Dumbbell"))
    }
}
