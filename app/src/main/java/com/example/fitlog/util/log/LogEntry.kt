package com.example.fitlog.util.log

import java.util.concurrent.atomic.AtomicLong

/**
 * 单条日志的不可变快照。
 *
 * [stackTrace] 在创建时即由 [Throwable.stackTraceToString] 格式化为字符串
 * （而非持有异常对象本身）：跨线程传递安全、不延长异常引用链、可直接落盘。
 *
 * @property seq 进程内单调递增序号（LazyColumn 等需要稳定唯一 key 的消费方专用；
 *   同毫秒同内容的两条日志仅凭 timeMillis+message 无法区分，作 key 会碰撞崩溃）
 * @property timeMillis 产生时间（epoch 毫秒）
 * @property level 级别
 * @property tag 来源标签（约定为类名，沿用既有 TAG 常量）
 * @property message 正文（可多行）
 * @property stackTrace 关联异常堆栈（多行原文，未缩进；落盘时由 sink 决定缩进），可为 null
 * @property threadName 产生日志的线程名（区分主线程/IO/WorkManager 线程）
 */
data class LogEntry(
    val seq: Long = SEQ.getAndIncrement(),
    val timeMillis: Long = System.currentTimeMillis(),
    val level: LogLevel,
    val tag: String,
    val message: String,
    val stackTrace: String? = null,
    val threadName: String = Thread.currentThread().name,
) {
    private companion object {
        /** 进程内全局序号发生器（ FitLog.log 是唯一构造入口，序号严格递增唯一）。 */
        val SEQ = AtomicLong(0L)
    }
}
