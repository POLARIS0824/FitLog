package com.example.fitlog.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.fitlog.data.local.AppDatabase
import com.example.fitlog.data.local.entity.plan.PlannedSessionEntity
import com.example.fitlog.data.local.entity.plan.WorkoutPlanEntity
import com.example.fitlog.data.local.entity.workout.WorkoutEntity
import com.example.fitlog.model.PlannedExerciseItem
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * [WorkoutPlanDao] 的仪器化测试。
 *
 * 在真实 SQLite 上验证计划 2 层结构（workout_plans → planned_sessions，
 * 动作清单内嵌 JSON 列）的事务保存、级联删除、排序与完成标记的外键行为。
 */
@RunWith(AndroidJUnit4::class)
class WorkoutPlanDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var planDao: WorkoutPlanDao

    /**
     * 创建内存数据库。
     */
    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        planDao = db.workoutPlanDao()
    }

    /**
     * 关闭数据库。
     */
    @After
    fun closeDb() {
        db.close()
    }

    private fun plan(
        id: String,
        name: String = "计划-$id",
        createdAt: LocalDate = LocalDate.of(2026, 5, 1),
    ) = WorkoutPlanEntity(
        id = id,
        name = name,
        description = null,
        goal = null,
        durationWeeks = 4,
        sessionsPerWeek = 3,
        isCustom = true,
        createdAt = createdAt,
        rawPlanText = null,
    )

    private fun session(
        id: String,
        planId: String,
        week: Int,
        day: Int,
        exercises: List<PlannedExerciseItem> = emptyList(),
        completedWorkoutId: Long? = null,
    ) = PlannedSessionEntity(
        id = id,
        planId = planId,
        name = "W${week}D${day}",
        description = null,
        dayNumber = day,
        weekNumber = week,
        targetDurationMinutes = 60,
        exercises = exercises,
        completedWorkoutId = completedWorkoutId,
    )

    private fun exerciseItem(key: String, order: Int) = PlannedExerciseItem(
        exerciseKey = key,
        exerciseName = "动作-$key",
        targetSets = 4,
        targetRepsMin = 8,
        targetRepsMax = 12,
        notes = null,
        order = order,
    )

    /**
     * 测试事务级保存完整计划后，级联查询返回完整结构（含内嵌动作清单）。
     */
    @Test
    fun savePlanWithSessions_thenGetByIdWithDetails_returnsFullHierarchy() = runTest {
        planDao.savePlanWithSessions(
            plan = plan("p1"),
            sessions = listOf(
                session(
                    "s1", "p1", week = 1, day = 1,
                    exercises = listOf(
                        exerciseItem("barbell-bench-press", order = 2),
                        exerciseItem("dumbbell-fly", order = 1),
                    ),
                ),
            ),
        )

        val result = planDao.getPlanByIdWithDetails("p1")
        assertNotNull(result)
        assertEquals("计划-p1", result?.plan?.name)
        assertEquals(1, result?.sessions?.size)

        val exercises = result?.sessions?.get(0)?.exercises
        assertEquals(2, exercises?.size)
        assertEquals(
            setOf("barbell-bench-press", "dumbbell-fly"),
            exercises?.map { it.exerciseKey }?.toSet(),
        )
    }

    /**
     * 测试相同 ID 重复插入计划时被忽略（IGNORE），原记录保留。
     */
    @Test
    fun insertPlan_duplicateId_ignored() = runTest {
        planDao.insertPlanIgnore(plan("p1", name = "旧名字"))
        planDao.insertPlanIgnore(plan("p1", name = "新名字"))

        assertEquals("旧名字", planDao.getPlanById("p1")?.name)
        assertEquals(1, planDao.getAllPlans().size)
    }

    /**
     * 测试更新已存在的计划：返回受影响行数 1，字段更新、不产生新行。
     */
    @Test
    fun updatePlan_updatesExistingRow() = runTest {
        planDao.insertPlanIgnore(plan("p1", name = "旧名字"))

        val rows = planDao.updatePlan(plan("p1", name = "新名字"))

        assertEquals(1, rows)
        assertEquals("新名字", planDao.getPlanById("p1")?.name)
        assertEquals(1, planDao.getAllPlans().size)
    }

    /**
     * 测试更新不存在的计划：返回 0 行受影响（调用方据此走 insert 分支）。
     */
    @Test
    fun updatePlan_missingRow_returnsZero() = runTest {
        assertEquals(0, planDao.updatePlan(plan("p-missing")))
    }

    /**
     * 测试 getAllPlans 按创建日期降序返回。
     */
    @Test
    fun getAllPlans_orderedByCreatedAtDesc() = runTest {
        planDao.insertPlanIgnore(plan("p-old", createdAt = LocalDate.of(2026, 4, 1)))
        planDao.insertPlanIgnore(plan("p-new", createdAt = LocalDate.of(2026, 6, 1)))
        planDao.insertPlanIgnore(plan("p-mid", createdAt = LocalDate.of(2026, 5, 1)))

        assertEquals(
            listOf("p-new", "p-mid", "p-old"),
            planDao.getAllPlans().map { it.id },
        )
    }

    /**
     * 测试 getSessionsByPlanId 按周数、天数升序返回。
     */
    @Test
    fun getSessionsByPlanId_orderedByWeekThenDay() = runTest {
        planDao.insertPlanIgnore(plan("p1"))
        planDao.insertSessions(
            listOf(
                session("s-w2d1", "p1", week = 2, day = 1),
                session("s-w1d3", "p1", week = 1, day = 3),
                session("s-w1d1", "p1", week = 1, day = 1),
            ),
        )

        assertEquals(
            listOf("s-w1d1", "s-w1d3", "s-w2d1"),
            planDao.getSessionsByPlanId("p1").map { it.id },
        )
    }

    /**
     * 测试内嵌动作清单（JSON 列）经真实数据库往返后字段完整。
     */
    @Test
    fun embeddedExercises_roundTripThroughRealDatabase() = runTest {
        planDao.savePlanWithSessions(
            plan = plan("p1"),
            sessions = listOf(
                session(
                    "s1", "p1", week = 1, day = 1,
                    exercises = listOf(
                        PlannedExerciseItem(
                            exerciseKey = "barbell-squat",
                            exerciseName = "杠铃深蹲",
                            targetSets = 5,
                            targetRepsMin = 5,
                            targetRepsMax = 5,
                            notes = "注意膝盖方向, 不要内扣",
                            order = 1,
                        ),
                    ),
                ),
            ),
        )

        val fetched = planDao.getSessionsByPlanId("p1")[0].exercises[0]
        assertEquals("barbell-squat", fetched.exerciseKey)
        assertEquals("杠铃深蹲", fetched.exerciseName)
        assertEquals(5, fetched.targetSets)
        assertEquals(5, fetched.targetRepsMin)
        // 含逗号的备注必须原样保留（JSON 序列化的关键验证）
        assertEquals("注意膝盖方向, 不要内扣", fetched.notes)
    }

    /**
     * 测试删除计划时级联删除其训练日（外键 CASCADE）。
     */
    @Test
    fun deletePlan_cascadesSessions() = runTest {
        planDao.savePlanWithSessions(
            plan = plan("p1"),
            sessions = listOf(
                session("s1", "p1", week = 1, day = 1, exercises = listOf(exerciseItem("e1", 1))),
            ),
        )

        planDao.deletePlan("p1")

        assertNull(planDao.getPlanById("p1"))
        assertTrue(planDao.getSessionsByPlanId("p1").isEmpty())
    }

    /**
     * 测试删除计划下所有训练日，计划本身保留。
     */
    @Test
    fun deleteSessionsByPlanId_keepsPlan() = runTest {
        planDao.savePlanWithSessions(
            plan = plan("p1"),
            sessions = listOf(session("s1", "p1", week = 1, day = 1)),
        )

        planDao.deleteSessionsByPlanId("p1")

        assertNotNull(planDao.getPlanById("p1"))
        assertTrue(planDao.getSessionsByPlanId("p1").isEmpty())
    }

    /**
     * 测试标记与取消训练日完成状态。
     */
    @Test
    fun markThenUnmarkSessionCompleted() = runTest {
        planDao.savePlanWithSessions(
            plan = plan("p1"),
            sessions = listOf(session("s1", "p1", week = 1, day = 1)),
        )
        val workoutId = db.workoutDao().insert(
            WorkoutEntity(
                date = LocalDate.of(2026, 5, 20),
                sourceFileName = null,
                rawContent = null,
            ),
        )

        planDao.markSessionCompleted("s1", workoutId)
        assertEquals(
            workoutId,
            planDao.getSessionsByPlanId("p1")[0].completedWorkoutId,
        )

        planDao.unmarkSessionCompleted("s1")
        assertNull(planDao.getSessionsByPlanId("p1")[0].completedWorkoutId)
    }

    /**
     * 测试删除关联的实际训练记录时，completedWorkoutId 被置为 NULL（SET_NULL），
     * 训练日本身保留。
     */
    @Test
    fun deleteCompletedWorkout_setsCompletedWorkoutIdNull() = runTest {
        planDao.savePlanWithSessions(
            plan = plan("p1"),
            sessions = listOf(session("s1", "p1", week = 1, day = 1)),
        )
        val workoutId = db.workoutDao().insert(
            WorkoutEntity(
                date = LocalDate.of(2026, 5, 20),
                sourceFileName = null,
                rawContent = null,
            ),
        )
        planDao.markSessionCompleted("s1", workoutId)

        db.workoutDao().delete(
            WorkoutEntity(
                id = workoutId,
                date = LocalDate.of(2026, 5, 20),
                sourceFileName = null,
                rawContent = null,
            ),
        )

        val sessions = planDao.getSessionsByPlanId("p1")
        assertEquals(1, sessions.size)
        assertNull(sessions[0].completedWorkoutId)
    }
}
