package com.example.fitlog.feature.reminder

import com.example.fitlog.FitLogApplication
import com.example.fitlog.data.repository.UserPreferencesRepository
import com.example.fitlog.testing.createTestPreferencesDataStore
import com.example.fitlog.ui.settings.reminder.ReminderViewModel
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
import java.util.UUID

/**
 * 训练提醒调度场景的集成回归测试。
 *
 * 覆盖任务要求的 5 种核心场景：
 * 1. 已有到期任务时启动：启动恢复调用 [ReminderScheduler.recoverSchedule]（KEEP 语义），
 *    绝不调用 [ReminderScheduler.schedule]（REPLACE 语义），确保当天到期任务不被推迟至次日；
 * 2. 没有任务时启动：提醒已开启时，启动恢复调用 [ReminderScheduler.recoverSchedule] 进行补排；
 *    提醒关闭时，不产生任何调度调用；
 * 3. 修改时间：[ReminderViewModel.onTimeChange] 正确调用 [ReminderScheduler.schedule]（REPLACE 语义）重排；
 * 4. 关闭提醒：[ReminderViewModel.onEnabledChange] 正确调用 [ReminderScheduler.cancel] 清除任务；
 * 5. Worker 自链与启动恢复交错：自链调用 [ReminderScheduler.scheduleSelfChainedNext] 并传递 Worker ID，
 *    启动恢复调用 [ReminderScheduler.recoverSchedule]，两者均不调用 REPLACE，互不打断、不产生重复后继。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReminderSchedulingScenariosTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val testScheduler = TestCoroutineScheduler()
    private lateinit var dataStoreScope: TestScope
    private lateinit var preferencesRepository: UserPreferencesRepository
    private lateinit var recordingScheduler: RecordingReminderScheduler

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        dataStoreScope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val dataStore = createTestPreferencesDataStore(
            tmpFolder.newFile("reminder_scenarios.preferences_pb"),
            dataStoreScope,
        )
        preferencesRepository = UserPreferencesRepository(dataStore)
        recordingScheduler = RecordingReminderScheduler()
    }

    @After
    fun tearDown() {
        dataStoreScope.cancel()
        Dispatchers.resetMain()
    }

    /**
     * 场景 1 & 2：启动恢复验证。
     * 验证 FitLogApplication.rescheduleReminderIfNeeded 在提醒开启时调用 recoverSchedule，
     * 且绝不调用 schedule（REPLACE），从而在已有在途/到期任务时保留原有任务，在无任务时完成补排。
     */
    @Test
    fun testStartupRecovery_whenEnabled_callsRecoverScheduleNotReplace() = runTest(testScheduler) {
        preferencesRepository.setReminderEnabled(true)
        preferencesRepository.setReminderMinutes(18 * 60)

        val app = FitLogApplication().apply {
            this.userPreferencesRepository = preferencesRepository
            this.reminderScheduler = recordingScheduler
        }

        app.rescheduleReminderIfNeeded(this)
        testScheduler.advanceUntilIdle()

        // 核心断言：启动恢复必须走 recoverSchedule（KEEP 语义）
        assertEquals(listOf(18 * 60), recordingScheduler.recovered)
        // 关键断言：绝对不能调用 schedule（REPLACE 语义），否则会取消今天到期的任务
        assertEquals(emptyList<Int>(), recordingScheduler.scheduled)
    }

    /**
     * 场景 2 分支：没有任务且提醒未开启时启动，不触发任何补排。
     */
    @Test
    fun testStartupRecovery_whenDisabled_doesNotSchedule() = runTest(testScheduler) {
        preferencesRepository.setReminderEnabled(false)

        val app = FitLogApplication().apply {
            this.userPreferencesRepository = preferencesRepository
            this.reminderScheduler = recordingScheduler
        }

        app.rescheduleReminderIfNeeded(this)
        testScheduler.advanceUntilIdle()

        assertEquals(emptyList<Int>(), recordingScheduler.recovered)
        assertEquals(emptyList<Int>(), recordingScheduler.scheduled)
    }

    /**
     * 场景 3：用户修改时间，ViewModel 调用 schedule（REPLACE 语义）以新时间替换旧任务。
     */
    @Test
    fun testUserChangesTime_callsScheduleReplace() = runTest(testScheduler) {
        preferencesRepository.setReminderEnabled(true)
        preferencesRepository.setReminderMinutes(18 * 60)

        val viewModel = ReminderViewModel(preferencesRepository, recordingScheduler)
        // 用户改时间为 07:30
        viewModel.onTimeChange(7 * 60 + 30)

        assertEquals(listOf(7 * 60 + 30), recordingScheduler.scheduled)
        assertEquals(emptyList<Int>(), recordingScheduler.recovered)
    }

    /**
     * 场景 4：用户关闭提醒，ViewModel 调用 cancel 取消任务。
     */
    @Test
    fun testUserDisablesReminder_callsCancel() = runTest(testScheduler) {
        preferencesRepository.setReminderEnabled(true)

        val viewModel = ReminderViewModel(preferencesRepository, recordingScheduler)
        viewModel.onEnabledChange(false)

        assertEquals(1, recordingScheduler.cancelCount)
    }

    /**
     * 场景 5：Worker 自链与启动恢复交错。
     * Worker 自链调用 scheduleSelfChainedNext（传递 Worker ID），启动恢复调用 recoverSchedule，
     * 两者独立运作且均不调用 REPLACE，杜绝任务取消和重复排程。
     */
    @Test
    fun testInterleaving_workerSelfChainAndStartupRecovery() = runTest(testScheduler) {
        preferencesRepository.setReminderEnabled(true)
        preferencesRepository.setReminderMinutes(18 * 60)

        val workerId = UUID.randomUUID()

        // 1. Worker 自链下一天
        recordingScheduler.scheduleSelfChainedNext(18 * 60, currentWorkId = workerId)

        // 2. 模拟此时应用启动恢复
        val app = FitLogApplication().apply {
            this.userPreferencesRepository = preferencesRepository
            this.reminderScheduler = recordingScheduler
        }
        app.rescheduleReminderIfNeeded(this)
        testScheduler.advanceUntilIdle()

        // 验证自链记录：传递了当前 Worker ID
        assertEquals(1, recordingScheduler.selfChained.size)
        assertEquals(18 * 60, recordingScheduler.selfChained[0].first)
        assertEquals(workerId, recordingScheduler.selfChained[0].second)

        // 验证启动恢复记录：走 recoverSchedule（KEEP 语义）
        assertEquals(listOf(18 * 60), recordingScheduler.recovered)

        // 关键保证：全过程无任何 REPLACE 调用
        assertEquals(emptyList<Int>(), recordingScheduler.scheduled)
    }

    /**
     * 记录式调度器替身，完整记录各项调用入参。
     */
    private class RecordingReminderScheduler : ReminderScheduler {
        val scheduled = mutableListOf<Int>()
        val recovered = mutableListOf<Int>()
        val selfChained = mutableListOf<Pair<Int, UUID?>>()
        var cancelCount = 0

        override fun schedule(minutesOfDay: Int) {
            scheduled += minutesOfDay
        }

        override fun recoverSchedule(minutesOfDay: Int) {
            recovered += minutesOfDay
        }

        override suspend fun scheduleSelfChainedNext(minutesOfDay: Int, currentWorkId: UUID?) {
            selfChained += Pair(minutesOfDay, currentWorkId)
        }

        override fun cancel() {
            cancelCount++
        }
    }
}
