package com.example.fitlog.ui.settings.logs

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fitlog.util.log.FitLog
import com.example.fitlog.util.log.FitLogBootstrap
import com.example.fitlog.util.log.LogLevel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/**
 * 日志查看页 ViewModel。
 *
 * 数据源是 [FitLogBootstrap.memorySink] 的内存环形缓冲（最近 1000 条）：
 * 订阅其自增版本号，变化时重新取快照并按当前筛选条件过滤——页面打开期间
 * 新产生的日志实时可见。筛选（关键字/级别）与快照同在 combine 中重算，
 * 条目量级（≤1000）下主线程开销可忽略。
 *
 * 日志子系统刻意不进 Hilt 图（见 [FitLogBootstrap] 的装配说明），本 VM
 * 经该单例对象访问，是全项目唯一的此类例外。
 */
@HiltViewModel
class LogsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val minLevel = MutableStateFlow<LogLevel?>(null)
    private val isExporting = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)

    /** 页面 UI 状态流：缓冲版本号（数据源）+ 筛选条件 + 一次性提示。 */
    val uiState: StateFlow<LogsUiState> = combine(
        FitLogBootstrap.memorySink.version,
        query,
        minLevel,
        isExporting,
        message,
    ) { version, q, level, exporting, msg ->
        val snapshot = FitLogBootstrap.memorySink.snapshot()
        val qTrimmed = q.trim()
        val filtered = snapshot.filter { entry ->
            (level == null || entry.level >= level) &&
                (qTrimmed.isEmpty() || entry.message.contains(qTrimmed, ignoreCase = true) ||
                    entry.tag.contains(qTrimmed, ignoreCase = true) ||
                    entry.stackTrace?.contains(qTrimmed, ignoreCase = true) == true)
        }
        LogsUiState(
            entries = filtered.asReversed(),
            totalEntries = snapshot.size,
            query = q,
            minLevel = level,
            isExporting = exporting,
            message = msg,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LogsUiState(),
    )

    /** 搜索关键字变化。 */
    fun onQueryChange(value: String) {
        query.value = value
    }

    /** 级别筛选变化（null = 全部）。 */
    fun onLevelSelected(level: LogLevel?) {
        minLevel.value = level
    }

    /** 一次性提示已展示，清除。 */
    fun onMessageShown() {
        message.value = null
    }

    /**
     * 清空日志：删除全部日志文件并重置内存缓冲。
     *
     * 文件写入是异步消费者，清空后消费者下次落盘会按当天重建新文件，
     * 无需额外复位操作。
     */
    fun onClearLogs() {
        viewModelScope.launch {
            try {
                FitLogBootstrap.fileSink.clearFiles()
                FitLogBootstrap.memorySink.clear()
                FitLog.i(TAG, "日志已清空（用户操作）")
                message.value = "日志已清空"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                FitLog.w(TAG, "清空日志失败", e)
                message.value = "清空失败：${e.message}"
            }
        }
    }

    /**
     * SAF 建档回调：按时间正序合并全部日志文件写出。
     *
     * [FileLogSink.logFiles] 返回新→旧排序，倒序拼接即得时间正序；
     * 消费者协程并发追加中的末行可能只写出一半，属可接受的最终一致。
     */
    fun onExportTargetSelected(uri: Uri) {
        viewModelScope.launch {
            isExporting.value = true
            try {
                val files = FitLogBootstrap.fileSink.logFiles()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(
                            ("FitLog 日志导出 ${java.time.LocalDateTime.now()}" +
                                "（${files.size} 个文件，按时间正序合并）\n\n").toByteArray(),
                        )
                        files.asReversed().forEach { file ->
                            file.inputStream().use { it.copyTo(out) }
                            out.write("\n".toByteArray())
                        }
                    } ?: throw IllegalStateException("无法打开导出文件")
                }
                FitLog.i(TAG, "日志导出完成：${files.size} 个文件 → $uri")
                message.value = "导出完成（${files.size} 个日志文件）"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                FitLog.w(TAG, "日志导出失败", e)
                message.value = "导出失败：${e.message}"
            } finally {
                isExporting.value = false
            }
        }
    }

    private companion object {
        private const val TAG = "LogsViewModel"
    }
}
