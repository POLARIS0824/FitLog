package com.example.fitlog.ui.settings.dataimport

import com.example.fitlog.data.file.MarkdownFileScanner

/**
 * 数据导入页的 UI 状态。
 */
data class DataImportUiState(
    val isScanning: Boolean = false,
    val successes: List<MarkdownFileScanner.ScannedMarkdown> = emptyList(),
    val failures: List<MarkdownFileScanner.Failure> = emptyList(),
    val isImporting: Boolean = false,
    /** 是否正在导出（导出目标文件已选定、写入进行中） */
    val isExporting: Boolean = false,
    /** 一次性提示（Snackbar 展示，展示后清除） */
    val message: String? = null,
)
