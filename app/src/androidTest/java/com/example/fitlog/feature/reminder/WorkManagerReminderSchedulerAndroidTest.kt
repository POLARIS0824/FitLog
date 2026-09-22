package com.example.fitlog.feature.reminder

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.runBlocking
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import org.junit.After
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

    private lateinit var workers: ControlledWorkers

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        workers = ControlledWorkers()
        val config = Configuration.Builder()
            .setWorkerFactory(workers)
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
    @After
    fun tearDown() {
        workManager.cancelAllWork().result.get(10, TimeUnit.SECONDS)
        WorkManagerTestInitHelper.closeWorkDatabase()
    }

    @Test
    fun runningParentSurvivesRecoveryAndBlockedSuccessorRunsAfterCompletion() = runBlocking {
        scheduler.schedule(18 * 60)
        val parent = workManager.getWorkInfosForUniqueWork("workout_reminder").get().single().id
        val driver = requireNotNull(WorkManagerTestInitHelper.getTestDriver(context))
        driver.setInitialDelayMet(parent)
        assertEquals(parent, withTimeout(10_000) { workers.started.receive() })
        awaitState(parent, WorkInfo.State.RUNNING)

        // Recovery while the Worker is suspended must preserve this very execution.
        scheduler.recoverSchedule(18 * 60)
        assertEquals(parent, workManager.getWorkInfosForUniqueWork("workout_reminder").get().single().id)
        awaitState(parent, WorkInfo.State.RUNNING)
        scheduler.scheduleSelfChainedNext(18 * 60, parent)
        val chain = workManager.getWorkInfosForUniqueWork("workout_reminder").get()
        assertEquals(2, chain.size)
        val child = chain.single { it.id != parent }.id
        awaitState(child, WorkInfo.State.BLOCKED)

        // Recovery and a repeated self-chain call must preserve the blocked child too.
        scheduler.recoverSchedule(18 * 60)
        scheduler.scheduleSelfChainedNext(18 * 60, parent)
        assertEquals(setOf(parent, child), workManager.getWorkInfosForUniqueWork("workout_reminder").get().map { it.id }.toSet())
        awaitState(parent, WorkInfo.State.RUNNING)
        workers.succeed(parent)
        awaitState(parent, WorkInfo.State.SUCCEEDED)
        awaitState(child, WorkInfo.State.ENQUEUED)
        driver.setInitialDelayMet(child)
        assertEquals(child, withTimeout(10_000) { workers.started.receive() })
        awaitState(child, WorkInfo.State.RUNNING)
        workers.succeed(child)
        awaitState(child, WorkInfo.State.SUCCEEDED)
    }

    private suspend fun awaitState(id: UUID, state: WorkInfo.State) = withTimeout(10_000) {
        workManager.getWorkInfoByIdFlow(id).first { it?.state == state }
    }

    /** Holds actual WorkManager executions at a barrier without posting notifications. */
    private class ControlledWorkers : WorkerFactory() {
        val started = Channel<UUID>(Channel.UNLIMITED)
        private val completions = ConcurrentHashMap<UUID, CallbackToFutureAdapter.Completer<ListenableWorker.Result>>()

        override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker? {
            if (workerClassName != ReminderWorker::class.java.name) return null
            return object : ListenableWorker(appContext, workerParameters) {
                override fun startWork() = CallbackToFutureAdapter.getFuture<Result> { completer ->
                    completions[id] = completer
                    started.trySend(id)
                    "Reminder barrier $id"
                }
            }
        }

        fun succeed(id: UUID) {
            assertTrue(requireNotNull(completions.remove(id)).set(ListenableWorker.Result.success()))
        }
    }

}
