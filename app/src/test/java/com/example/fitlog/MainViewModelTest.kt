package com.example.fitlog

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.fitlog.data.local.AppDatabase
import com.example.fitlog.data.local.entity.ExerciseEntity
import com.example.fitlog.data.repository.ThemeMode
import com.example.fitlog.data.repository.UserPreferencesRepository
import com.example.fitlog.data.seed.ExerciseSeeder
import com.example.fitlog.data.seed.SeedOrchestrator
import com.example.fitlog.data.seed.WorkoutPlanSeeder
import com.example.fitlog.model.Muscle
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

/**
 * [MainViewModel] 的单元测试。
 *
 * 验证全局主题偏好（ThemeMode, dynamicColor）以 Pair 形式暴露，
 * 且偏好修改会驱动 Pair 更新（供 FitLogTheme 响应式应用主题）。
 *
 * 使用 Robolectric 提供 Context 以构造 [ExerciseSeeder]；
 * 预置 seed 版本号 + 一条动作记录使种子导入短路返回，
 * 避免每个测试真实解析 1.33MB exercises.json 并重灌全库。
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    /**
     * 每个测试方法使用独立的临时目录存放 DataStore 文件。
     */
    @get:Rule
    val tmpFolder = TemporaryFolder()

    private lateinit var db: AppDatabase
    private lateinit var preferencesRepository: UserPreferencesRepository
    private lateinit var viewModel: MainViewModel

    /**
     * 测试调度器：与 DataStore scope、Main dispatcher、`runTest` 共享同一实例，
     * 保证 DataStore 写协程与测试协程在同一虚拟时间线上推进。
     */
    private val testScheduler = TestCoroutineScheduler()

    /**
     * DataStore 内部协程的作用域，测试结束时在 [tearDown] 中取消。
     */
    private lateinit var dataStoreScope: TestScope

    /**
     * 设置主调度器并初始化仓库与 ViewModel。
     */
    @Before
    fun setUp() = runTest(testScheduler) {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val context = ApplicationProvider.getApplicationContext<Context>()
        dataStoreScope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val dataStore = createTestPreferencesDataStore(
            tmpFolder.newFile("main_prefs.preferences_pb"),
            dataStoreScope,
        )
        // 预置 seed 版本号：ExerciseSeeder 短路还需动作表非空（下方插入一条）；
        // WorkoutPlanSeeder 短路需版本号 ≥ SEED_VERSION(2) 且计划表非空——
        // 计划表为空时会走重灌路径，但因动作缺失全部计划被跳过，行为不变。
        dataStore.edit { it[intPreferencesKey("exercise_seed_version")] = 1 }
        dataStore.edit { it[intPreferencesKey("plan_seed_version")] = 2 }
        preferencesRepository = UserPreferencesRepository(dataStore)
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        db.exerciseDao().insertAll(
            listOf(
                ExerciseEntity(
                    id = "barbell-bench-press",
                    name = "Barbell bench press",
                    primaryMuscles = listOf(Muscle.CHEST),
                ),
            ),
        )
        val exerciseSeeder = ExerciseSeeder(db.exerciseDao(), dataStore, context)
        val planSeeder = WorkoutPlanSeeder(db.workoutPlanDao(), db.exerciseDao(), dataStore)
        viewModel = MainViewModel(preferencesRepository, SeedOrchestrator(exerciseSeeder, planSeeder))
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
     * 测试初始暴露的主题偏好为默认值：跟随系统 + 动态取色开启。
     */
    @Test
    fun testInitialAppearance_defaults() = runTest(testScheduler) {
        val (mode, dynamicColor) = viewModel.appearance.first()
        assertEquals(ThemeMode.SYSTEM, mode)
        assertEquals(true, dynamicColor)
    }

    /**
     * 测试偏好修改后，暴露的 Pair 随之更新。
     */
    @Test
    fun testAppearance_updatesOnPreferenceChange() = runTest(testScheduler) {
        preferencesRepository.setThemeMode(ThemeMode.LIGHT)

        val state = viewModel.appearance.first { it.first == ThemeMode.LIGHT }
        assertEquals(ThemeMode.LIGHT to true, state)
    }

    /**
     * 测试首帧放行条件：外观偏好加载后 isReady 置位。
     * （种子不再阻塞 Splash——isReady 只等外观，见 MainViewModel KDoc。）
     */
    @Test
    fun testIsReady_becomesTrueAfterAppearance() = runTest(testScheduler) {
        assertTrue(viewModel.isReady.first { it })
    }

    /**
     * 回归测试：上游偏好流抛异常时不击穿 appearance 链（启动崩溃路径）。
     *
     * MainViewModel.appearance 已加 .catch { }——异常被吞掉后 stateIn 停留在
     * 默认值，isReady 仍能经 onCompletion 放行。若无 catch，stateIn(Eagerly)
     * 的共享协程将以未捕获协程异常使测试进程崩溃。
     */
    @Test
    fun testAppearance_upstreamException_doesNotCrashAndReleasesSplash() = runTest(testScheduler) {
        val crashingSource = object : com.example.fitlog.data.repository.AppearanceSource {
            override val themeMode =
                kotlinx.coroutines.flow.flow<ThemeMode> { throw IOException("DataStore 损坏") }
            override val dynamicColor =
                kotlinx.coroutines.flow.flow<Boolean> { throw IOException("DataStore 损坏") }
        }
        val vm2 = MainViewModel(
            crashingSource,
            SeedOrchestrator(
                ExerciseSeeder(db.exerciseDao(), createTestPreferencesDataStore(tmpFolder.newFile("seed2.preferences_pb"), dataStoreScope), ApplicationProvider.getApplicationContext()),
                WorkoutPlanSeeder(
                    db.workoutPlanDao(),
                    db.exerciseDao(),
                    createTestPreferencesDataStore(tmpFolder.newFile("seed3.preferences_pb"), dataStoreScope),
                ),
            ),
        )

        // 异常被 catch 吞掉：不崩溃、isReady 放行、appearance 停留默认值
        assertTrue(vm2.isReady.first { it })
        assertEquals(ThemeMode.SYSTEM to true, vm2.appearance.value)
    }
}
