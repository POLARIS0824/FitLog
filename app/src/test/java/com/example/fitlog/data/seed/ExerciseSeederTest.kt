package com.example.fitlog.data.seed

import com.example.fitlog.model.BodyPart
import com.example.fitlog.model.Equipment
import com.example.fitlog.model.Muscle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ExerciseSeedMapper] 的单元测试。
 *
 * 验证种子数据 DTO 到 Room 实体的完整映射逻辑，包括 kebab-case ID 生成、
 * 肌肉合并、isCompound 推导、中文指导提取和图片 URL 构建。
 */
class ExerciseSeederTest {

    // ── kebab-case 转换 ──

    @Test
    fun `toKebabCase converts name correctly`() {
        with(ExerciseSeedMapper) {
            assertEquals("barbell-bench-front-squat", "barbell bench front squat".toKebabCase())
            assertEquals("3-4-sit-up", "3/4 sit-up".toKebabCase())
            assertEquals("pull-up", "pull-up".toKebabCase())
            assertEquals("ab-crunch-machine", "ab crunch machine".toKebabCase())
            assertEquals("90-90-hamstring", "90/90 hamstring".toKebabCase())
        }
    }

    // ── 基本映射 ──

    @Test
    fun `basic exercise maps correctly`() {
        val seed = createSeedData(
            name = "barbell bench press",
            target = "pectorals",
            muscle_group = "shoulders",
            secondary_muscles = listOf("triceps"),
            equipment = "barbell",
            body_part = "chest",
        )

        val entity = ExerciseSeedMapper.toEntity(seed)
        assertNotNull(entity)
        assertEquals("barbell-bench-press", entity!!.id)
        assertEquals("Barbell bench press", entity.name)
        assertEquals(listOf(Muscle.CHEST, Muscle.SHOULDERS), entity.primaryMuscles)
        assertEquals(listOf(Muscle.TRICEPS), entity.secondaryMuscles)
        assertEquals(Equipment.BARBELL, entity.equipment)
        assertEquals(BodyPart.CHEST, entity.bodyPart)
    }

    @Test
    fun `muscle_group same as target is not duplicated`() {
        val seed = createSeedData(
            target = "biceps",
            muscle_group = "biceps",
        )

        val entity = ExerciseSeedMapper.toEntity(seed)
        assertNotNull(entity)
        assertEquals(listOf(Muscle.BICEPS), entity!!.primaryMuscles)
    }

    @Test
    fun `unmappable target returns null`() {
        val seed = createSeedData(target = "unknown_muscle")

        val entity = ExerciseSeedMapper.toEntity(seed)
        assertNull(entity)
    }

    // ── isCompound 推导 ──

    @Test
    fun `isCompound true when 3+ total muscles`() {
        val seed = createSeedData(
            target = "quads",
            muscle_group = "glutes",
            secondary_muscles = listOf("hamstrings"),
        )

        val entity = ExerciseSeedMapper.toEntity(seed)
        assertNotNull(entity)
        assertTrue(entity!!.isCompound)
    }

    @Test
    fun `isCompound false when fewer than 3 muscles`() {
        val seed = createSeedData(
            target = "biceps",
            muscle_group = "biceps",
            secondary_muscles = emptyList(),
        )

        val entity = ExerciseSeedMapper.toEntity(seed)
        assertNotNull(entity)
        assertTrue(!entity!!.isCompound)
    }

    // ── 中文指导 ──

    @Test
    fun `chinese instructions are extracted`() {
        val seed = createSeedData(
            instructions = mapOf("zh" to "站立，双脚分开与肩同宽"),
            instruction_steps = mapOf("zh" to listOf("第一步", "第二步", "第三步")),
        )

        val entity = ExerciseSeedMapper.toEntity(seed)
        assertNotNull(entity)
        assertEquals("站立，双脚分开与肩同宽", entity!!.description)
        assertEquals(listOf("第一步", "第二步", "第三步"), entity.instructions)
    }

    // ── 图片 URL ──

    @Test
    fun `image url extracts filename only`() {
        val seed = createSeedData(
            image = "images/0025-EIeI8Vf.jpg",
        )

        val entity = ExerciseSeedMapper.toEntity(seed)
        assertNotNull(entity)
        assertEquals("0025-EIeI8Vf.jpg", entity!!.imageUrl)
    }

    @Test
    fun `gif url builds full github url`() {
        val seed = createSeedData(
            gif_url = "videos/0025-EIeI8Vf.gif",
        )

        val entity = ExerciseSeedMapper.toEntity(seed)
        assertNotNull(entity)
        assertEquals(
            "https://raw.githubusercontent.com/hasaneyldrm/exercises-dataset/main/videos/0025-EIeI8Vf.gif",
            entity!!.gifUrl
        )
    }

    // ── 辅助方法 ──

    private fun createSeedData(
        id: String = "0001",
        name: String = "test exercise",
        body_part: String = "chest",
        equipment: String = "barbell",
        target: String = "pectorals",
        muscle_group: String = "shoulders",
        secondary_muscles: List<String> = listOf("triceps"),
        instructions: Map<String, String> = mapOf("zh" to "测试指导"),
        instruction_steps: Map<String, List<String>> = mapOf("zh" to listOf("步骤一")),
        image: String = "images/0001-test.jpg",
        gif_url: String = "videos/0001-test.gif",
    ) = ExerciseSeedData(
        id = id,
        name = name,
        body_part = body_part,
        equipment = equipment,
        target = target,
        muscle_group = muscle_group,
        secondary_muscles = secondary_muscles,
        instructions = instructions,
        instruction_steps = instruction_steps,
        image = image,
        gif_url = gif_url,
    )
}
