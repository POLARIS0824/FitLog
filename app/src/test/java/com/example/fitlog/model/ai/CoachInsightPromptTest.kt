package com.example.fitlog.model.ai

import com.example.fitlog.model.BodyPart
import com.example.fitlog.model.Exercise
import com.example.fitlog.model.ExerciseLog
import com.example.fitlog.model.Muscle
import com.example.fitlog.model.PlannedExerciseItem
import com.example.fitlog.model.PlannedSession
import com.example.fitlog.model.SetLog
import com.example.fitlog.model.SetType
import com.example.fitlog.model.Workout
import com.example.fitlog.model.WorkoutPlan
import com.example.fitlog.model.user.TrainingGoal
import com.example.fitlog.model.user.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * [CoachInsightPrompt] 的单元测试（纯 JVM）。
 *
 * 锁定 prompt 契约：system 的输出约束、user 的上下文区块
 * （今日课次/计划进度/最近训练摘要），AI 的决策质量直接取决于这些输入。
 */
class CoachInsightPromptTest {

    private val today = LocalDate.of(2026, 7, 27)

    // ── system prompt 契约 ──

    @Test
    fun `system prompt constrains json output and allowed actions`() {
        val content = CoachInsightPrompt.SYSTEM_PROMPT.content
        assertEquals("system", CoachInsightPrompt.SYSTEM_PROMPT.role)
        assertTrue(content.contains("JSON"))
        assertTrue(content.contains("START_WORKOUT"))
        assertTrue(content.contains("REST"))
        // v1 不产出 ADJUST_PLAN
        assertTrue(!content.contains("ADJUST_PLAN"))
    }

    // ── user prompt 上下文区块 ──

    @Test
    fun `user prompt includes session name and plan position`() {
        val messages = CoachInsightPrompt.buildMessages(context())

        assertEquals(2, messages.size)
        assertEquals("system", messages[0].role)
        assertEquals("user", messages[1].role)
        val user = messages[1].content
        assertTrue(user.contains("腿日 · 股四头后侧链"))
        assertTrue(user.contains("增肌计划"))
        assertTrue(user.contains("第 2 周第 1 天"))
        assertTrue(user.contains("已完成 2/4 次"))
        assertTrue(user.contains("今天未练"))
    }

    @Test
    fun `user prompt includes profile goal when present`() {
        val messages = CoachInsightPrompt.buildMessages(
            context(profile = UserProfile(1L, "Polaris", 25, null, null, null, TrainingGoal.HYPERTROPHY)),
        )
        val user = messages[1].content
        assertTrue(user.contains("名字：Polaris"))
        assertTrue(user.contains("训练目标：增肌"))
    }

    @Test
    fun `user prompt summarizes recent workout with dominant body parts`() {
        val squat = Exercise(
            id = "barbell-squat",
            name = "Barbell Squat",
            primaryMuscles = listOf(Muscle.CORE),
            bodyPart = BodyPart.UPPER_LEGS,
        )
        val workout = Workout(
            id = 1L,
            userId = 0L,
            date = today.minusDays(1),
            exercises = listOf(
                ExerciseLog(
                    name = "Barbell Squat",
                    exerciseKey = "barbell-squat",
                    sets = listOf(
                        SetLog(100f, 5, SetType.WORKING),
                        SetLog(100f, 5, SetType.WORKING),
                        SetLog(60f, 10, SetType.WARMUP), // 热身组不计入
                    ),
                ),
            ),
            feelings = null,
        )
        val messages = CoachInsightPrompt.buildMessages(
            context(recentWorkouts = listOf(workout), catalog = listOf(squat)),
        )
        val user = messages[1].content
        assertTrue(user.contains("7月26日"))
        assertTrue(user.contains("腿/臀"))
        assertTrue(user.contains("2 组正式组"))
        assertTrue(user.contains("容量 1000 kg") || user.contains("容量 1.0 吨"))
    }

    @Test
    fun `user prompt handles no plan and no history`() {
        val messages = CoachInsightPrompt.buildMessages(
            context(activePlan = null, nextSession = null, recentWorkouts = emptyList()),
        )
        val user = messages[1].content
        assertTrue(user.contains("无激活计划（自由训练）"))
        assertTrue(user.contains("无训练记录"))
    }

    @Test
    fun `user prompt notes plan fully completed`() {
        val messages = CoachInsightPrompt.buildMessages(context(nextSession = null))
        assertTrue(messages[1].content.contains("当前计划已全部完成"))
    }

    // ── 夹具 ──

    private fun context(
        profile: UserProfile? = null,
        activePlan: WorkoutPlan? = plan(),
        nextSession: PlannedSession? = session(),
        recentWorkouts: List<Workout> = emptyList(),
        catalog: List<Exercise> = emptyList(),
    ) = CoachInsightContext(
        profile = profile,
        weekCompleted = 2,
        weekTarget = 4,
        todayCompleted = false,
        activePlan = activePlan,
        nextSession = nextSession,
        recentWorkouts = recentWorkouts,
        catalog = catalog,
        today = today,
    )

    private fun plan() = WorkoutPlan(
        id = "plan-1",
        name = "增肌计划",
        description = null,
        goal = null,
        durationWeeks = 4,
        sessionsPerWeek = 4,
        isCustom = false,
        createdAt = today,
        rawPlanText = null,
        sessions = emptyList(),
    )

    private fun session() = PlannedSession(
        id = "w2d1",
        name = "腿日 · 股四头后侧链",
        description = null,
        dayNumber = 1,
        weekNumber = 2,
        targetDurationMinutes = 60,
        exercises = listOf(PlannedExerciseItem(exerciseKey = "barbell-squat", targetSets = 4, order = 0)),
    )
}
