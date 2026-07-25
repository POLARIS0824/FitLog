package com.example.fitlog.data.seed

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * [PresetPlans] 的单元测试（纯 JVM，无需 Robolectric）。
 *
 * 主防线：断言预置计划引用的每个 exerciseKey 都存在于动作库种子
 * `res/raw/exercises.json` 中——改种子 JSON 或改计划都会立刻测试失败。
 */
class PresetPlansTest {

    /** 仅取 name 字段做 kebab-case 化，与 [ExerciseSeedMapper] 的 ID 生成规则一致。 */
    @Serializable
    private data class ExerciseNameEntry(val name: String)

    private val seedExerciseKeys: Set<String> by lazy {
        val json = File("src/main/res/raw/exercises.json").readText()
        Json { ignoreUnknownKeys = true }
            .decodeFromString<List<ExerciseNameEntry>>(json)
            .map { with(ExerciseSeedMapper) { it.name.toKebabCase() } }
            .toSet()
    }

    /**
     * 测试预置计划引用的动作 key 全部存在于动作库种子中。
     */
    @Test
    fun `all exerciseKeys exist in exercise seed library`() {
        // 健壮性检查：种子库应为千级规模（1324 条目中存在少量 kebab 重名，不去硬编码精确值）
        assertTrue("动作库种子规模异常，可能读错了文件", seedExerciseKeys.size > 1000)

        PresetPlans.all().forEach { plan ->
            val keys = plan.sessions.flatMap { it.exercises }.map { it.exerciseKey }.distinct()
            val missing = keys - seedExerciseKeys
            assertTrue(
                "计划 ${plan.id} 引用了不存在的动作 key：$missing",
                missing.isEmpty(),
            )
        }
    }

    /**
     * 测试每套计划的训练日总数 = 周数 × 每周次数。
     */
    @Test
    fun `session count equals durationWeeks times sessionsPerWeek`() {
        PresetPlans.all().forEach { plan ->
            assertEquals(
                "计划 ${plan.id} 的训练日总数不符",
                plan.durationWeeks * plan.sessionsPerWeek,
                plan.sessions.size,
            )
        }
    }

    /**
     * 测试 weekNumber/dayNumber 连续无空洞：按 (week, day) 排序后与序号一一对应。
     */
    @Test
    fun `week and day numbers are contiguous`() {
        PresetPlans.all().forEach { plan ->
            val coordinates = plan.sessions.map { it.weekNumber to it.dayNumber }.sortedWith(
                compareBy({ it.first }, { it.second }),
            )
            val expected = (1..plan.durationWeeks).flatMap { week ->
                (1..plan.sessionsPerWeek).map { day -> week to day }
            }
            assertEquals("计划 ${plan.id} 的周/日编号不连续", expected, coordinates)
        }
    }

    /**
     * 测试预置计划的固定属性：isCustom=false、rawPlanText=null、session id 全局唯一。
     */
    @Test
    fun `preset plans are non-custom with unique session ids`() {
        val plans = PresetPlans.all()
        assertTrue(plans.all { !it.isCustom })
        assertTrue(plans.all { it.rawPlanText == null })

        val sessionIds = plans.flatMap { it.sessions }.map { it.id }
        assertEquals(sessionIds.size, sessionIds.distinct().size)
    }

    /**
     * 测试训练日内动作 order 连续从 0 开始。
     */
    @Test
    fun `exercise orders are zero-based and contiguous within each session`() {
        PresetPlans.all().forEach { plan ->
            plan.sessions.forEach { session ->
                val orders = session.exercises.map { it.order }.sorted()
                assertEquals(
                    "计划 ${plan.id} 的 ${session.id} 动作序号不连续",
                    (0 until session.exercises.size).toList(),
                    orders,
                )
            }
        }
    }
}
