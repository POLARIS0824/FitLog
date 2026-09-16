package com.example.fitlog.util.log

/**
 * 日志输出目的地（SAM 接口）。
 *
 * 契约：
 * - [log] 必须快速返回、永不抛异常——门面虽会兜底捕获，但实现自身负责
 *   容错（写盘失败静默重置、Logcat 输出无副作用）；
 * - [minLevel] 声明该目的地接受的最低级别，门面据此在组装 [LogEntry]
 *   之前做全局短路（正式构建里 DEBUG 调用零成本），实现内部也应自行过滤。
 */
fun interface LogSink {

    /** 该 sink 接受的最低日志级别。 */
    val minLevel: LogLevel
        get() = LogLevel.DEBUG

    /** 处理一条日志。 */
    fun log(entry: LogEntry)
}
