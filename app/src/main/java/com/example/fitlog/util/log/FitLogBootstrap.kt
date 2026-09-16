package com.example.fitlog.util.log

import android.content.Context
import android.os.Build
import com.example.fitlog.BuildConfig
import java.io.File

/**
 * 日志子系统装配入口：[com.example.fitlog.FitLogApplication] onCreate
 * **最先**调用（先于 Hilt 初始化，DI 阶段的异常才能被记录）。
 * 幂等，重复调用无副作用。
 *
 * 三路输出与分级策略：
 * - Logcat：debug 构建全量；正式构建留 INFO+——AI 请求元数据等关键链路日志
 *   在真机连 adb 排查时直接可见（logcat 仅本机可读，无泄露面），与文件日志同级
 * - 文件（`filesDir/logs/`）：debug 构建记 DEBUG+；正式构建留 INFO+——
 *   AI 请求元数据、导入汇总等离线诊断信息仍需留档，但不落调试正文
 * - 内存缓冲（[memorySink]）：始终全量，日志查看页数据源
 *
 * 刻意**不进 Hilt 图**（Timber 式静态装配）：日志子系统必须先于 DI 完成
 * 初始化，且调用点包含 object/TypeConverter/Worker 等非注入环境；查看页
 * （`ui/settings/logs`）经本对象持有引用访问。
 */
object FitLogBootstrap {

    /** 内存缓冲 sink（查看页实时数据源，全局唯一）。 */
    val memorySink: MemoryLogSink = MemoryLogSink()

    @Volatile
    private var initialized = false

    @Volatile
    private var _fileSink: FileLogSink? = null

    /** 文件日志 sink（查看页导出/清空经此操作）；未初始化前访问抛出。 */
    val fileSink: FileLogSink
        get() = _fileSink ?: error("FitLogBootstrap 尚未初始化")

    /**
     * 装配日志子系统并安装崩溃捕获。
     *
     * @param context 应用上下文（取 filesDir 定位日志目录）
     */
    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val appContext = context.applicationContext
            val file = FileLogSink(
                directory = File(appContext.filesDir, "logs"),
                minLevel = if (BuildConfig.DEBUG) LogLevel.DEBUG else LogLevel.INFO,
            )
            _fileSink = file
            FitLog.plant(LogcatSink(minLevel = if (BuildConfig.DEBUG) LogLevel.DEBUG else LogLevel.INFO))
            FitLog.plant(file)
            FitLog.plant(memorySink)
            Thread.setDefaultUncaughtExceptionHandler(
                CrashHandler(file, Thread.getDefaultUncaughtExceptionHandler())
            )
            FitLog.i(
                TAG,
                "App 冷启动：version=${BuildConfig.VERSION_NAME}(${BuildConfig.VERSION_CODE}) " +
                    "sdk=${Build.VERSION.SDK_INT} device=${Build.MANUFACTURER}/${Build.MODEL} " +
                    "buildType=${if (BuildConfig.DEBUG) "debug" else "release"}",
            )
            initialized = true
        }
    }

    private const val TAG = "FitLogBootstrap"
}
