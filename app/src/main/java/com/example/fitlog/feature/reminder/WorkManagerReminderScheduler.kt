package com.example.fitlog.feature.reminder

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [ReminderScheduler] 的 WorkManager 实现。
 *
 * ## 调度模型
 *
 * 每次调度一条**带初始延迟的一次性任务**（unique work，REPLACE）：
 * 延迟 = 距下一次到达提醒时刻（今天已过则明天）的时长。任务触发后由
 * [ReminderWorker] 发通知并**自链**下一天——开关/时间变化时经 [schedule]
 * 以 REPLACE 覆盖重排，[cancel] 清除。
 *
 * 选择 OneTime 自链而非 Periodic(24h)：每日触发时刻随日光节约/跨天偏移
 * 的累积漂移更小，且时间变更重排语义简单（替换唯一任务即可）。
 * WorkManager 的任务队列跨进程死亡与重启持久化，无需 Application 启动兜底。
 *
 * 时间精度说明：WorkManager 非精确闹钟（不打 SCHEDULE_EXACT_ALARM），触发
 * 可能晚于设定时刻数分钟（系统 batching）——训练提醒场景可接受。
 */
@Singleton
class WorkManagerReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : ReminderScheduler {

    /** {@inheritDoc} */
    override fun schedule(minutesOfDay: Int) {
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delayUntilNextOccurrence(minutesOfDay), TimeUnit.MILLISECONDS)
            .addTag(TAG)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    /** {@inheritDoc} */
    override fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    /** 距下一次到达提醒时刻（今天未到取今天，已过取明天）的毫秒数。 */
    private fun delayUntilNextOccurrence(minutesOfDay: Int): Long {
        val time = LocalTime.of(minutesOfDay / 60, minutesOfDay % 60)
        var next = LocalDateTime.of(LocalDate.now(), time)
        if (!next.isAfter(LocalDateTime.now())) {
            next = next.plusDays(1)
        }
        return Duration.between(LocalDateTime.now(), next).toMillis().coerceAtLeast(0L)
    }

    private companion object {
        const val WORK_NAME = "workout_reminder"
        const val TAG = "workout_reminder"
    }
}
