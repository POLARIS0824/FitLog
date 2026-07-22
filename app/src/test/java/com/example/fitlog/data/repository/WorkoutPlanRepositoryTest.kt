package com.example.fitlog.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.fitlog.data.local.AppDatabase
import com.example.fitlog.data.local.entity.plan.PlannedExerciseEntity
import com.example.fitlog.data.local.entity.plan.PlannedSessionEntity
import com.example.fitlog.data.local.entity.plan.WorkoutPlanEntity
import com.example.fitlog.model.Difficulty
import com.example.fitlog.model.user.TrainingGoal
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

/**
 * 训练计划仓库 [WorkoutPlanRepository] 的单元测试。
 * 使用 Robolectric 在 JVM 环境下验证训练计划（3层嵌套：计划 -> 训练日 -> 动作配置）的存储、级联关系和聚合查询。
 */
@RunWith(RobolectricTestRunner::class)
class WorkoutPlanRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: WorkoutPlanRepository

    /**
     * 初始化内存 Room 数据库和 WorkoutPlanRepository 实例。
     */
    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = WorkoutPlanRepository(db.workoutPlanDao())
    }

    /**
     * 测试后关闭数据库。
     */
    @After
    fun closeDb() {
        db.close()
    }

    /**
     * 测试最基础的计划实体插入与获取。
     */
    @Test
    fun testInsertPlan() = runTest {
        val plan = com.example.fitlog.model.WorkoutPlan(
            id = "push-pull-legs-1",
            name = "推拉腿经典计划",
            description = "经典的推拉腿三分化训练计划",
            goal = TrainingGoal.HYPERTROPHY,
            difficulty = Difficulty.INTERMEDIATE,
            durationWeeks = 8,
            sessionsPerWeek = 3,
            isCustom = false,
            createdAt = LocalDate.of(2026, 5, 20),
            sessions = emptyList()
        )

        repository.insert(plan)

        val fetched = repository.getPlanById("push-pull-legs-1")
        assertNotNull(fetched)
        assertEquals("推拉腿经典计划", fetched?.name)
        assertEquals(TrainingGoal.HYPERTROPHY, fetched?.goal)
        assertEquals(Difficulty.INTERMEDIATE, fetched?.difficulty)
        assertEquals(8, fetched?.durationWeeks)
    }

    /**
     * 测试完整的 3 层训练计划级联查询与拼装。
     */
    @Test
    fun testGetPlanWithFullNestedHierarchy() = runTest {
        // 1. 准备并插入 3 层级联的计划数据
        val planEntity = WorkoutPlanEntity(
            id = "ppl-nested",
            name = "嵌套推拉腿计划",
            description = "测试嵌套级联",
            goal = "HYPERTROPHY",
            difficulty = "INTERMEDIATE",
            durationWeeks = 4,
            sessionsPerWeek = 3,
            isCustom = false,
            createdAt = LocalDate.of(2026, 5, 20)
        )

        val sessions = listOf(
            PlannedSessionEntity(
                id = "session-1",
                planId = "ppl-nested",
                name = "推日 (Push)",
                description = "胸肩三头训练",
                dayNumber = 1,
                weekNumber = 1,
                targetDurationMinutes = 60,
                completedWorkoutId = null
            )
        )

        val exercises = listOf(
            PlannedExerciseEntity(
                id = "exercise-config-1",
                sessionId = "session-1",
                exerciseKey = "barbell-bench-press",
                exerciseName = "杠铃卧推",
                targetSets = 4,
                targetRepsMin = 8,
                targetRepsMax = 12,
                targetWeightKg = 80f,
                targetRpe = 8,
                restSeconds = 90,
                notes = "专注于离心收缩",
                order = 1
            )
        )

        // 使用 DAO 提供的级联事务保存方法
        db.workoutPlanDao().savePlanWithSessions(planEntity, sessions, exercises)

        // 2. 使用 Repository 获取完整的多级计划
        val fetched = repository.getPlanById("ppl-nested")

        // 3. 验证级联数据
        assertNotNull(fetched)
        assertEquals("嵌套推拉腿计划", fetched?.name)
        assertEquals(1, fetched?.sessions?.size)

        val session = fetched?.sessions?.get(0)
        assertEquals("推日 (Push)", session?.name)
        assertEquals(1, session?.dayNumber)
        assertEquals(1, session?.weekNumber)
        assertEquals(1, session?.exercises?.size)

        val exercise = session?.exercises?.get(0)
        assertEquals("barbell-bench-press", exercise?.exerciseKey)
        assertEquals("杠铃卧推", exercise?.exerciseName)
        assertEquals(4, exercise?.targetSets)
        assertEquals(8, exercise?.targetRepsMin)
        assertEquals(12, exercise?.targetRepsMax)
        assertEquals(80f, exercise?.targetWeightKg)
        assertEquals(90, exercise?.restSeconds)
    }

    /**
     * 测试计划列表获取。
     */
    @Test
    fun testGetAllPlans() = runTest {
        val plan1 = WorkoutPlanEntity(
            id = "plan-1",
            name = "计划A",
            description = null,
            goal = null,
            difficulty = null,
            durationWeeks = 4,
            sessionsPerWeek = 2,
            isCustom = false,
            createdAt = LocalDate.of(2026, 5, 1)
        )
        val plan2 = WorkoutPlanEntity(
            id = "plan-2",
            name = "计划B",
            description = null,
            goal = null,
            difficulty = null,
            durationWeeks = 12,
            sessionsPerWeek = 4,
            isCustom = true,
            createdAt = LocalDate.of(2026, 5, 10)
        )

        db.workoutPlanDao().insertPlan(plan1)
        db.workoutPlanDao().insertPlan(plan2)

        val plans = repository.getAllPlans()
        assertEquals(2, plans.size)
        // 按照默认 orderBy createdAt DESC，应该 plan2 在最前面
        assertEquals("plan-2", plans[0].id)
        assertEquals("plan-1", plans[1].id)
    }

    /**
     * 测试计划的删除。
     */
    @Test
    fun testDeletePlan() = runTest {
        val plan = WorkoutPlanEntity(
            id = "plan-to-delete",
            name = "待删除计划",
            description = null,
            goal = null,
            difficulty = null,
            durationWeeks = 4,
            sessionsPerWeek = 3,
            isCustom = true,
            createdAt = LocalDate.now()
        )
        db.workoutPlanDao().insertPlan(plan)

        // 使用 DAO 提供的删除逻辑
        db.workoutPlanDao().deletePlan("plan-to-delete")

        val fetched = repository.getPlanById("plan-to-delete")
        assertNull(fetched)
    }
}
