package com.example.fitlog.util.log

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 内存环形缓冲 sink：为 App 内日志查看页提供最近日志的实时快照。
 *
 * 设计取舍：不直接暴露 `StateFlow<List<LogEntry>>`（每条日志都复制整表），
 * 而是暴露自增 [version]——查看页订阅版本号，变化时再调 [snapshot] 取
 * 一次性快照做筛选。无人查看时零复制开销，容量固定 [DEFAULT_CAPACITY]
 * 条无泄漏风险。
 *
 * 始终全量接收（minLevel 固定 DEBUG）：查看页需要看到 debug 构建的
 * 全部细节；正式构建里低级别日志在调用侧/文件侧已被裁剪。
 *
 * @param capacity 缓冲容量，超出后淘汰最旧条目
 */
class MemoryLogSink(
    private val capacity: Int = DEFAULT_CAPACITY,
) : LogSink {

    override val minLevel: LogLevel = LogLevel.DEBUG

    private val lock = Any()
    private val buffer = ArrayDeque<LogEntry>(capacity.coerceAtLeast(1))

    private val _version = MutableStateFlow(0L)

    /** 版本号：每写入/清空一次自增，查看页据此感知变化。 */
    val version: StateFlow<Long> = _version

    override fun log(entry: LogEntry) {
        synchronized(lock) {
            if (buffer.size >= capacity) buffer.removeFirst()
            buffer.addLast(entry)
            _version.value++
        }
    }

    /** 当前缓冲快照（按时间旧→新排列，查看页自行倒序展示）。 */
    fun snapshot(): List<LogEntry> = synchronized(lock) { buffer.toList() }

    /** 清空缓冲（查看页「清空」操作）。 */
    fun clear() {
        synchronized(lock) {
            buffer.clear()
            _version.value++
        }
    }

    companion object {
        /** 默认缓冲容量：约覆盖一次完整的导入/对话会话。 */
        const val DEFAULT_CAPACITY = 1000
    }
}
