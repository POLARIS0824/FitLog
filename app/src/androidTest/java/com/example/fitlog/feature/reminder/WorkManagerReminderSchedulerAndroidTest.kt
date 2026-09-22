package com.example.fitlog.feature.reminder

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [WorkManagerReminderScheduler] 在真实 Android / WorkManager 环境下的集成测试。
 *
 * 验证：
 * 1. [WorkManagerReminderScheduler.schedule]：正常入队带有预期 tag 的 OneTimeWorkRequest，REPLACE 覆盖旧任务；
 * 2. [WorkManagerReminderScheduler.recoverSchedule]：KEEP 策略下保留已有的在途任务；
 * 3. [WorkManagerReminderScheduler.cancel]：成功取消唯一工作；
 * 4. [WorkManagerReminderScheduler.scheduleSelfChainedNext]：在没有后继时成功自链追加新任务；
 * 5. [WorkManagerReminderScheduler.scheduleSelfChainedNext]：在已存在后继时触发幂等守卫，不产生重复后继。
 */
@RunWith(AndroidJUnit4::class)
class WorkManagerReminderSchedulerAndroidTest {

    private lateinit var context: Context
    private lateinit var scheduler: WorkManagerReminderScheduler
    private lateinit var workManager: WorkManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(Log.DEBUG)
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
        workManager = WorkManager.getInstance(context)
        scheduler = WorkManagerReminderScheduler(context)
    }

    @Test
    fun testSchedule_enqueuesWorkWithExpectedTag() {
        scheduler.schedule(18 * 60)
        val works = workManager.getWorkInfosForUniqueWork("workout_reminder").get()
        assertEquals(1, works.size)
        val work = works[0]
        assertTrue(work.tags.contains("workout_reminder"))
        assertTrue(work.state == WorkInfo.State.ENQUEUED || work.state == WorkInfo.State.RUNNING)
    }

    @Test
    fun testSchedule_replacePolicyOverridesExistingWork() {
        scheduler.schedule(18 * 60)
        val firstWorks = workManager.getWorkInfosForUniqueWork("workout_reminder").get()
        val firstId = firstWorks[0].id

        scheduler.schedule(7 * 60)
        val secondWorks = workManager.getWorkInfosForUniqueWork("workout_reminder").get()
        assertEquals(1, secondWorks.size)
        val secondId = secondWorks[0].id
        assertNotEquals(firstId, secondId)
    }

    @Test
    fun testRecoverSchedule_keepPolicyPreservesExistingWork() {
        scheduler.schedule(18 * 60)
        val firstWorks = workManager.getWorkInfosForUniqueWork("workout_reminder").get()
        val firstId = firstWorks[0].id

        scheduler.recoverSchedule(18 * 60)
        val secondWorks = workManager.getWorkInfosForUniqueWork("workout_reminder").get()
        assertEquals(1, secondWorks.size)
        assertEquals(firstId, secondWorks[0].id)
    }

    @Test
    fun testCancel_cancelsWork() {
        scheduler.schedule(18 * 60)
        scheduler.cancel()
        val works = workManager.getWorkInfosForUniqueWork("workout_reminder").get()
        assertTrue(works.isEmpty() || works.all { it.state == WorkInfo.State.CANCELLED })
    }

    @Test
    fun testScheduleSelfChainedNext_whenNoSuccessor_appendsWork() = runBlocking {
        scheduler.schedule(18 * 60)
        val initialWorks = workManager.getWorkInfosForUniqueWork("workout_reminder").get()
        val initialId = initialWorks[0].id

        scheduler.scheduleSelfChainedNext(18 * 60, currentWorkId = initialId)
        val updatedWorks = workManager.getWorkInfosForUniqueWork("workout_reminder").get()
        assertTrue(updatedWorks.size >= 2)
    }

    @Test
    fun testScheduleSelfChainedNext_idempotencyGuardsAgainstDuplicates() = runBlocking {
        scheduler.schedule(18 * 60)
        val initialWorks = workManager.getWorkInfosForUniqueWork("workout_reminder").get()
        val initialId = initialWorks[0].id

        scheduler.scheduleSelfChainedNext(18 * 60, currentWorkId = initialId)
        val countAfterFirstChain = workManager.getWorkInfosForUniqueWork("workout_reminder").get().size

        // 二次调用携带相同 Worker ID 时，幂等守卫应当拦截追加
        scheduler.scheduleSelfChainedNext(18 * 60, currentWorkId = initialId)
        val countAfterSecondChain = workManager.getWorkInfosForUniqueWork("workout_reminder").get().size

        assertEquals(countAfterFirstChain, countAfterSecondChain)
    }
}
