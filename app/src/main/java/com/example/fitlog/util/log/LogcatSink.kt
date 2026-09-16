package com.example.fitlog.util.log

import android.util.Log

/**
 * 输出到 Logcat 的 sink。
 *
 * Logcat 与文件日志独立分级：debug 构建全量输出，正式构建保留 INFO+
 * （AI 请求元数据等关键链路日志真机排查时直接可见；文件日志的分级
 * 见 [FitLogBootstrap]）。
 *
 * 超长消息按 UTF-8 字节边界分片输出：logcat 单条上限约 4KB（按字节计），
 * AI 请求体等长日志会被系统静默截断，分片保证内容完整可见。
 *
 * @param minLevel 最低输出级别
 */
class LogcatSink(
    override val minLevel: LogLevel = LogLevel.DEBUG,
) : LogSink {

    override fun log(entry: LogEntry) {
        if (entry.level < minLevel) return
        val priority = when (entry.level) {
            LogLevel.DEBUG -> Log.DEBUG
            LogLevel.INFO -> Log.INFO
            LogLevel.WARN -> Log.WARN
            LogLevel.ERROR -> Log.ERROR
        }
        // println 重载不校验 tag 长度/内容，也不会因 message 为空抛异常
        val text = entry.stackTrace?.let { "${entry.message}\n$it" } ?: entry.message

        val bytes = text.toByteArray(Charsets.UTF_8)
        if (bytes.size <= MAX_LOGCAT_BYTES) {
            Log.println(priority, entry.tag, text)
            return
        }
        val partCount = (bytes.size + MAX_LOGCAT_BYTES - 1) / MAX_LOGCAT_BYTES
        var offset = 0
        var part = 1
        while (offset < bytes.size) {
            // 回退到 UTF-8 字符边界：续字节 (b & 0xC0) == 0x80，避免中文被从中间切开
            var end = minOf(offset + MAX_LOGCAT_BYTES, bytes.size)
            while (end < bytes.size && (bytes[end].toInt() and 0xC0) == 0x80) end--
            val chunk = String(bytes, offset, end - offset, Charsets.UTF_8)
            Log.println(priority, entry.tag, "($part/$partCount) $chunk")
            part++
            offset = end
        }
    }

    private companion object {
        /** logcat 单条字节上限（约 4067），留余量给 tag 与分片序号前缀。 */
        private const val MAX_LOGCAT_BYTES = 3500
    }
}
