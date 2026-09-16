package com.example.fitlog.util.log

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [FitLog] 门面的路由与容错行为单测。
 */
class FitLogTest {

    /** 捕获型 sink：记录门面分发到的全部条目。 */
    private class RecordingSink(override val minLevel: LogLevel = LogLevel.DEBUG) : LogSink {
        val entries = mutableListOf<LogEntry>()
        override fun log(entry: LogEntry) {
            entries.add(entry)
        }
    }

    @Before
    fun setUp() {
        FitLog.uproot()
    }

    @After
    fun tearDown() {
        FitLog.uproot()
    }

    @Test
    fun `级别与异常正确路由到 sink`() {
        val sink = RecordingSink()
        FitLog.plant(sink)

        FitLog.w("Tag", "降级为空", IllegalStateException("boom"))

        assertEquals(1, sink.entries.size)
        val entry = sink.entries.first()
        assertEquals(LogLevel.WARN, entry.level)
        assertEquals("Tag", entry.tag)
        assertEquals("降级为空", entry.message)
        assertNotNull(entry.stackTrace)
        assertTrue(entry.stackTrace!!.contains("IllegalStateException"))
    }

    @Test
    fun `未携带异常时 stackTrace 为空`() {
        val sink = RecordingSink()
        FitLog.plant(sink)

        FitLog.i("Tag", "纯文本")

        assertNull(sink.entries.single().stackTrace)
    }

    @Test
    fun `低于全部 sink 门槛的级别被短路`() {
        val sink = RecordingSink(minLevel = LogLevel.ERROR)
        FitLog.plant(sink)

        FitLog.i("Tag", "不应到达")
        FitLog.w("Tag", "不应到达")

        assertTrue(sink.entries.isEmpty())
    }

    @Test
    fun `sink 抛异常不会向上传播`() {
        FitLog.plant(LogSink { throw IllegalStateException("sink 内部崩溃") })

        // 不应抛出任何异常
        FitLog.e("Tag", "触发异常 sink")
    }

    @Test
    fun `重复 plant 同一实例不重复安装`() {
        val sink = RecordingSink()
        FitLog.plant(sink)
        FitLog.plant(sink)

        FitLog.d("Tag", "一次")

        assertEquals(1, sink.entries.size)
    }
}
