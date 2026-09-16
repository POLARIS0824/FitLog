package com.example.fitlog.util.log

import java.util.concurrent.atomic.AtomicBoolean

/**
 * 全局未捕获异常处理器：崩溃全栈落盘后交还原有处理链（保持系统崩溃
 * 对话框/进程退出流程不变）。
 *
 * 安装方式见 [FitLogBootstrap]——链式包装 [Thread.getDefaultUncaughtExceptionHandler]
 * 当时的原值，而不是粗暴覆盖（将来若接入 Crashlytics 等上报 SDK，仍能
 * 沿链传递）。
 *
 * @param fileSink 用于同步落盘的文件 sink（崩溃路径必须绕过异步队列）
 * @param previous 安装时点原有的处理器，记录完成后原样交还
 */
class CrashHandler(
    private val fileSink: FileLogSink,
    private val previous: Thread.UncaughtExceptionHandler?,
) : Thread.UncaughtExceptionHandler {

    /** 防重入：崩溃记录自身再抛异常时直接透传，避免无限递归。 */
    private val crashing = AtomicBoolean(false)

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        if (!crashing.compareAndSet(false, true)) {
            previous?.uncaughtException(thread, throwable)
            return
        }
        try {
            FitLog.e(
                TAG,
                "[CRASH] 线程 ${thread.name} 未捕获异常：${throwable.javaClass.name}",
                throwable,
            )
            fileSink.flushSync()
        } catch (_: Throwable) {
            // 崩溃记录链路的任何异常都不能阻止进程按原路径退出
        } finally {
            previous?.uncaughtException(thread, throwable)
        }
    }

    private companion object {
        private const val TAG = "CrashHandler"
    }
}
