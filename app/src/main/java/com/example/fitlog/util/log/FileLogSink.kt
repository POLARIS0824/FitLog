package com.example.fitlog.util.log

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 按天轮转的文件日志 sink（App 私有目录，无需任何存储权限）。
 *
 * 设计要点：
 * - **异步写盘**：调用线程只做 [Channel.trySend]（容量 [CHANNEL_CAPACITY]，
 *   溢出丢最旧——日志洪峰下宁可丢历史也不阻塞业务线程），实际写盘由
 *   单线程消费者顺序追加，互不交错；
 * - **按天轮转**：文件名 `log-yyyy-MM-dd.txt`，跨天首条日志自动换文件，
 *   每天首次打开时惰性清理超过 [retentionDays] 的旧文件；
 * - **单文件封顶**：超过 [maxFileBytes] 轮转为 `log-yyyy-MM-dd.txt.1`
 *   （同日仅保一个溢出分卷，再溢出覆盖之）；
 * - **崩溃路径**：[flushSync] 在崩溃线程同步排空队列并刷盘，由
 *   [CrashHandler] 调用；
 * - **绝不打崩 App**：任何写盘异常只重置写入器并丢弃当条，下一条
 *   日志会尝试重开文件。
 *
 * 行格式（与查看页/导出共用）：
 * ```
 * 09-16 14:32:01.123 [W] [AgentEngine] [main] 记忆归档失败，降级为空
 *     java.io.IOException: ...
 *     at ...
 * ```
 *
 * @param directory 日志目录（由装配方传入 `filesDir/logs`）
 * @param minLevel 最低落盘级别（debug 构建记 DEBUG+，正式构建留 INFO+ 保住诊断元数据）
 * @param retentionDays 旧文件保留天数
 * @param maxFileBytes 单文件字节数上限，超出轮转 `.1` 分卷
 * @param consumerDispatcher 写盘协程调度器，单线程保证顺序；测试注入
 *   [Dispatchers.Unconfined] 可获得同步语义
 */
class FileLogSink(
    private val directory: File,
    override val minLevel: LogLevel = LogLevel.DEBUG,
    private val retentionDays: Int = DEFAULT_RETENTION_DAYS,
    private val maxFileBytes: Long = DEFAULT_MAX_FILE_BYTES,
    consumerDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1),
) : LogSink {

    /** 保护 writer 与文件操作的互斥锁（消费者协程与 flushSync 线程共用）。 */
    private val lock = Any()

    @Volatile
    private var writer: BufferedWriter? = null

    @Volatile
    private var writerDate: LocalDate? = null

    @Volatile
    private var lastCleanupDate: LocalDate? = null

    private val channel = Channel<LogEntry>(
        capacity = CHANNEL_CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private val scope = CoroutineScope(SupervisorJob() + consumerDispatcher)

    init {
        scope.launch {
            for (entry in channel) {
                appendSafely(entry)
            }
        }
    }

    override fun log(entry: LogEntry) {
        if (entry.level < minLevel) return
        channel.trySend(entry)
    }

    /** 崩溃线程调用：同步排空待写队列并强制刷盘（绕过异步消费者）。 */
    fun flushSync() {
        synchronized(lock) {
            while (true) {
                val entry = channel.tryReceive().getOrNull() ?: break
                appendSafelyLocked(entry)
            }
            runCatching { writer?.flush() }
        }
    }

    /** 删除全部日志文件并重置写入器（查看页「清空」操作）。 */
    fun clearFiles() {
        synchronized(lock) {
            closeWriterLocked()
            lastCleanupDate = null
            directory.listFiles { f -> f.name.startsWith(FILE_PREFIX) }?.forEach { it.delete() }
        }
    }

    /**
     * 全部日志文件，按日期新→旧排序；同日分卷排在该日基础文件之后
     * （`.1` 是更早的半段，基础文件是更晚的半段）。纯按文件名字典序排时
     * "log-2026-09-16.txt.1" > "log-2026-09-16.txt"，同日两半会颠倒，
     * 导出倒序拼接即不再时间正序。导出时倒序拼接即得时间正序。
     */
    fun logFiles(): List<File> =
        directory.listFiles { f -> f.isFile && f.name.startsWith(FILE_PREFIX) }
            ?.sortedWith(
                compareByDescending<File> { parseFileDate(it.name) ?: LocalDate.MIN }
                    // 同日：基础文件（新半段）在前，.1 分卷（旧半段）在后
                    .thenBy { if (it.name.endsWith(FILE_SUFFIX)) 0 else 1 }
                    .thenByDescending { it.name },
            )
            .orEmpty()

    // ---- 内部实现 ----

    private fun appendSafely(entry: LogEntry) {
        synchronized(lock) { appendSafelyLocked(entry) }
    }

    private fun appendSafelyLocked(entry: LogEntry) {
        try {
            val date = Instant.ofEpochMilli(entry.timeMillis)
                .atZone(ZoneId.systemDefault()).toLocalDate()
            ensureWriterLocked(date)
            val w = writer ?: return
            w.write(formatLine(entry))
            w.flush()
        } catch (_: Throwable) {
            // 写盘失败只重置写入器（下一条会重开），绝不让日志打崩 App
            closeWriterLocked()
        }
    }

    /** 确保写入器指向 [date] 对应文件，必要时轮转/清理/重开。 */
    private fun ensureWriterLocked(date: LocalDate) {
        if (writer != null) {
            if (writerDate == date) {
                maybeRollBySizeLocked(date)
                if (writer != null) return
            } else {
                // 跨天首条日志：关闭旧日期文件，走重开流程
                closeWriterLocked()
            }
        }
        cleanupOldFilesLocked(date)
        if (!directory.isDirectory && !directory.mkdirs()) return
        runCatching {
            writer = BufferedWriter(
                OutputStreamWriter(FileOutputStream(fileFor(date), true), StandardCharsets.UTF_8)
            )
            writerDate = date
        }.onFailure {
            writer = null
            writerDate = null
        }
    }

    /** 当前文件超过容量上限时轮转为 .1 分卷（同日只保一个，再溢出覆盖）。 */
    private fun maybeRollBySizeLocked(date: LocalDate) {
        val file = fileFor(date)
        if (!file.exists() || file.length() <= maxFileBytes) return
        closeWriterLocked()
        val rolled = File(directory, file.name + ROLLED_SUFFIX)
        rolled.delete()
        file.renameTo(rolled)
    }

    /** 每天首次打开文件时清理超过保留期的日志（含 .1 分卷）。 */
    private fun cleanupOldFilesLocked(today: LocalDate) {
        if (lastCleanupDate == today) return
        lastCleanupDate = today
        val cutoff = today.minusDays(retentionDays.toLong())
        directory.listFiles { f -> f.name.startsWith(FILE_PREFIX) }?.forEach { f ->
            parseFileDate(f.name)?.let { if (it.isBefore(cutoff)) f.delete() }
        }
    }

    private fun closeWriterLocked() {
        try {
            writer?.flush()
            writer?.close()
        } catch (_: Throwable) {
            // 关闭失败同样吞掉：文件句柄由 GC 兜底回收
        } finally {
            writer = null
            writerDate = null
        }
    }

    /** 组装落盘行：时间 [级别] [TAG] [线程] 消息 + 缩进堆栈。 */
    private fun formatLine(entry: LogEntry): String {
        val sb = StringBuilder(160)
        val time = TIME_FORMAT.format(
            Instant.ofEpochMilli(entry.timeMillis).atZone(ZoneId.systemDefault())
        )
        sb.append(time).append(" [").append(entry.level.label).append("] [")
            .append(entry.tag).append("] [").append(entry.threadName).append("] ")
            .append(entry.message).append('\n')
        entry.stackTrace?.let { stack ->
            stack.lineSequence().forEach { line ->
                sb.append("    ").append(line).append('\n')
            }
        }
        return sb.toString()
    }

    /** 从文件名解析日期（`log-2026-09-16.txt(.1)` → 2026-09-16）。 */
    private fun parseFileDate(name: String): LocalDate? = runCatching {
        LocalDate.parse(
            name.removePrefix(FILE_PREFIX).substringBefore(FILE_SUFFIX)
        )
    }.getOrNull()

    private fun fileFor(date: LocalDate): File = File(directory, "$FILE_PREFIX$date$FILE_SUFFIX")

    companion object {
        /** 日志保留天数。 */
        const val DEFAULT_RETENTION_DAYS = 7

        /** 单文件字节数上限（2MB）。 */
        const val DEFAULT_MAX_FILE_BYTES = 2L * 1024 * 1024

        private const val CHANNEL_CAPACITY = 512
        private const val FILE_PREFIX = "log-"
        private const val FILE_SUFFIX = ".txt"
        private const val ROLLED_SUFFIX = ".1"
        private val TIME_FORMAT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("MM-dd HH:mm:ss.SSS")
    }
}
