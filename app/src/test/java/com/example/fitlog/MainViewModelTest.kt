package com.example.fitlog

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.fitlog.data.local.AppDatabase
import com.example.fitlog.data.repository.ThemeMode
import com.example.fitlog.data.repository.UserPreferencesRepository
import com.example.fitlog.data.seed.ExerciseSeeder
import com.example.fitlog.testing.createTestPreferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * [MainViewModel] 的单元测试。
 *
 * 验证全局主题偏好（ThemeMode, dynamicColor）以 Pair 形式暴露，
 * 且偏好修改会驱动 Pair 更新（供 FitLogTheme 响应式应用主题）。
 *
 * 使用 Robolectric 提供 Context 以构造 [ExerciseSeeder]；
 * 预置 seed 版本号使种子导入短路返回，避免测试触碰资源与数据库写入。
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
     * 设置主调度器并初始化仓库与 ViewModel。
     */
    @Before
    fun setUp() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dataStore = createTestPreferencesDataStore(tmpFolder.newFile("main_prefs.preferences_pb"))
        // 预置 seed 版本号，使 ExerciseSeeder.seedIfNeeded() 立即返回
        dataStore.edit { it[intPreferencesKey("exercise_seed_version")] = 1 }
        preferencesRepository = UserPreferencesRepository(dataStore)
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val seeder = ExerciseSeeder(db.exerciseDao(), dataStore, context)
        viewModel = MainViewModel(preferencesRepository, seeder)
    }

    /**
     * 重置主调度器并关闭数据库。
     */
    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    /**
     * 测试初始暴露的主题偏好为默认值：跟随系统 + 动态取色开启。
     */
    @Test
    fun testInitialAppearance_defaults() = runTest {
        val (mode, dynamicColor) = viewModel.appearance.first()
        assertEquals(ThemeMode.SYSTEM, mode)
        assertEquals(true, dynamicColor)
    }

    /**
     * 测试偏好修改后，暴露的 Pair 随之更新。
     */
    @Test
    fun testAppearance_updatesOnPreferenceChange() = runTest {
        preferencesRepository.setThemeMode(ThemeMode.LIGHT)

        val state = viewModel.appearance.first { it.first == ThemeMode.LIGHT }
        assertEquals(ThemeMode.LIGHT to true, state)
    }
}
