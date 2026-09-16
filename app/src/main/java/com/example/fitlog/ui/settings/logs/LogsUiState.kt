package com.example.fitlog.ui.settings.logs

import com.example.fitlog.util.log.LogEntry
import com.example.fitlog.util.log.LogLevel

/**
 * 日志查看页 UI 状态。
 *
 * @property entries 当前筛选结果（时间倒序，最新在前）
 * @property totalEntries 筛选前的总条数（与 [entries].size 一起展示筛选命中）
 * @property query 关键字（tag/message/堆栈 模糊匹配）
 * @property minLevel 最低级别筛选（null = 全部）
 * @property isExporting 导出进行中（禁用按钮防重复）
 * @property message 一次性操作提示（Snackbar 展示后清除）
 */
data class LogsUiState(
    val entries: List<LogEntry> = emptyList(),
    val totalEntries: Int = 0,
    val query: String = "",
    val minLevel: LogLevel? = null,
    val isExporting: Boolean = false,
    val message: String? = null,
)
