package com.example.fitlog.feature.today

import com.example.fitlog.model.PlannedSession
import com.example.fitlog.model.Workout
import com.example.fitlog.model.ai.CoachAction
import com.example.fitlog.model.user.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * [CoachInsightBuilder] 的单元测试（纯 JVM）。
 *
 * 验证问候语时段边界、observation 模板、recommendation 优先级、
 * 规则版 action 兜底与降级策略。
 */
class CoachInsightBuilderTest {

    private val today = LocalDate.of(2026, 7, 24)

    // ── greeting 时段边界 ──

    @Test
    fun `greeting follows hour boundaries`() {
        assertEquals("晚上好", buildInsight(hour = 4).greeting)
        assertEquals("早上好", buildInsight(hour = 5).greeting)
        assertEquals("早上好", buildInsight(hour = 10).greeting)
        assertEquals("中午好", buildInsight(hour = 11).greeting)
        assertEquals("中午好", buildInsight(hour = 12).greeting)
        assertEquals("下午好", buildInsight(hour = 13).greeting)
        assertEquals("下午好", buildInsight(hour = 17).greeting)
        assertEquals("晚上好", buildInsight(hour = 18).greeting)
        assertEquals("晚上好", buildInsight(hour = 23).greeting)
    }

    @Test
    fun `greeting includes user name when profile exists`() {
        val state = buildInsight(hour = 8, profile = profile(name = "Polaris"))
        assertEquals("早上好，Polaris", state.greeting)
        assertEquals("Polaris", state.userName)
    }

    @Test
    fun `greeting omits name when profile name is blank`() {
        val state = buildInsight(hour = 8, profile = profile(name = "  "))
        assertEquals("早上好", state.greeting)
    }

    // ── observation ──

    @Test
    fun `observation without workout history`() {
        val state = buildInsight(latestWorkout = null)
        assertEquals("还没有训练记录，从第一练开始吧", state.observation)
    }

    @Test
    fun `observation with workout today shows 今天已练`() {
        val state = buildInsight(
            weekCompleted = 1,
            weekTarget = 3,
            latestWorkout = workout(date = today),
        )
        assertEquals("本周已练 1/3 次 · 今天已练", state.observation)
    }

    @Test
    fun `observation with workout days ago`() {
        val state = buildInsight(
            weekCompleted = 2,
            weekTarget = 4,
            latestWorkout = workout(date = today.minusDays(3)),
        )
        assertEquals("本周已练 2/4 次 · 距上次训练 3 天", state.observation)
    }

    // ── recommendation 优先级 ──

    @Test
    fun `recommendation prioritizes rest when today completed`() {
        val state = buildInsight(
            todayCompleted = true,
            nextSession = session(name = "课 A"),
        )
        assertEquals("今天的训练已完成，好好休息恢复", state.recommendation)
    }

    @Test
    fun `recommendation shows next session name`() {
        val state = buildInsight(nextSession = session(name = "课 A · 下肢 + 推"))
        assertEquals("下一课：课 A · 下肢 + 推", state.recommendation)
    }

    @Test
    fun `recommendation guides to next plan when plan fully completed`() {
        val state = buildInsight(hasActivePlan = true, nextSession = null)
        assertEquals("当前计划已全部完成，去挑选下一套计划吧", state.recommendation)
    }

    @Test
    fun `recommendation falls back to light rest suggestion`() {
        val state = buildInsight(hasActivePlan = false, nextSession = null)
        assertEquals("今天适合休息，或做一次轻量恢复训练", state.recommendation)
    }

    // ── 规则版 action 兜底 ──

    @Test
    fun `action is START_WORKOUT when next session pending`() {
        val state = buildInsight(todayCompleted = false, nextSession = session(name = "课 A"))
        assertEquals(CoachAction.START_WORKOUT, state.action)
    }

    @Test
    fun `action is NONE when today completed`() {
        val state = buildInsight(todayCompleted = true, nextSession = session(name = "课 A"))
        assertEquals(CoachAction.NONE, state.action)
    }

    @Test
    fun `action is NONE without next session`() {
        val state = buildInsight(todayCompleted = false, nextSession = null)
        assertEquals(CoachAction.NONE, state.action)
    }

    // ── isAvailable 降级策略 ──

    @Test
    fun `isAvailable false for brand new user`() {
        val state = buildInsight(hasActivePlan = false, latestWorkout = null)
        assertFalse(state.isAvailable)
    }

    @Test
    fun `isAvailable true with active plan or workout history`() {
        assertTrue(buildInsight(hasActivePlan = true).isAvailable)
        assertTrue(buildInsight(latestWorkout = workout(date = today)).isAvailable)
    }

    // ── 辅助方法 ──

    private fun buildInsight(
        hour: Int = 9,
        profile: UserProfile? = null,
        weekCompleted: Int = 0,
        weekTarget: Int = 4,
        latestWorkout: Workout? = null,
        nextSession: PlannedSession? = null,
        todayCompleted: Boolean = false,
        hasActivePlan: Boolean = false,
    ) = CoachInsightBuilder.build(
        profile = profile,
        weekCompleted = weekCompleted,
        weekTarget = weekTarget,
        latestWorkout = latestWorkout,
        nextSession = nextSession,
        todayCompleted = todayCompleted,
        hasActivePlan = hasActivePlan,
        today = today,
        hour = hour,
    )

    private fun profile(name: String) = UserProfile(
        id = 1L,
        name = name,
        age = null,
        gender = null,
        height = null,
        weight = null,
        trainingGoal = null,
    )

    private fun workout(date: LocalDate) = Workout(
        id = 1L,
        userId = 0L,
        date = date,
        exercises = emptyList(),
        feelings = null,
    )

    private fun session(name: String) = PlannedSession(
        id = "s1",
        name = name,
        description = null,
        dayNumber = 1,
        weekNumber = 1,
        targetDurationMinutes = 60,
        exercises = emptyList(),
    )
}
