package com.example.fitlog.feature.reminder

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.WorkInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.UUID

/**
 * [WorkManagerReminderScheduler] 的单元测试。
 *
 * 专门验证自链幂等守卫（[WorkManagerReminderScheduler.hasPendingSuccessor]）：
 * - 排除自身：当前 Worker 处于 RUNNING 状态时不误判为后继；
 * - 捕获 BLOCKED：APPEND 追加的子任务在父任务完成前为 BLOCKED，Worker 重试重跑必须识别并跳过；
 * - 捕获 ENQUEUED：父任务已完成后继转为 ENQUEUED，必须识别并跳过；
 * - 捕获 RUNNING：后继已在运行时必须识别并跳过；
 * - 忽略终端状态：已完成（SUCCEEDED / FAILED / CANCELLED）的任务不阻碍自链。
 */
@RunWith(RobolectricTestRunner::class)
class WorkManagerReminderSchedulerTest {

    private lateinit var context: Context
    private lateinit var scheduler: WorkManagerReminderScheduler

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        scheduler = WorkManagerReminderScheduler(context)
    }

    private fun createWorkInfo(id: UUID, state: WorkInfo.State): WorkInfo {
        return WorkInfo(
            id = id,
            state = state,
            outputData = Data.EMPTY,
            tags = emptySet(),
            progress = Data.EMPTY,
            runAttemptCount = 0,
            generation = 0,
            constraints = Constraints.NONE,
            initialDelayMillis = 0,
            periodicityInfo = null,
            nextScheduleTimeMillis = 0,
            stopReason = WorkInfo.STOP_REASON_NOT_STOPPED,
        )
    }

    @Test
    fun testHasPendingSuccessor_emptyList_returnsFalse() {
        val currentId = UUID.randomUUID()
        assertFalse(scheduler.hasPendingSuccessor(emptyList(), currentId))
    }

    @Test
    fun testHasPendingSuccessor_onlyCurrentWorkerRunning_returnsFalse() {
        val currentId = UUID.randomUUID()
        val infos = listOf(createWorkInfo(currentId, WorkInfo.State.RUNNING))
        // 当前 Worker 自身处于 RUNNING，不应被判为后继，保证首次自链能正常追加
        assertFalse(scheduler.hasPendingSuccessor(infos, currentId))
    }

    @Test
    fun testHasPendingSuccessor_successorIsBlocked_returnsTrue() {
        val currentId = UUID.randomUUID()
        val successorId = UUID.randomUUID()
        val infos = listOf(
            createWorkInfo(currentId, WorkInfo.State.RUNNING),
            createWorkInfo(successorId, WorkInfo.State.BLOCKED),
        )
        // 关键用例：APPEND 后继在父任务完成前处于 BLOCKED，重试时必须拦截
        assertTrue(scheduler.hasPendingSuccessor(infos, currentId))
    }

    @Test
    fun testHasPendingSuccessor_successorIsEnqueued_returnsTrue() {
        val currentId = UUID.randomUUID()
        val successorId = UUID.randomUUID()
        val infos = listOf(
            createWorkInfo(currentId, WorkInfo.State.RUNNING),
            createWorkInfo(successorId, WorkInfo.State.ENQUEUED),
        )
        assertTrue(scheduler.hasPendingSuccessor(infos, currentId))
    }

    @Test
    fun testHasPendingSuccessor_successorIsRunning_returnsTrue() {
        val currentId = UUID.randomUUID()
        val successorId = UUID.randomUUID()
        val infos = listOf(
            createWorkInfo(currentId, WorkInfo.State.RUNNING),
            createWorkInfo(successorId, WorkInfo.State.RUNNING),
        )
        assertTrue(scheduler.hasPendingSuccessor(infos, currentId))
    }

    @Test
    fun testHasPendingSuccessor_allOtherFinished_returnsFalse() {
        val currentId = UUID.randomUUID()
        val infos = listOf(
            createWorkInfo(currentId, WorkInfo.State.RUNNING),
            createWorkInfo(UUID.randomUUID(), WorkInfo.State.SUCCEEDED),
            createWorkInfo(UUID.randomUUID(), WorkInfo.State.FAILED),
            createWorkInfo(UUID.randomUUID(), WorkInfo.State.CANCELLED),
        )
        assertFalse(scheduler.hasPendingSuccessor(infos, currentId))
    }

    @Test
    fun testHasPendingSuccessor_nullCurrentId_detectsBlockedAndEnqueued() {
        val blockedId = UUID.randomUUID()
        val enqueuedId = UUID.randomUUID()

        assertTrue(scheduler.hasPendingSuccessor(listOf(createWorkInfo(blockedId, WorkInfo.State.BLOCKED)), null))
        assertTrue(scheduler.hasPendingSuccessor(listOf(createWorkInfo(enqueuedId, WorkInfo.State.ENQUEUED)), null))
        assertFalse(scheduler.hasPendingSuccessor(listOf(createWorkInfo(UUID.randomUUID(), WorkInfo.State.SUCCEEDED)), null))
    }
}
