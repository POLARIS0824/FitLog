package com.example.fitlog.data.mapper

import com.example.fitlog.data.local.entity.UserProfileEntity
import com.example.fitlog.model.user.Gender
import com.example.fitlog.model.user.TrainingGoal
import com.example.fitlog.model.user.TrainingLevel
import com.example.fitlog.model.user.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [UserProfileMapper] 的单元测试。
 * 验证用户资料 Entity 与领域模型之间的映射（含 Gender/TrainingGoal 枚举转换）。
 */
class UserProfileMapperTest {

    /**
     * 测试 Entity 转领域模型：性别与训练目标字符串还原为枚举。
     */
    @Test
    fun testEntityToModel_enumsParsed() {
        val entity = UserProfileEntity(
            id = 1L,
            name = "张三",
            age = 25,
            gender = "MALE",
            height = 175f,
            weight = 70f,
            trainingGoal = "STRENGTH",
        )

        val model = entity.toModel()

        assertEquals(1L, model.id)
        assertEquals("张三", model.name)
        assertEquals(25, model.age)
        assertEquals(Gender.MALE, model.gender)
        assertEquals(175f, model.height)
        assertEquals(70f, model.weight)
        assertEquals(TrainingGoal.STRENGTH, model.trainingGoal)
    }

    /**
     * 测试可空字段为 null 时全部透传为 null。
     */
    @Test
    fun testEntityToModel_nullableFields_passThroughAsNull() {
        val entity = UserProfileEntity(
            id = 2L,
            name = "李四",
            age = null,
            gender = null,
            height = null,
            weight = null,
            trainingGoal = null,
        )

        val model = entity.toModel()

        assertNull(model.age)
        assertNull(model.gender)
        assertNull(model.height)
        assertNull(model.weight)
        assertNull(model.trainingGoal)
    }

    /**
     * 测试 Entity 转领域模型时 trainingLevel 恒为空 Map。
     *
     * 注意：当前 user_profiles 表没有 trainingLevel 列，
     * 训练水平在持久化过程中不被保存，读取时只能重建为空。
     */
    @Test
    fun testEntityToModel_trainingLevelAlwaysEmpty() {
        val entity = UserProfileEntity(
            id = 1L,
            name = "张三",
            age = 25,
            gender = "MALE",
            height = 175f,
            weight = 70f,
            trainingGoal = "STRENGTH",
        )

        assertTrue(entity.toModel().trainingLevel.exercises.isEmpty())
    }

    /**
     * 测试领域模型转 Entity：枚举序列化为 name 字符串。
     */
    @Test
    fun testModelToEntity_enumsSerialized() {
        val model = UserProfile(
            id = 3L,
            name = "王五",
            age = 30,
            gender = Gender.FEMALE,
            height = 165f,
            weight = 55f,
            trainingLevel = TrainingLevel(emptyMap()),
            trainingGoal = TrainingGoal.HYPERTROPHY,
        )

        val entity = model.toEntity()

        assertEquals(3L, entity.id)
        assertEquals("王五", entity.name)
        assertEquals(30, entity.age)
        assertEquals("FEMALE", entity.gender)
        assertEquals(165f, entity.height)
        assertEquals(55f, entity.weight)
        assertEquals("HYPERTROPHY", entity.trainingGoal)
    }

    /**
     * 测试领域模型的可空枚举为 null 时序列化为 null。
     */
    @Test
    fun testModelToEntity_nullEnums_serializedAsNull() {
        val model = UserProfile(
            id = 4L,
            name = "赵六",
            age = null,
            gender = null,
            height = null,
            weight = null,
            trainingLevel = TrainingLevel(emptyMap()),
            trainingGoal = null,
        )

        val entity = model.toEntity()

        assertNull(entity.gender)
        assertNull(entity.trainingGoal)
        assertNull(entity.age)
        assertNull(entity.height)
        assertNull(entity.weight)
    }
}
