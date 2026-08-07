package com.example.fitlog.feature.today

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.fitlog.data.local.AppDatabase
import com.example.fitlog.data.local.entity.ExerciseEntity
import com.example.fitlog.data.local.entity.UserProfileEntity
import com.example.fitlog.data.repository.AIChatRepository
import com.example.fitlog.data.repository.AIProviderConfigRepository
import com.example.fitlog.data.repository.CoachInsightRepository
import com.example.fitlog.data.repository.ExerciseRepository
import com.example.fitlog.data.repository.UserProfileRepository
import com.example.fitlog.data.repository.WorkoutPlanRepository
import com.example.fitlog.data.repository.WorkoutRepository
import com.example.fitlog.data.seed.ExerciseSeeder
import com.example.fitlog.data.seed.SeedOrchestrator
import com.example.fitlog.data.seed.WorkoutPlanSeeder
import com.example.fitlog.model.ExerciseLog
import com.example.fitlog.model.Muscle
import com.example.fitlog.model.PlannedExerciseItem
import com.example.fitlog.model.PlannedSession
import com.example.fitlog.model.SetLog
import com.example.fitlog.model.SetType
import com.example.fitlog.model.Workout
import com.example.fitlog.model.WorkoutPlan
import com.example.fitlog.testing.FakeAIApi
import com.example.fitlog.testing.createTestPreferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

/**
 * [TodayViewModel] 的单元测试。
 *
 * 使用 Robolectric 内存 Room 数据库与测试 DataStore，手工构造四个 Repository
 * （不经 Hilt），验证数据层变化经 combine 链装配为 [TodayUiState] 的完整链路。
 *
 * 注意：ViewModel 内 `LocalDate.now()` 固定"今天"，
 * 测试夹具的 workout 日期一律用 [LocalDate.now] 构造。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class TodayViewModelTest {

    /**
     * 每个测试方法使用独立的临时目录存放 DataStore 文件。
     */
    @get:Rule
    val tmpFolder = TemporaryFolder()

    private lateinit var db: AppDatabase
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var workoutRepository: WorkoutRepository
    private lateinit var workoutPlanRepository: WorkoutPlanRepository
    private lateinit var userProfileRepository: UserProfileRepository
    private lateinit var exerciseRepository: ExerciseRepository
    private lateinit var seedOrchestrator: SeedOrchestrator
    private lateinit var fakeApi: FakeAIApi
    private lateinit var coachInsightRepository: CoachInsightRepository

    private val today: LocalDate = LocalDate.now()

    /**
     * 测试调度器：与 DataStore scope、Main dispatcher 及 `runTest` 共享同一实例。
     */
    private val testScheduler = TestCoroutineScheduler()

    /**
     * DataStore 内部协程的作用域，测试结束时在 [tearDown] 中取消。
     */
    private lateinit var dataStoreScope: TestScope

    /**
     * 设置主调度器并初始化数据库与仓库（ViewModel 由各测试按需创建，
     * 以便在创建前插入 profile 等一次性加载的数据）。
     *
     * 预置 seed 版本号 + 一条桩动作行使 [SeedOrchestrator] 短路：
     * 避免每个测试真实解析 1.33MB exercises.json 并写入预置计划。
     */
    @Before
    fun setUp() = runTest(testScheduler) {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dataStoreScope = TestScope(UnconfinedTestDispatcher(testScheduler))
        dataStore = createTestPreferencesDataStore(
            tmpFolder.newFile("today_prefs.preferences_pb"),
            dataStoreScope,
        )
        // ExerciseSeeder 短路需"版本号 ≥ SEED_VERSION 且动作表非空"；WorkoutPlanSeeder 仅需版本号
        dataStore.edit { it[intPreferencesKey("exercise_seed_version")] = 1 }
        dataStore.edit { it[intPreferencesKey("plan_seed_version")] = 2 }
        db.exerciseDao().insertAll(
            listOf(
                ExerciseEntity(
                    id = "seed-stub",
                    name = "Seed stub",
                    primaryMuscles = listOf(Muscle.CORE),
                ),
            ),
        )
        workoutRepository = WorkoutRepository(
            workoutDao = db.workoutDao(),
            exerciseLogDao = db.exerciseLogDao(),
            setLogDao = db.setLogDao(),
            db = db,
        )
        workoutPlanRepository = WorkoutPlanRepository(db.workoutPlanDao(), dataStore)
        userProfileRepository = UserProfileRepository(db.userProfileDao())
        exerciseRepository = ExerciseRepository(db.exerciseDao())
        seedOrchestrator = SeedOrchestrator(
            ExerciseSeeder(db.exerciseDao(), dataStore, context),
            WorkoutPlanSeeder(db.workoutPlanDao(), db.exerciseDao(), dataStore),
        )
        // Coach Insight AI 链路：Fake API + 真实配置仓库（默认无激活服务商 → AI 静默隐藏）
        fakeApi = FakeAIApi()
        val aiProviderConfigRepository = AIProviderConfigRepository(db.aiProviderConfigDao(), dataStore)
        coachInsightRepository = CoachInsightRepository(
            aiChatRepository = AIChatRepository(fakeApi, aiProviderConfigRepository),
            providerConfigRepo = aiProviderConfigRepository,
            dataStore = dataStore,
        )
    }

    /**
     * 取消 DataStore 作用域，重置主调度器并关闭数据库。
     */
    @After
    fun tearDown() {
        dataStoreScope.cancel()
        db.close()
        Dispatchers.resetMain()
    }

    /**
     * 创建 ViewModel 前先触发种子（短路完成），打开 uiState 的种子门。
     */
    private suspend fun createViewModel(): TodayViewModel {
        seedOrchestrator.seedIfNeeded()
        return TodayViewModel(
            workoutRepository = workoutRepository,
            workoutPlanRepository = workoutPlanRepository,
            userProfileRepository = userProfileRepository,
            exerciseRepository = exerciseRepository,
            seedOrchestrator = seedOrchestrator,
            coachInsightRepository = coachInsightRepository,
        )
    }

    /**
     * 测试初始暴露的状态为加载中（stateIn initialValue）。
     */
    @Test
    fun testInitialState_isLoading() = runTest(testScheduler) {
        val viewModel = createViewModel()
        assertTrue(viewModel.uiState.value.uiState.isLoading)
    }

    /**
     * 测试空库：今日计划为 NO_PLAN，Coach Insight 降级。
     */
    @Test
    fun testEmptyDatabase_showsNoPlanAndUnavailableInsight() = runTest(testScheduler) {
        val viewModel = createViewModel()

        val state = viewModel.uiState.first { !it.uiState.isLoading }

        assertEquals(PlanStatus.NO_PLAN, state.todayPlan.status)
        assertFalse(state.coachInsight.isAvailable)
    }

    /**
     * 测试激活计划后：今日计划变为 NOT_STARTED，标题为下一课，SPLIT 渲染项生成。
     */
    @Test
    fun testSelectPlan_showsNextSessionAsTodayPlan() = runTest(testScheduler) {
        workoutPlanRepository.save(
            plan(
                sessions = listOf(
                    session(id = "w1d1", name = "课 A · 下肢 + 推", dayNumber = 1),
                    session(id = "w1d2", name = "课 B · 拉 + 铰链", dayNumber = 2),
                ),
            ),
        )
        val viewModel = createViewModel()

        viewModel.onPlanSelected("plan-1")

        // 排除 stateIn initialValue（其 todayPlan 默认就是 NOT_STARTED 空壳）
        val state = viewModel.uiState.first {
            !it.uiState.isLoading && it.todayPlan.status == PlanStatus.NOT_STARTED
        }
        assertEquals("课 A · 下肢 + 推", state.todayPlan.title)
        assertEquals("plan-1", state.todayPlan.planId)
        // SPLIT 模式：item[0] 为本周完成度大卡，item[1] 为下一训练课次
        assertEquals("本周训练", state.weekProgress.items[0].title)
        assertTrue(
            state.weekProgress.items.any { it.title == "下一训练" && it.subtitle == "课 A · 下肢 + 推" },
        )
        assertTrue(state.coachInsight.isAvailable)
    }

    /**
     * 测试今日完成训练并关联训练日后：卡片变为 COMPLETED，本周计数为 1。
     */
    @Test
    fun testCompleteTodaySession_updatesCardAndWeekProgress() = runTest(testScheduler) {
        // 两个训练日：完成第一课后仍有下一课（nextSession 非空），
        // 命中"今日已关联完成"分支（workoutId 透传）；单课计划会走"全部完成"分支
        workoutPlanRepository.save(
            plan(
                sessions = listOf(
                    session(id = "w1d1", name = "课 A", dayNumber = 1),
                    session(id = "w1d2", name = "课 B", dayNumber = 2),
                ),
            ),
        )
        val viewModel = createViewModel()
        viewModel.onPlanSelected("plan-1")
        viewModel.uiState.first {
            !it.uiState.isLoading && it.todayPlan.status == PlanStatus.NOT_STARTED
        }

        val workoutId = workoutRepository.insert(workout(date = today))
        workoutPlanRepository.markSessionCompleted("w1d1", workoutId)

        val state = viewModel.uiState.first { it.todayPlan.status == PlanStatus.COMPLETED }
        assertEquals(1f, state.todayPlan.progress)
        assertEquals(workoutId, state.todayPlan.workoutId)
        assertEquals(1, state.weekProgress.completedWorkouts)
    }

    /**
     * 测试切换展示模式：渲染项切换为肌肉组数聚合。
     */
    @Test
    fun testDisplayModeSwitch_recomputesItems() = runTest(testScheduler) {
        db.exerciseDao().insertAll(
            listOf(
                ExerciseEntity(
                    id = "barbell-bench-press",
                    name = "Barbell bench press",
                    primaryMuscles = listOf(Muscle.CHEST),
                ),
            ),
        )
        workoutRepository.insert(
            workout(
                date = today,
                exercises = listOf(
                    ExerciseLog(
                        name = "Barbell bench press",
                        exerciseKey = "barbell-bench-press",
                        sets = listOf(SetLog(80f, 10, SetType.WORKING)),
                    ),
                ),
            ),
        )
        val viewModel = createViewModel()
        viewModel.uiState.first { !it.uiState.isLoading }

        viewModel.onDisplayModeSelected(WeekProgressDisplayMode.MUSCLE_SETS)

        val state = viewModel.uiState.first {
            it.weekProgress.displayMode == WeekProgressDisplayMode.MUSCLE_SETS
        }
        // 1 个正式组 → 重点肌群为胸部（ExerciseEntity bodyPart 默认 CHEST）
        assertTrue(
            state.weekProgress.items.any { it.title == "重点肌群" && it.subtitle == "胸部 · 1 组" },
        )
    }

    /**
     * 测试写入用户资料后：问候语带名字（一次性加载，须在创建 ViewModel 前写入）。
     *
     * 首发门控契约：profile 是无默认值冷 Flow，combine 首个非加载发射
     * 就必须已含真实资料——不允许"先匿名问候、后补名字"的两段跳变。
     */
    @Test
    fun testProfile_greetingIncludesName() = runTest(testScheduler) {
        db.userProfileDao().insert(UserProfileEntity(name = "Polaris", age = null, gender = null, height = null, weight = null, trainingGoal = null))
        val viewModel = createViewModel()

        // 断言【第一个】非加载发射即含名字（首发门控契约，而非最终一致性）
        val state = viewModel.uiState.first { !it.uiState.isLoading }
        assertEquals("Polaris", state.coachInsight.userName)
        assertTrue(state.coachInsight.greeting.endsWith("，Polaris"))
    }

    /**
     * 测试 C1 接线：VOLUME_PR 模式订阅全历史并检测新 PR。
     *
     * 夹具：上周前旧记录 80kg×10（历史最佳 e1RM≈106.7），
     * 今日同动作 90kg×10（本周 e1RM=120）→ 必出新 PR。
     *
     * 断言【首个】VOLUME_PR 发射即含 PR 项——同时锁死两件事：
     * getWorkouts() 确实被订阅（非恒 emptyList）；
     * (mode, history) 原子对无"模式先切、历史后到"的中间帧（否则首帧为"本周暂无突破"）。
     */
    @Test
    fun testVolumePrMode_subscribesHistoryAndDetectsPr() = runTest(testScheduler) {
        // exercise_logs.exerciseKey 外键指向 exercises.id，须先灌动作目录
        db.exerciseDao().insertAll(
            listOf(
                ExerciseEntity(
                    id = "barbell-bench-press",
                    name = "Barbell bench press",
                    primaryMuscles = listOf(Muscle.CHEST),
                ),
            ),
        )
        val benchLog = { weight: Float ->
            ExerciseLog(
                name = "Barbell bench press",
                exerciseKey = "barbell-bench-press",
                sets = listOf(SetLog(weight, 10, SetType.WORKING)),
            )
        }
        // 历史最佳（weekStart 之前，不进本周统计）
        workoutRepository.insert(workout(date = today.minusDays(14), exercises = listOf(benchLog(80f))))
        // 本周突破
        workoutRepository.insert(workout(date = today, exercises = listOf(benchLog(90f))))
        val viewModel = createViewModel()
        viewModel.uiState.first { !it.uiState.isLoading }

        viewModel.onDisplayModeSelected(WeekProgressDisplayMode.VOLUME_PR)

        val state = viewModel.uiState.first {
            it.weekProgress.displayMode == WeekProgressDisplayMode.VOLUME_PR
        }
        // PR 卡展示该动作本周实际最佳正式组
        assertTrue(
            state.weekProgress.items.any { it.title == "PR" && it.subtitle.contains("90kg×10") },
        )
    }

    /**
     * 测试上周区间接线：VOLUME_PR 大卡副标题环比上周容量。
     *
     * 夹具：7 天前（恒落在上周区间 [weekStart-7, weekStart-1]）卧推 80kg×10（800kg），
     * 今日 100kg×10（1000kg）→ 环比 +25%，锁死 prevWeekWorkouts 确实被订阅。
     */
    @Test
    fun testVolumePr_comparesWithPrevWeek() = runTest(testScheduler) {
        db.exerciseDao().insertAll(
            listOf(
                ExerciseEntity(
                    id = "barbell-bench-press",
                    name = "Barbell bench press",
                    primaryMuscles = listOf(Muscle.CHEST),
                ),
            ),
        )
        val benchLog = { weight: Float ->
            ExerciseLog(
                name = "Barbell bench press",
                exerciseKey = "barbell-bench-press",
                sets = listOf(SetLog(weight, 10, SetType.WORKING)),
            )
        }
        workoutRepository.insert(workout(date = today.minusDays(7), exercises = listOf(benchLog(80f))))
        workoutRepository.insert(workout(date = today, exercises = listOf(benchLog(100f))))
        val viewModel = createViewModel()
        viewModel.uiState.first { !it.uiState.isLoading }

        viewModel.onDisplayModeSelected(WeekProgressDisplayMode.VOLUME_PR)

        val state = viewModel.uiState.first {
            it.weekProgress.displayMode == WeekProgressDisplayMode.VOLUME_PR
        }
        assertEquals("训练容量", state.weekProgress.items[0].title)
        assertTrue(state.weekProgress.items[0].subtitle.startsWith("较上周"))
    }

    // ── 辅助方法 ──

    private fun plan(sessions: List<PlannedSession>) = WorkoutPlan(
        id = "plan-1",
        name = "测试计划",
        description = null,
        goal = null,
        durationWeeks = 4,
        sessionsPerWeek = 3,
        isCustom = false,
        createdAt = today,
        rawPlanText = null,
        sessions = sessions,
    )

    private fun session(id: String, name: String, dayNumber: Int) = PlannedSession(
        id = id,
        name = name,
        description = null,
        dayNumber = dayNumber,
        weekNumber = 1,
        targetDurationMinutes = 60,
        exercises = listOf(
            PlannedExerciseItem(exerciseKey = "barbell-bench-press", targetSets = 4, order = 0),
        ),
    )

    private fun workout(date: LocalDate, exercises: List<ExerciseLog> = emptyList()) = Workout(
        id = 0L,
        userId = 0L,
        date = date,
        exercises = exercises,
        feelings = null,
    )
}
