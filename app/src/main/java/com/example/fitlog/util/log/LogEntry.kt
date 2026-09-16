package com.example.fitlog.util.log

/**
 * 单条日志的不可变快照。
 *
 * [stackTrace] 在创建时即由 [Throwable.stackTraceToString] 格式化为字符串
 * （而非持有异常对象本身）：跨线程传递安全、不延长异常引用链、可直接落盘。
 *
 * @property timeMillis 产生时间（epoch 毫秒）
 * @property level 级别
 * @property tag 来源标签（约定为类名，沿用既有 TAG 常量）
 * @property message 正文（可多行）
 * @property stackTrace 关联异常堆栈（多行原文，未缩进；落盘时由 sink 决定缩进），可为 null
 * @property threadName 产生日志的线程名（区分主线程/IO/WorkManager 线程）
 */
data class LogEntry(
    val timeMillis: Long,
    val level: LogLevel,
    val tag: String,
    val message: String,
    val stackTrace: String? = null,
    val threadName: String = Thread.currentThread().name,
)
