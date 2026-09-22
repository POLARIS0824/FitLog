package com.example.fitlog.feature.agent.tools

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.fitlog.data.local.AppDatabase
import com.example.fitlog.data.local.entity.ExerciseEntity
import com.example.fitlog.data.repository.BodyMetricRepository
import com.example.fitlog.data.repository.ExerciseRepository
import com.example.fitlog.data.repository.UserProfileRepository
import com.example.fitlog.data.repository.WorkoutPlanRepository
import com.example.fitlog.data.repository.WorkoutRepository
import com.example.fitlog.testing.createTestPreferencesDataStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * [FitnessTools.createPlan] 的单元测试：AI 生成计划的落地出口
 * （JSON 解析容错 / exerciseKey 存在性校验 / 落库与激活联动 / 数值钳制）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CreatePlanToolTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private lateinit var db: AppDatabase
    private lateinit var tools: FitnessTools
    private lateinit var planRepository: WorkoutPlanRepository
    private lateinit var dataStoreScope: TestScope

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dataStoreScope = TestScope(UnconfinedTestDispatcher())
        val dataStore = createTestPreferencesDataStore(
            tmpFolder.newFile("create_plan_prefs.preferences_pb"),
            dataStoreScope,
        )
        planRepository = WorkoutPlanRepository(db.workoutPlanDao(), dataStore)
        tools = FitnessTools(
            workoutRepository = WorkoutRepository(
                workoutDao = db.workoutDao(),
                exerciseLogDao = db.exerciseLogDao(),
                setLogDao = db.setLogDao(),
                workoutPlanDao = db.workoutPlanDao(),
                db = db,
            ),
            workoutPlanRepository = planRepository,
            userProfileRepository = UserProfileRepository(db.userProfileDao()),
            exerciseRepository = ExerciseRepository(db.exerciseDao()),
            bodyMetricRepository = BodyMetricRepository(db.bodyMetricDao()),
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    /** 训练日 JSON 解析失败必须以 success=false 回给模型重试，不得抛异常。 */
    @Test
    fun `malformed sessions json fails gracefully`() = runTest(dataStoreScope.testScheduler) {
        val result = tools.createPlan(name = "测试计划", sessionsJson = "not-a-json")
        assertTrue(!result.success)
        assertTrue(result.message.contains("解析失败"))
    }

    /** 训练日列表为空时拒绝落库。 */
    @Test
    fun `empty session list is rejected`() = runTest(dataStoreScope.testScheduler) {
        val result = tools.createPlan(name = "测试计划", sessionsJson = "[]")
        assertTrue(!result.success)
        assertTrue(result.message.contains("至少"))
    }

    /** exerciseKey 不在动作库时拦截（外键会整体回滚），并回报未知 key。 */
    @Test
    fun `unknown exercise keys are rejected with hint`() = runTest(dataStoreScope.testScheduler) {
        val result = tools.createPlan(
            name = "测试计划",
            sessionsJson = """[{"weekNumber":1,"dayNumber":1,"name":"Day 1","exercises":[
                {"exerciseKey":"made-up-exercise","targetSets":3}]}]""",
        )
        assertTrue(!result.success)
        assertTrue(result.message.contains("made-up-exercise"))
    }

    /** 合法参数：计划落库（isCustom=true）、缺省周数推导、activate=true 时联动激活。 */
    @Test
    fun `valid plan is saved and activated`() = runTest(dataStoreScope.testScheduler) {
        db.exerciseDao().insertAll(
            listOf(ExerciseEntity(id = "barbell-bench-press", name = "Barbell bench press")),
        )
        val result = tools.createPlan(
            name = "4 周上肢增肌",
            sessionsJson = """[{"weekNumber":2,"dayNumber":1,"name":"Day 1 推","exercises":[
                {"exerciseKey":"barbell-bench-press","targetSets":4,"targetRepsMin":8,"targetRepsMax":10}]}]""",
            activate = true,
        )
        assertTrue(result.success)

        val planId = planRepository.getAllPlans().first { it.isCustom }.id
        val saved = planRepository.getPlanById(planId)
        assertNotNull(saved)
        assertEquals("4 周上肢增肌", saved!!.name)
        assertTrue(saved.isCustom)
        // durationWeeks 缺省按训练日最大周数推导
        assertEquals(2, saved.durationWeeks)
        assertEquals(1, saved.sessionsPerWeek)
        assertEquals("barbell-bench-press", saved.sessions.single().exercises.single().exerciseKey)
        assertEquals(4, saved.sessions.single().exercises.single().targetSets)
        // activate=true → 联动激活
        assertEquals(planId, planRepository.activePlanId.first())
    }

    /** 目标组数钳制：模型幻觉出的天文数字不得原样落库。 */
    @Test
    fun `target sets are clamped to sane bounds`() = runTest(dataStoreScope.testScheduler) {
        db.exerciseDao().insertAll(
            listOf(ExerciseEntity(id = "barbell-bench-press", name = "Barbell bench press")),
        )
        val result = tools.createPlan(
            name = "钳制测试",
            sessionsJson = """[{"weekNumber":1,"dayNumber":1,"name":"Day 1","exercises":[
                {"exerciseKey":"barbell-bench-press","targetSets":99}]}]""",
        )
        assertTrue(result.success)
        val plan = planRepository.getAllPlans().first { it.isCustom }
        assertEquals(12, plan.sessions.single().exercises.single().targetSets)
    }

    /** 验证 4 周 × 每周 3 练场景下，sessionsPerWeek 正确推导为 3（而非总周数 4）。 */
    @Test
    fun `sessionsPerWeek correctly derives 3 for 4 weeks with 3 sessions per week`() = runTest(dataStoreScope.testScheduler) {
        db.exerciseDao().insertAll(
            listOf(ExerciseEntity(id = "barbell-bench-press", name = "Barbell bench press")),
        )
        val sessions = (1..4).flatMap { week ->
            (1..3).map { day ->
                """{"weekNumber":$week,"dayNumber":$day,"name":"W${week}D$day","exercises":[{"exerciseKey":"barbell-bench-press","targetSets":3}]}"""
            }
        }.joinToString(",", prefix = "[", postfix = "]")

        val result = tools.createPlan(name = "4周3练", sessionsJson = sessions)
        assertTrue(result.success)

        val plan = planRepository.getAllPlans().first { it.name == "4周3练" }
        assertEquals(4, plan.durationWeeks)
        assertEquals(3, plan.sessionsPerWeek)
    }

    /** 验证各周训练次数不等时，sessionsPerWeek 取众数。 */
    @Test
    fun `sessionsPerWeek derives mode for unequal weeks`() = runTest(dataStoreScope.testScheduler) {
        db.exerciseDao().insertAll(
            listOf(ExerciseEntity(id = "barbell-bench-press", name = "Barbell bench press")),
        )
        // Week 1: 3 sessions; Week 2: 4 sessions; Week 3: 4 sessions -> 众数为 4
        val sessions = """[
            {"weekNumber":1,"dayNumber":1,"exercises":[{"exerciseKey":"barbell-bench-press","targetSets":3}]},
            {"weekNumber":1,"dayNumber":2,"exercises":[{"exerciseKey":"barbell-bench-press","targetSets":3}]},
            {"weekNumber":1,"dayNumber":3,"exercises":[{"exerciseKey":"barbell-bench-press","targetSets":3}]},
            {"weekNumber":2,"dayNumber":1,"exercises":[{"exerciseKey":"barbell-bench-press","targetSets":3}]},
            {"weekNumber":2,"dayNumber":2,"exercises":[{"exerciseKey":"barbell-bench-press","targetSets":3}]},
            {"weekNumber":2,"dayNumber":3,"exercises":[{"exerciseKey":"barbell-bench-press","targetSets":3}]},
            {"weekNumber":2,"dayNumber":4,"exercises":[{"exerciseKey":"barbell-bench-press","targetSets":3}]},
            {"weekNumber":3,"dayNumber":1,"exercises":[{"exerciseKey":"barbell-bench-press","targetSets":3}]},
            {"weekNumber":3,"dayNumber":2,"exercises":[{"exerciseKey":"barbell-bench-press","targetSets":3}]},
            {"weekNumber":3,"dayNumber":3,"exercises":[{"exerciseKey":"barbell-bench-press","targetSets":3}]},
            {"weekNumber":3,"dayNumber":4,"exercises":[{"exerciseKey":"barbell-bench-press","targetSets":3}]}
        ]"""

        val result = tools.createPlan(name = "众数测试", sessionsJson = sessions)
        assertTrue(result.success)

        val plan = planRepository.getAllPlans().first { it.name == "众数测试" }
        assertEquals(4, plan.sessionsPerWeek)
    }

    /** 验证各周训练次数平局时，sessionsPerWeek 取较大值。 */
    @Test
    fun `sessionsPerWeek tie-break picks larger value`() = runTest(dataStoreScope.testScheduler) {
        db.exerciseDao().insertAll(
            listOf(ExerciseEntity(id = "barbell-bench-press", name = "Barbell bench press")),
        )
        // Week 1: 3 sessions; Week 2: 4 sessions -> 平局取大：4
        val sessions = """[
            {"weekNumber":1,"dayNumber":1,"exercises":[{"exerciseKey":"barbell-bench-press","targetSets":3}]},
            {"weekNumber":1,"dayNumber":2,"exercises":[{"exerciseKey":"barbell-bench-press","targetSets":3}]},
            {"weekNumber":1,"dayNumber":3,"exercises":[{"exerciseKey":"barbell-bench-press","targetSets":3}]},
            {"weekNumber":2,"dayNumber":1,"exercises":[{"exerciseKey":"barbell-bench-press","targetSets":3}]},
            {"weekNumber":2,"dayNumber":2,"exercises":[{"exerciseKey":"barbell-bench-press","targetSets":3}]},
            {"weekNumber":2,"dayNumber":3,"exercises":[{"exerciseKey":"barbell-bench-press","targetSets":3}]},
            {"weekNumber":2,"dayNumber":4,"exercises":[{"exerciseKey":"barbell-bench-press","targetSets":3}]}
        ]"""

        val result = tools.createPlan(name = "平局取大测试", sessionsJson = sessions)
        assertTrue(result.success)

        val plan = planRepository.getAllPlans().first { it.name == "平局取大测试" }
        assertEquals(4, plan.sessionsPerWeek)
    }

    /** 验证创建计划时为每个 PlannedExerciseItem 分配独立非空 UUID。 */
    @Test
    fun `createPlan assigns UUID to planned exercises`() = runTest(dataStoreScope.testScheduler) {
        db.exerciseDao().insertAll(
            listOf(ExerciseEntity(id = "barbell-bench-press", name = "Barbell bench press")),
        )
        val result = tools.createPlan(
            name = "UUID测试",
            sessionsJson = """[{"weekNumber":1,"dayNumber":1,"exercises":[{"exerciseKey":"barbell-bench-press","targetSets":3}]}]""",
        )
        assertTrue(result.success)

        val plan = planRepository.getAllPlans().first { it.name == "UUID测试" }
        val exercise = plan.sessions.first().exercises.first()
        assertNotNull(exercise.id)
        assertTrue(exercise.id!!.isNotBlank())
    }

    /** 验证显式指定 sessionsPerWeek 时，显式值优先于推导值。 */
    @Test
    fun `explicit sessionsPerWeek takes precedence over derived frequency`() = runTest(dataStoreScope.testScheduler) {
        db.exerciseDao().insertAll(
            listOf(ExerciseEntity(id = "barbell-bench-press", name = "Barbell bench press")),
        )
        val sessions = (1..4).flatMap { week ->
            (1..3).map { day ->
                """{"weekNumber":$week,"dayNumber":$day,"name":"W${week}D$day","exercises":[{"exerciseKey":"barbell-bench-press","targetSets":3}]}"""
            }
        }.joinToString(",", prefix = "[", postfix = "]")

        val result = tools.createPlan(
            name = "显式频次优先",
            sessionsJson = sessions,
            sessionsPerWeek = 5,
        )
        assertTrue(result.success)

        val plan = planRepository.getAllPlans().first { it.name == "显式频次优先" }
        assertEquals(5, plan.sessionsPerWeek)
    }

    /** 验证课次名称缺省或空白时，自动补全为默认名称。 */
    @Test
    fun `missing or blank session name defaults to fallback name`() = runTest(dataStoreScope.testScheduler) {
        db.exerciseDao().insertAll(
            listOf(ExerciseEntity(id = "barbell-bench-press", name = "Barbell bench press")),
        )
        val sessions = """[
            {"weekNumber":1,"dayNumber":1,"exercises":[{"exerciseKey":"barbell-bench-press","targetSets":3}]},
            {"weekNumber":1,"dayNumber":2,"name":"   ","exercises":[{"exerciseKey":"barbell-bench-press","targetSets":3}]}
        ]"""

        val result = tools.createPlan(name = "缺省名称测试", sessionsJson = sessions)
        assertTrue(result.success)

        val plan = planRepository.getAllPlans().first { it.name == "缺省名称测试" }
        assertEquals("Day 1", plan.sessions[0].name)
        assertEquals("Day 2", plan.sessions[1].name)
    }
}
