package com.example.fitlog.model.ai

import com.example.fitlog.model.Workout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * [parseCoachInsight]、[CoachAction.fromString] 与缓存指纹的单元测试（纯 JVM）。
 *
 * 解析器是 AI 脏输出的第一道防线：fence 包裹、前后多余文字、畸形 JSON、
 * 未知 action 都必须安全降级（返回 null 或 [CoachAction.NONE]），
 * 绝不让异常逃逸到 UI 层。
 */
class CoachInsightTest {

    // ── parseCoachInsight ──

    @Test
    fun `parse plain json`() {
        val insight = parseCoachInsight(
            """{"observation":"昨天练了腿","recommendation":"今天适度有氧","action":"REST"}""",
        )
        assertEquals(
            CoachInsight("昨天练了腿", "今天适度有氧", CoachAction.REST),
            insight,
        )
    }

    @Test
    fun `parse json wrapped in markdown fence`() {
        val raw = """
            ```json
            {"observation":"本周训练均衡","recommendation":"按计划练背","action":"START_WORKOUT"}
            ```
        """.trimIndent()
        val insight = parseCoachInsight(raw)
        assertEquals("本周训练均衡", insight?.observation)
        assertEquals(CoachAction.START_WORKOUT, insight?.action)
    }

    @Test
    fun `parse json with surrounding prose`() {
        val raw = """好的，这是今日建议：{"observation":"观察","recommendation":"建议","action":"REST"} 希望有帮助"""
        val insight = parseCoachInsight(raw)
        assertEquals("观察", insight?.observation)
        assertEquals("建议", insight?.recommendation)
    }

    @Test
    fun `parse returns null for malformed json`() {
        assertNull(parseCoachInsight("{not json}"))
        assertNull(parseCoachInsight("{\"observation\":\"未闭合"))
    }

    @Test
    fun `parse returns null when no braces present`() {
        assertNull(parseCoachInsight("今天适合练腿"))
    }

    @Test
    fun `parse returns null when required fields blank`() {
        assertNull(parseCoachInsight("""{"observation":"","recommendation":"建议"}"""))
        assertNull(parseCoachInsight("""{"observation":"观察","recommendation":"  "}"""))
    }

    @Test
    fun `unknown action falls back to NONE`() {
        val insight = parseCoachInsight(
            """{"observation":"观察","recommendation":"建议","action":"DANCE"}""",
        )
        assertEquals(CoachAction.NONE, insight?.action)
    }

    @Test
    fun `missing action falls back to NONE`() {
        val insight = parseCoachInsight("""{"observation":"观察","recommendation":"建议"}""")
        assertEquals(CoachAction.NONE, insight?.action)
    }

    // ── CoachAction.fromString ──

    @Test
    fun `fromString is case insensitive and null safe`() {
        assertEquals(CoachAction.REST, CoachAction.fromString("rest"))
        assertEquals(CoachAction.START_WORKOUT, CoachAction.fromString(" start_workout "))
        assertEquals(CoachAction.NONE, CoachAction.fromString(null))
        assertEquals(CoachAction.NONE, CoachAction.fromString(""))
    }

    // ── 缓存指纹 ──

    private val today = LocalDate.of(2026, 7, 27)

    private fun fingerprint(
        today: LocalDate = this.today,
        planId: String? = "plan-1",
        nextSessionId: String? = "s1",
        latestWorkoutId: Long? = 7L,
        weekCompleted: Int = 1,
        todayCompleted: Boolean = false,
    ) = coachInsightFingerprint(today, planId, nextSessionId, latestWorkoutId, weekCompleted, todayCompleted)

    @Test
    fun `fingerprint stable for same inputs`() {
        assertEquals(fingerprint(), fingerprint())
    }

    @Test
    fun `fingerprint changes when any dimension changes`() {
        val base = fingerprint()
        assertNotEquals(base, fingerprint(today = today.plusDays(1)))
        assertNotEquals(base, fingerprint(planId = "plan-2"))
        assertNotEquals(base, fingerprint(planId = null))
        assertNotEquals(base, fingerprint(nextSessionId = "s2"))
        assertNotEquals(base, fingerprint(nextSessionId = null))
        assertNotEquals(base, fingerprint(latestWorkoutId = 8L))
        assertNotEquals(base, fingerprint(latestWorkoutId = null))
        assertNotEquals(base, fingerprint(weekCompleted = 2))
        assertNotEquals(base, fingerprint(todayCompleted = true))
    }

    @Test
    fun `context fingerprint uses first recent workout as latest`() {
        val context = CoachInsightContext(
            profile = null,
            weekCompleted = 1,
            weekTarget = 4,
            todayCompleted = false,
            activePlan = null,
            nextSession = null,
            recentWorkouts = listOf(workout(id = 42L), workout(id = 7L)),
            catalog = emptyList(),
            today = today,
        )
        assertEquals(fingerprint(planId = null, nextSessionId = null, latestWorkoutId = 42L), context.fingerprint())
    }

    private fun workout(id: Long) = Workout(
        id = id,
        userId = 0L,
        date = today,
        exercises = emptyList(),
        feelings = null,
    )
}
