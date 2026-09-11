package com.example.fitlog.feature.reminder

/**
 * 训练提醒的调度契约（接口化以便纯 JVM 测试注入替身）。
 *
 * 生产实现 [WorkManagerReminderScheduler]：WorkManager 一次性任务自链，
 * 任务队列跨进程死亡与重启持久化。
 */
interface ReminderScheduler {

    /**
     * 调度/重排每日提醒（时间或开关变化时以唯一任务 REPLACE 覆盖）。
     *
     * @param minutesOfDay 提醒时刻（一天中的分钟数，0–1439）
     */
    fun schedule(minutesOfDay: Int)

    /**
     * Worker 触发后的自链调度：接力下一次提醒。
     *
     * 与 [schedule] 分离的原因：自链发生时 Worker 自身正以同一 unique name
     * 运行，REPLACE 语义会取消运行中的任务（见实现类 KDoc），因此接力
     * 必须走非打断式的追加语义。
     *
     * 实现必须**幂等**：Worker 重试或进程死亡重跑会再次进入本方法，
     * 无条件追加会让链翻倍（每天重复通知且永不收敛）。
     * 声明为 suspend：实现需查询既有任务状态（见实现类 KDoc）。
     *
     * @param minutesOfDay 提醒时刻（一天中的分钟数，0–1439）
     */
    suspend fun scheduleSelfChainedNext(minutesOfDay: Int)

    /** 取消提醒。 */
    fun cancel()
}
