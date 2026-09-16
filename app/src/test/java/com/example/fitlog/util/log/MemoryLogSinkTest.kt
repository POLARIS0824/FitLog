package com.example.fitlog.util.log

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [MemoryLogSink] 环形缓冲行为单测。
 */
class MemoryLogSinkTest {

    private fun entry(message: String) = LogEntry(
        timeMillis = System.currentTimeMillis(),
        level = LogLevel.INFO,
        tag = "Test",
        message = message,
    )

    @Test
    fun `超出容量后淘汰最旧条目`() {
        val sink = MemoryLogSink(capacity = 3)

        repeat(5) { sink.log(entry("消息$it")) }

        val snapshot = sink.snapshot()
        assertEquals(3, snapshot.size)
        assertEquals("消息2", snapshot[0].message)
        assertEquals("消息4", snapshot.last().message)
    }

    @Test
    fun `写入与清空都推进版本号`() {
        val sink = MemoryLogSink()
        val before = sink.version.value

        sink.log(entry("a"))
        val afterLog = sink.version.value
        assertTrue(afterLog > before)

        sink.clear()
        assertTrue(sink.version.value > afterLog)
        assertTrue(sink.snapshot().isEmpty())
    }

    @Test
    fun `快照为缓冲副本互不影响`() {
        val sink = MemoryLogSink()
        sink.log(entry("唯一"))

        val snapshot = sink.snapshot()
        sink.clear()

        assertEquals(1, snapshot.size)
        assertTrue(sink.snapshot().isEmpty())
    }
}
