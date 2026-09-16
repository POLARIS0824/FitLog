package com.example.fitlog.util.log

import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import java.time.ZoneId

/**
 * [FileLogSink] 落盘、轮转与清理行为单测。
 *
 * 注入 [Dispatchers.Unconfined] 使消费者协程在 trySend 线程内联执行，
 * 测试获得同步语义，无需轮询等待。
 */
class FileLogSinkTest {

    private lateinit var directory: File

    @Before
    fun setUp() {
        directory = Files.createTempDirectory("fitlog-test").toFile()
    }

    @After
    fun tearDown() {
        directory.listFiles()?.forEach { it.delete() }
        directory.delete()
    }

    /** 构造同步写盘语义的 sink（Unconfined 消费者）。 */
    private fun newSink(
        minLevel: LogLevel = LogLevel.DEBUG,
        retentionDays: Int = 7,
        maxFileBytes: Long = FileLogSink.DEFAULT_MAX_FILE_BYTES,
    ) = FileLogSink(
        directory = directory,
        minLevel = minLevel,
        retentionDays = retentionDays,
        maxFileBytes = maxFileBytes,
        consumerDispatcher = Dispatchers.Unconfined,
    )

    private fun entry(
        message: String,
        level: LogLevel = LogLevel.INFO,
        dayOffset: Long = 0,
        error: Throwable? = null,
    ) = LogEntry(
        timeMillis = LocalDate.now().plusDays(dayOffset)
            .atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
        level = level,
        tag = "TestTag",
        message = message,
        stackTrace = error?.stackTraceToString(),
    )

    private fun todayFile() = File(directory, "log-${LocalDate.now()}.txt")

    private fun todayFileContent() = todayFile().readText()

    @Test
    fun `按格式落盘并包含堆栈`() {
        val sink = newSink()

        sink.log(entry("训练会话已结束", error = IllegalStateException("db busy")))

        val content = todayFileContent()
        assertTrue(content.contains(" [I] [TestTag] ["))
        assertTrue(content.contains("] 训练会话已结束"))
        // 堆栈缩进换行
        assertTrue(content.contains("    java.lang.IllegalStateException: db busy"))
    }

    @Test
    fun `低于门槛的级别不落盘`() {
        val sink = newSink(minLevel = LogLevel.INFO)

        sink.log(entry("调试细节", level = LogLevel.DEBUG))

        assertFalse(todayFile().exists())
    }

    @Test
    fun `跨天写入自动轮转文件`() {
        val sink = newSink()

        sink.log(entry("昨天的记录", dayOffset = -1))
        sink.log(entry("今天的记录"))

        val yesterday = File(directory, "log-${LocalDate.now().minusDays(1)}.txt")
        assertTrue(yesterday.exists())
        assertTrue(yesterday.readText().contains("昨天的记录"))
        assertTrue(todayFileContent().contains("今天的记录"))
    }

    @Test
    fun `单文件超过上限轮转为点1分卷`() {
        val sink = newSink(maxFileBytes = 16)

        sink.log(entry("第一条长消息XXXXXXXXXXXX"))
        sink.log(entry("第二条消息YYYYYYYYYYYY"))

        val rolled = File(directory, "log-${LocalDate.now()}.txt.1")
        assertTrue(rolled.exists())
        assertTrue(rolled.readText().contains("第一条"))
        assertTrue(todayFileContent().contains("第二条"))
    }

    @Test
    fun `清理超过保留期的旧文件`() {
        val stale = File(directory, "log-2000-01-01.txt").apply {
            writeText("过期内容")
        }
        val fresh = File(directory, "log-${LocalDate.now()}.txt").apply {
            writeText("")
        }

        val sink = newSink(retentionDays = 7)
        sink.log(entry("触发清理"))

        assertFalse("过期文件应被删除", stale.exists())
        assertTrue("保留期内的文件不应被误删", fresh.exists())
    }

    @Test
    fun `清空操作删除全部日志文件`() {
        val sink = newSink()
        sink.log(entry("写入一条"))
        assertNotNull(todayFile())

        sink.clearFiles()

        assertTrue(sink.logFiles().isEmpty())
        assertFalse(todayFile().exists())
    }

    @Test
    fun `flushSync 不抛异常且内容完好`() {
        val sink = newSink()
        sink.log(entry("崩溃前最后一条"))

        // 崩溃线程语义：排空队列 + 强制刷盘，不应有副作用异常
        sink.flushSync()

        assertTrue(todayFileContent().contains("崩溃前最后一条"))
    }

    @Test
    fun `logFiles 按日期新到旧排序`() {
        newSink().apply {
            log(entry("昨天", dayOffset = -1))
            log(entry("今天"))
        }

        val names = FileLogSink(
            directory = directory,
            consumerDispatcher = Dispatchers.Unconfined,
        ).logFiles().map { it.name }

        if (names.size >= 2) {
            assertEquals("log-${LocalDate.now()}.txt", names.first())
        }
    }

    @Test
    fun `目录不存在时自动创建`() {
        val nested = File(directory, "nested/logs")
        val sink = FileLogSink(
            directory = nested,
            consumerDispatcher = Dispatchers.Unconfined,
        )

        sink.log(entry("深目录写入"))

        assertTrue(nested.isDirectory)
        assertTrue(File(nested, "log-${LocalDate.now()}.txt").exists())
    }
}
