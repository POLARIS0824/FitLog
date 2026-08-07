package com.example.fitlog.ui.settings.appearance

import com.example.fitlog.data.repository.ThemeMode
import com.example.fitlog.data.repository.UserPreferencesRepository
import com.example.fitlog.testing.createTestPreferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
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

/**
 * [AppearanceViewModel] 的单元测试。
 *
 * 使用临时文件 DataStore + 真实 [UserPreferencesRepository]，
 * 验证 UI 状态来自偏好仓库，且修改事件会写盘并驱动状态刷新。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppearanceViewModelTest {

    /**
     * 每个测试方法使用独立的临时目录存放 DataStore 文件。
     */
    @get:Rule
    val tmpFolder = TemporaryFolder()

    private lateinit var preferencesRepository: UserPreferencesRepository
    private lateinit var viewModel: AppearanceViewModel

    /**
     * 测试调度器：与 DataStore scope、Main dispatcher 及 `runTest` 共享同一实例。
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
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        dataStoreScope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val dataStore = createTestPreferencesDataStore(
            tmpFolder.newFile("appearance_prefs.preferences_pb"),
            dataStoreScope,
        )
        preferencesRepository = UserPreferencesRepository(dataStore)
        viewModel = AppearanceViewModel(preferencesRepository)
    }

    /**
     * 取消 DataStore 作用域并重置主调度器。
     */
    @After
    fun tearDown() {
        dataStoreScope.cancel()
        Dispatchers.resetMain()
    }

    /**
     * 测试初始状态为默认值：跟随系统 + 动态取色开启。
     */
    @Test
    fun testInitialState_defaults() = runTest(testScheduler) {
        val state = viewModel.uiState.first()
        assertEquals(ThemeMode.SYSTEM, state.themeMode)
        assertEquals(true, state.dynamicColor)
    }

    /**
     * 测试切换主题模式：状态从默认 SYSTEM 流转为 DARK。
     */
    @Test
    fun testOnThemeModeChange_updatesState() = runTest(testScheduler) {
        viewModel.onThemeModeChange(ThemeMode.DARK)

        val state = viewModel.uiState.first { it.themeMode == ThemeMode.DARK }
        assertEquals(true, state.dynamicColor)
    }

    /**
     * 测试关闭动态取色：状态流转为 false。
     */
    @Test
    fun testOnDynamicColorChange_updatesState() = runTest(testScheduler) {
        viewModel.onDynamicColorChange(false)

        val state = viewModel.uiState.first { !it.dynamicColor }
        assertEquals(ThemeMode.SYSTEM, state.themeMode)
    }
}
