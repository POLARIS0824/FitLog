package com.example.fitlog.feature.reminder

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.fitlog.util.log.FitLog
import kotlinx.coroutines.flow.first
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
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
 * WorkManager 的任务队列跨进程死亡与重启持久化；但 force-stop 会被系统
 * 整队清除，启动兜底补排程经 [recoverSchedule] 以 KEEP 语义自愈，
 * 避免 REPLACE 在到期临界点打断或推迟当天任务。
 *
 * 时间精度说明：WorkManager 非精确闹钟（不打 SCHEDULE_EXACT_ALARM），触发
 * 可能晚于设定时刻数分钟（系统 batching）——训练提醒场景可接受。
 */
@Singleton
class WorkManagerReminderScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : ReminderScheduler {

    /** {@inheritDoc} */
    override fun schedule(minutesOfDay: Int) {
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delayUntilNextOccurrence(minutesOfDay), TimeUnit.MILLISECONDS)
            .addTag(TAG)
            .build()
        FitLog.i(TAG, "提醒重排：$minutesOfDay 分钟档（REPLACE）")
        WorkManager.getInstance(context)
            .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    /** {@inheritDoc} */
    override fun recoverSchedule(minutesOfDay: Int) {
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delayUntilNextOccurrence(minutesOfDay), TimeUnit.MILLISECONDS)
            .addTag(TAG)
            .build()
        FitLog.i(TAG, "提醒启动恢复：$minutesOfDay 分钟档（KEEP）")
        WorkManager.getInstance(context)
            .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    /**
     * {@inheritDoc}
     *
     * 走 [ExistingWorkPolicy.APPEND_OR_REPLACE] 而非外部重排的 REPLACE：
     * Worker 自链时它自己正以同一 unique name 运行中——REPLACE 会把这条
     * 运行中的任务标为 CANCELLED 并打断（当前仅因 showNotification 无挂起点
     * 而侥幸无害，任何重构加入 suspend 调用都会静默断掉每日提醒）。
     * APPEND 把下一次任务挂为本任务的子节点，父任务正常完成后自动接力；
     * 用户改时间/开关时外部仍走 [schedule] 的 REPLACE，会取消整条 pending 链
     * 并以新时间重排，两条路径互不冲突、不产生重复提醒。
     *
     * **幂等守卫**：追加前查询唯一名下是否存在处于 BLOCKED、RUNNING 或 ENQUEUED
     * 状态的有效后继（排除当前 Worker 自身）：
     * - Worker 正在运行但尚未完成时，已通过 APPEND 追加的后继处于 BLOCKED 状态；
     * - Worker 失败重试或进程死亡重跑会再次进入本方法，此时后继处于 BLOCKED 或 ENQUEUED；
     * - 极端情况下后继可能已处于 RUNNING。
     * 若仅检查 ENQUEUED，BLOCKED 状态的后继会被漏判，导致每次重试都追加一个重复后继，
     * 链从而翻倍且永不收敛。核对 BLOCKED/RUNNING/ENQUEUED 确保幂等。
     */
    override suspend fun scheduleSelfChainedNext(minutesOfDay: Int, currentWorkId: UUID?) {
        val workInfos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkFlow(WORK_NAME)
            .first()
        if (hasPendingSuccessor(workInfos, currentWorkId)) {
            // 幂等守卫触发说明 Worker 重试/进程死亡重跑——正是链翻倍被拦下的现场
            FitLog.i(TAG, "自链跳过：已存在 BLOCKED/RUNNING/ENQUEUED 后继（幂等守卫生效）")
            return
        }

        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delayUntilNextOccurrence(minutesOfDay), TimeUnit.MILLISECONDS)
            .addTag(TAG)
            .build()
        FitLog.i(TAG, "提醒自链下一天：$minutesOfDay 分钟档（APPEND_OR_REPLACE）")
        WorkManager.getInstance(context)
            .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    /**
     * 判断在途任务列表中是否存在有效后继（处于 BLOCKED、ENQUEUED 或 RUNNING 状态）。
     *
     * @param workInfos 唯一任务名称关联的所有任务状态
     * @param currentWorkId 当前执行自链的 Worker ID；非 null 时排除自身，避免把自身的 RUNNING 误判为后继
     */
    internal fun hasPendingSuccessor(
        workInfos: List<WorkInfo>,
        currentWorkId: UUID?,
    ): Boolean {
        return workInfos.any { workInfo ->
            val isCurrentWorker = currentWorkId != null && workInfo.id == currentWorkId
            !isCurrentWorker && (
                workInfo.state == WorkInfo.State.BLOCKED ||
                workInfo.state == WorkInfo.State.ENQUEUED ||
                (currentWorkId != null && workInfo.state == WorkInfo.State.RUNNING)
            )
        }
    }

    /** {@inheritDoc} */
    override fun cancel() {
        FitLog.i(TAG, "提醒取消")
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    /** 距下一次到达提醒时刻（今天未到取今天，已过取明天）的毫秒数。 */
    private fun delayUntilNextOccurrence(minutesOfDay: Int): Long {
        val time = LocalTime.of(minutesOfDay / 60, minutesOfDay % 60)
        // 必须以即时（Instant）差计算而非 LocalDateTime 墙上时间差：delay 窗口
        // 横跨日光节约切换时（用户旅行/系统时区变化），墙上差值会让提醒
        // 偏早/偏晚一小时——类注释声明的"每日触发时刻随日光节约偏移更小"
        // 依赖本处锚定时区换算才成立
        val zone = ZoneId.systemDefault()
        var next = LocalDate.now(zone).atTime(time).atZone(zone)
        if (!next.toInstant().isAfter(Instant.now())) {
            next = next.plusDays(1)
        }
        return Duration.between(Instant.now(), next.toInstant()).toMillis().coerceAtLeast(0L)
    }

    private companion object {
        const val WORK_NAME = "workout_reminder"
        const val TAG = "workout_reminder"
    }
}
