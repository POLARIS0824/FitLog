package com.example.fitlog.ui.settings.reminder

import com.example.fitlog.data.repository.UserPreferencesRepository
import com.example.fitlog.feature.reminder.ReminderScheduler
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
 * [ReminderViewModel] 的单元测试。
 *
 * 使用临时文件 DataStore + 真实 [UserPreferencesRepository] + 记录式调度器替身，
 * 验证提醒开关与提醒时间的状态暴露、修改事件及调度联动。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReminderViewModelTest {

    /**
     * 每个测试方法使用独立的临时目录存放 DataStore 文件。
     */
    @get:Rule
    val tmpFolder = TemporaryFolder()

    private lateinit var preferencesRepository: UserPreferencesRepository
    private lateinit var scheduler: RecordingScheduler
    private lateinit var viewModel: ReminderViewModel

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
            tmpFolder.newFile("reminder_prefs.preferences_pb"),
            dataStoreScope,
        )
        preferencesRepository = UserPreferencesRepository(dataStore)
        scheduler = RecordingScheduler()
        viewModel = ReminderViewModel(preferencesRepository, scheduler)
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
     * 测试初始状态为默认值：提醒关闭、时间 18:00（1080 分钟）。
     */
    @Test
    fun testInitialState_defaults() = runTest(testScheduler) {
        val state = viewModel.uiState.first()
        assertEquals(false, state.enabled)
        assertEquals(18 * 60, state.minutes)
    }

    /**
     * 测试打开提醒开关：状态从 false 流转为 true，且按默认时间调度。
     */
    @Test
    fun testOnEnabledChange_updatesStateAndSchedules() = runTest(testScheduler) {
        viewModel.onEnabledChange(true)

        val state = viewModel.uiState.first { it.enabled }
        assertEquals(18 * 60, state.minutes)
        assertEquals(listOf(18 * 60), scheduler.scheduled)
        assertEquals(0, scheduler.cancelCount)
    }

    /**
     * 测试修改提醒时间：状态从默认 18:00 流转为 07:30；开关关闭时只写偏好不调度。
     */
    @Test
    fun testOnTimeChange_updatesStateWithoutSchedulingWhenDisabled() = runTest(testScheduler) {
        viewModel.onTimeChange(7 * 60 + 30)

        val state = viewModel.uiState.first { it.minutes == 7 * 60 + 30 }
        assertEquals(false, state.enabled)
        assertEquals(0, scheduler.scheduled.size)
    }

    /**
     * 测试开关为开时改时间：重排到新时刻；随后关开关：取消调度。
     */
    @Test
    fun testScheduling_lifecycle() = runTest(testScheduler) {
        viewModel.onEnabledChange(true)
        viewModel.onTimeChange(7 * 60)

        val state = viewModel.uiState.first { it.minutes == 7 * 60 }
        assertEquals(true, state.enabled)
        assertEquals(listOf(18 * 60, 7 * 60), scheduler.scheduled)

        viewModel.onEnabledChange(false)
        viewModel.uiState.first { !it.enabled }
        assertEquals(1, scheduler.cancelCount)
    }

    /** 记录式调度器替身：记录 schedule 参数与 cancel 次数。 */
    private class RecordingScheduler : ReminderScheduler {
        val scheduled = mutableListOf<Int>()
        var cancelCount = 0

        override fun schedule(minutesOfDay: Int) {
            scheduled += minutesOfDay
        }

        override fun cancel() {
            cancelCount++
        }
    }
}
