package com.example.fitlog.util.log

import java.util.concurrent.CopyOnWriteArrayList

/**
 * 全局日志门面（Timber 式静态入口）。
 *
 * 全项目约定：**不再直接调用 `android.util.Log`**，统一经本门面输出，
 * 由安装的 [LogSink] 决定实际去向（Logcat / 按天轮转文件 / 内存缓冲）。
 * 装配见 [FitLogBootstrap]（Application onCreate 最先调用），装配前的
 * 调用静默丢弃（与 Timber 行为一致）。
 *
 * 使用方式（沿用既有 TAG 约定）：
 * ```
 * private const val TAG = "AgentEngine"
 * FitLog.w(TAG, "记忆归档失败，降级为空", e)
 * ```
 *
 * 线程安全：sink 列表为写时复制容器，[log] 可在任意线程并发调用；
 * 单条 [LogEntry] 为不可变快照，各 sink 各自处理同步。
 */
object FitLog {

    /** 已安装的输出目的地；读多写少，遍历期间无锁。 */
    private val sinks = CopyOnWriteArrayList<LogSink>()

    /** 全部 sink 中最低的接受级别，低于它则连 [LogEntry] 都不组装。 */
    @Volatile
    private var lowestSinkLevel: LogLevel = LogLevel.ERROR

    /** 安装一个输出目的地（同一实例不重复安装）。 */
    @Synchronized
    fun plant(sink: LogSink) {
        if (sink in sinks) return
        sinks.add(sink)
        refreshLowestLevel()
    }

    /** 移除全部目的地（仅单测使用，业务代码不得调用）。 */
    @Synchronized
    fun uproot() {
        sinks.clear()
        lowestSinkLevel = LogLevel.ERROR
    }

    /** DEBUG 级日志。 */
    fun d(tag: String, message: String, error: Throwable? = null) {
        log(LogLevel.DEBUG, tag, message, error)
    }

    /** INFO 级日志。 */
    fun i(tag: String, message: String, error: Throwable? = null) {
        log(LogLevel.INFO, tag, message, error)
    }

    /** WARN 级日志。 */
    fun w(tag: String, message: String, error: Throwable? = null) {
        log(LogLevel.WARN, tag, message, error)
    }

    /** ERROR 级日志。 */
    fun e(tag: String, message: String, error: Throwable? = null) {
        log(LogLevel.ERROR, tag, message, error)
    }

    /** 核心路由：组装快照并分发到全部 sink。 */
    fun log(level: LogLevel, tag: String, message: String, error: Throwable? = null) {
        if (level < lowestSinkLevel) return
        val entry = LogEntry(
            timeMillis = System.currentTimeMillis(),
            level = level,
            tag = tag,
            message = message,
            stackTrace = error?.stackTraceToString(),
        )
        for (sink in sinks) {
            try {
                sink.log(entry)
            } catch (t: Throwable) {
                // 日志子系统绝不允许把 App 打崩；实现方 bug 只能吞掉并留系统级痕迹
                System.err.println("FitLog: sink ${sink.javaClass.simpleName} 抛出异常: $t")
            }
        }
    }

    /** 重算全局最低接受级别（plant 后调用，持锁上下文中执行）。 */
    private fun refreshLowestLevel() {
        lowestSinkLevel = if (sinks.isEmpty()) {
            LogLevel.ERROR
        } else {
            sinks.minOf { it.minLevel }
        }
    }
}
