package com.example.fitlog.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.fitlog.data.local.AppDatabase
import com.example.fitlog.model.PlannedExerciseItem
import com.example.fitlog.model.PlannedSession
import com.example.fitlog.model.WorkoutPlan
import com.example.fitlog.model.user.TrainingGoal
import com.example.fitlog.testing.createTestPreferencesDataStore
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

/**
 * 训练计划仓库 [WorkoutPlanRepository] 的单元测试。
 *
 * 使用 Robolectric 在 JVM 环境下验证训练计划（2 层结构：计划 -> 训练日，
 * 动作清单以 JSON 内嵌于训练日）的存储、级联关系和聚合查询，
 * 以及激活计划的 DataStore 持久化与联动清理。
 */
@RunWith(RobolectricTestRunner::class)
class WorkoutPlanRepositoryTest {

    /**
     * 每个测试方法使用独立的临时目录存放 DataStore 文件。
     */
    @get:Rule
    val tmpFolder = TemporaryFolder()

    private lateinit var db: AppDatabase
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repository: WorkoutPlanRepository

    /**
     * 测试调度器：与 DataStore scope 及 `runTest` 共享同一实例。
     */
    private val testScheduler = TestCoroutineScheduler()

    /**
     * DataStore 内部协程的作用域，测试结束时在 [closeDb] 中取消。
     */
    private lateinit var dataStoreScope: TestScope

    /**
     * 初始化内存 Room 数据库、测试 DataStore 和 WorkoutPlanRepository 实例。
     */
    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dataStoreScope = TestScope(UnconfinedTestDispatcher(testScheduler))
        dataStore = createTestPreferencesDataStore(
            tmpFolder.newFile("plan_prefs.preferences_pb"),
            dataStoreScope,
        )
        repository = WorkoutPlanRepository(db.workoutPlanDao(), dataStore)
    }

    /**
     * 取消 DataStore 作用域并关闭数据库。
     */
    @After
    fun closeDb() {
        dataStoreScope.cancel()
        db.close()
    }

    /**
     * 测试完整的计划保存与获取（含训练日和内嵌动作清单）。
     */
    @Test
    fun testSaveAndGetPlanWithFullHierarchy() = runTest(testScheduler) {
        val plan = createPlan(
            id = "ppl-1",
            name = "推拉腿经典计划",
            sessions = listOf(
                createSession(
                    id = "session-1",
                    name = "推日 (Push)",
                    exercises = listOf(
                        PlannedExerciseItem(
                            exerciseKey = "barbell-bench-press",
                            exerciseName = "杠铃卧推",
                            targetSets = 4,
                            targetRepsMin = 8,
                            targetRepsMax = 12,
                            notes = "专注于离心收缩",
                            order = 1,
                        ),
                        PlannedExerciseItem(
                            exerciseKey = "dumbbell-shoulder-press",
                            exerciseName = "哑铃推举",
                            targetSets = 3,
                            targetRepsMin = 10,
                            targetRepsMax = 12,
                            order = 2,
                        ),
                    ),
                ),
            ),
        )

        repository.save(plan)

        val fetched = repository.getPlanById("ppl-1")
        assertNotNull(fetched)
        assertEquals("推拉腿经典计划", fetched?.name)
        assertEquals(TrainingGoal.HYPERTROPHY, fetched?.goal)
        assertEquals(8, fetched?.durationWeeks)
        assertEquals("AI 原始计划文本", fetched?.rawPlanText)

        assertEquals(1, fetched?.sessions?.size)
        val session = fetched?.sessions?.get(0)
        assertEquals("推日 (Push)", session?.name)
        assertEquals(1, session?.dayNumber)
        assertEquals(1, session?.weekNumber)

        assertEquals(2, session?.exercises?.size)
        val exercise = session?.exercises?.get(0)
        assertEquals("barbell-bench-press", exercise?.exerciseKey)
        assertEquals("杠铃卧推", exercise?.exerciseName)
        assertEquals(4, exercise?.targetSets)
        assertEquals(8, exercise?.targetRepsMin)
        assertEquals(12, exercise?.targetRepsMax)
        assertEquals("专注于离心收缩", exercise?.notes)
    }

    /**
     * 测试计划列表按创建日期降序返回。
     */
    @Test
    fun testGetAllPlans() = runTest(testScheduler) {
        repository.save(createPlan(id = "plan-1", createdAt = LocalDate.of(2026, 5, 1)))
        repository.save(createPlan(id = "plan-2", createdAt = LocalDate.of(2026, 5, 10)))

        val plans = repository.getAllPlans()
        assertEquals(2, plans.size)
        assertEquals("plan-2", plans[0].id)
        assertEquals("plan-1", plans[1].id)
    }

    /**
     * 测试删除计划后级联删除训练日。
     */
    @Test
    fun testDeletePlan() = runTest(testScheduler) {
        repository.save(
            createPlan(
                id = "plan-to-delete",
                sessions = listOf(createSession(id = "s1")),
            ),
        )

        repository.delete("plan-to-delete")

        assertNull(repository.getPlanById("plan-to-delete"))
        assertTrue(db.workoutPlanDao().getSessionsByPlanId("plan-to-delete").isEmpty())
    }

    /**
     * 测试标记与取消训练日完成状态。
     */
    @Test
    fun testMarkAndUnmarkSessionCompleted() = runTest(testScheduler) {
        repository.save(
            createPlan(
                id = "p1",
                sessions = listOf(createSession(id = "s1")),
            ),
        )
        val workoutId = db.workoutDao().insert(
            com.example.fitlog.data.local.entity.workout.WorkoutEntity(
                date = LocalDate.of(2026, 5, 20),
                sourceFileName = null,
                rawContent = null,
            ),
        )

        repository.markSessionCompleted("s1", workoutId)
        assertEquals(
            workoutId,
            db.workoutPlanDao().getSessionsByPlanId("p1")[0].completedWorkoutId,
        )

        repository.unmarkSessionCompleted("s1")
        assertNull(db.workoutPlanDao().getSessionsByPlanId("p1")[0].completedWorkoutId)
    }

    // ── 激活计划管理 ──

    /**
     * 测试激活计划的设置与清除。
     */
    @Test
    fun testSetAndClearActivePlanId() = runTest(testScheduler) {
        assertNull(repository.activePlanId.first())

        repository.setActivePlanId("plan-1")
        assertEquals("plan-1", repository.activePlanId.first())

        repository.clearActivePlanId()
        assertNull(repository.activePlanId.first())
    }

    /**
     * 测试 activePlan 将激活 ID 解析为完整计划对象。
     */
    @Test
    fun testActivePlan_resolvesToFullPlan() = runTest(testScheduler) {
        assertNull(repository.activePlan.first())

        repository.save(createPlan(id = "plan-1"))
        repository.setActivePlanId("plan-1")

        val active = repository.activePlan.first()
        assertNotNull(active)
        assertEquals("plan-1", active?.id)
        assertEquals("测试计划", active?.name)
    }

    /**
     * 测试删除当前激活计划时联动清除激活 ID。
     */
    @Test
    fun testDeleteActivePlan_clearsActiveId() = runTest(testScheduler) {
        repository.save(createPlan(id = "plan-1"))
        repository.setActivePlanId("plan-1")

        repository.delete("plan-1")

        assertNull(repository.activePlanId.first())
        assertNull(repository.activePlan.first())
    }

    /**
     * 测试删除非激活计划时保留激活 ID。
     */
    @Test
    fun testDeleteInactivePlan_keepsActiveId() = runTest(testScheduler) {
        repository.save(createPlan(id = "plan-1"))
        repository.save(createPlan(id = "plan-2"))
        repository.setActivePlanId("plan-1")

        repository.delete("plan-2")

        assertEquals("plan-1", repository.activePlanId.first())
    }

    // ── 下一个未完成训练日 ──

    /**
     * 测试 getNextIncompleteSession 跳过已完成训练日、按计划内周/日顺序取第一个。
     */
    @Test
    fun testGetNextIncompleteSession_skipsCompletedInOrder() = runTest(testScheduler) {
        repository.save(
            createPlan(
                id = "p1",
                sessions = listOf(
                    createSession(id = "w1d1", name = "第1天"),
                    createSession(id = "w1d2", name = "第2天").copy(dayNumber = 2),
                    createSession(id = "w2d1", name = "第2周第1天").copy(weekNumber = 2),
                ),
            ),
        )
        val workoutId = db.workoutDao().insert(
            com.example.fitlog.data.local.entity.workout.WorkoutEntity(
                date = LocalDate.of(2026, 5, 20),
                sourceFileName = null,
                rawContent = null,
            ),
        )

        assertEquals("w1d1", repository.getNextIncompleteSession("p1").first()?.id)

        repository.markSessionCompleted("w1d1", workoutId)
        assertEquals("w1d2", repository.getNextIncompleteSession("p1").first()?.id)

        repository.markSessionCompleted("w1d2", workoutId)
        assertEquals("w2d1", repository.getNextIncompleteSession("p1").first()?.id)

        repository.markSessionCompleted("w2d1", workoutId)
        assertNull(repository.getNextIncompleteSession("p1").first())
    }

    /**
     * 测试 getAllPlansFlow 在保存新计划后重新发射。
     */
    @Test
    fun testGetAllPlansFlow_reEmitsOnSave() = runTest(testScheduler) {
        assertTrue(repository.getAllPlansFlow().first().isEmpty())

        repository.save(createPlan(id = "plan-1"))

        val plans = repository.getAllPlansFlow().first()
        assertEquals(1, plans.size)
        assertEquals("plan-1", plans[0].id)
    }

    // ── 辅助方法 ──

    private fun createPlan(
        id: String = "test-plan",
        name: String = "测试计划",
        createdAt: LocalDate = LocalDate.of(2026, 5, 20),
        sessions: List<PlannedSession> = emptyList(),
    ) = WorkoutPlan(
        id = id,
        name = name,
        description = "计划说明",
        goal = TrainingGoal.HYPERTROPHY,
        durationWeeks = 8,
        sessionsPerWeek = 3,
        isCustom = false,
        createdAt = createdAt,
        rawPlanText = "AI 原始计划文本",
        sessions = sessions,
    )

    private fun createSession(
        id: String = "test-session",
        name: String = "训练日",
        exercises: List<PlannedExerciseItem> = emptyList(),
    ) = PlannedSession(
        id = id,
        name = name,
        description = null,
        dayNumber = 1,
        weekNumber = 1,
        targetDurationMinutes = 60,
        exercises = exercises,
        completedWorkoutId = null,
    )
}
