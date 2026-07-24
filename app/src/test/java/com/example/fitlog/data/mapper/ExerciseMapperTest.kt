package com.example.fitlog.data.mapper

import com.example.fitlog.data.local.entity.ExerciseEntity
import com.example.fitlog.model.BodyPart
import com.example.fitlog.model.Equipment
import com.example.fitlog.model.Muscle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [ExerciseMapper] 的单元测试。
 * 验证动作库 Entity 与领域模型之间的全字段双向映射（含枚举、列表类型）。
 */
class ExerciseMapperTest {

    private val fullEntity = ExerciseEntity(
        id = "barbell-bench-press",
        name = "Barbell Bench Press",
        primaryMuscles = listOf(Muscle.CHEST),
        secondaryMuscles = listOf(Muscle.SHOULDERS, Muscle.TRICEPS),
        isCompound = true,
        isCustom = false,
        equipment = Equipment.BARBELL,
        bodyPart = BodyPart.CHEST,
        description = "胸部王牌动作",
        instructions = listOf("躺平", "握杠", "推起"),
        imageUrl = "0001-abc.jpg",
        gifUrl = "https://example.com/0001-abc.gif",
    )

    /**
     * 测试 Entity 转领域模型：所有字段（含枚举与列表）完整透传。
     */
    @Test
    fun testEntityToModel_allFieldsMapped() {
        val model = fullEntity.toModel()

        assertEquals("barbell-bench-press", model.id)
        assertEquals("Barbell Bench Press", model.name)
        assertEquals(listOf(Muscle.CHEST), model.primaryMuscles)
        assertEquals(listOf(Muscle.SHOULDERS, Muscle.TRICEPS), model.secondaryMuscles)
        assertEquals(true, model.isCompound)
        assertEquals(false, model.isCustom)
        assertEquals(Equipment.BARBELL, model.equipment)
        assertEquals(BodyPart.CHEST, model.bodyPart)
        assertEquals("胸部王牌动作", model.description)
        assertEquals(listOf("躺平", "握杠", "推起"), model.instructions)
        assertEquals("0001-abc.jpg", model.imageUrl)
        assertEquals("https://example.com/0001-abc.gif", model.gifUrl)
    }

    /**
     * 测试 Entity ↔ Model 双向转换的对称性（往返转换后与原值相等）。
     */
    @Test
    fun testRoundTripSymmetry() {
        val roundTripped = fullEntity.toModel().toEntity()
        assertEquals(fullEntity, roundTripped)
    }

    /**
     * 测试可空字段为 null 时的透传。
     */
    @Test
    fun testNullableFields_passThroughAsNull() {
        val entity = ExerciseEntity(
            id = "custom-move",
            name = "自定义动作",
            primaryMuscles = listOf(Muscle.CORE),
            secondaryMuscles = emptyList(),
            isCompound = false,
            isCustom = true,
            equipment = null,
            bodyPart = BodyPart.WAIST,
            description = null,
            instructions = emptyList(),
            imageUrl = null,
            gifUrl = null,
        )

        val model = entity.toModel()

        assertNull(model.equipment)
        assertNull(model.description)
        assertNull(model.imageUrl)
        assertNull(model.gifUrl)
        assertEquals(BodyPart.WAIST, model.bodyPart)
        assertEquals(true, model.isCustom)
    }
}
