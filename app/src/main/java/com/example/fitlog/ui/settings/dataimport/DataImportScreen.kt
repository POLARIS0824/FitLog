package com.example.fitlog.ui.settings.dataimport

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fitlog.data.file.MarkdownExporter
import com.example.fitlog.data.file.MarkdownFileScanner
import com.example.fitlog.ui.components.CollapsingTitleScaffold
import com.example.fitlog.ui.components.FitLogCard
import com.example.fitlog.ui.components.SectionLabel
import com.example.fitlog.ui.components.StackedSnackbarHost
import com.example.fitlog.ui.components.rememberStackedSnackbarHostState
import java.time.LocalDate

/** 扫描结果在滚动 Column 中的最大渲染行数（超出部分折叠为摘要行）。 */
private const val MAX_SHOWN_RESULT_ROWS = 20

/**
 * 1. 容器层 (Stateful)
 */
@Composable
fun DataImportRoute(
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: DataImportViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    DataImportScreen(
        uiState = uiState,
        onBack = onBack,
        onFolderSelected = viewModel::onFolderSelected,
        onImport = viewModel::onImport,
        onExportTargetSelected = viewModel::onExportTargetSelected,
        onMessageShown = viewModel::onMessageShown,
        modifier = modifier,
    )
}

/**
 * 2. 纯 UI 展示层 (Stateless)
 *
 * 动态双标题交互契约见 [CollapsingTitleScaffold]。
 */
@Composable
fun DataImportScreen(
    uiState: DataImportUiState,
    onBack: () -> Unit,
    onFolderSelected: (Uri) -> Unit,
    onImport: () -> Unit,
    onExportTargetSelected: (Uri) -> Unit,
    onMessageShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val stackedSnackbarHostState = rememberStackedSnackbarHostState()

    // SAF 文件夹选择器
    val folderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let(onFolderSelected)
    }

    // SAF 建档导出（建议名带日期，避免覆盖历史导出）
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/markdown")
    ) { uri ->
        uri?.let(onExportTargetSelected)
    }

    CollapsingTitleScaffold(
        title = "Data Import",
        onBack = onBack,
        parentTitle = "Settings",
        modifier = modifier,
        snackbarHost = { StackedSnackbarHost(hostState = stackedSnackbarHostState) },
    ) {
        SectionLabel("说明")
        FitLogCard {
            Text("从 Markdown 导入训练日志", style = MaterialTheme.typography.titleMedium)
            Text(
                "选择存放日志的文件夹，每个文件代表一天训练，" +
                    "文件名需为日期格式，如 2026-05-07.md" +
                    "（扫描文件夹的根目录，子文件夹不递归）",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(
                    onClick = { folderLauncher.launch(null) },
                    enabled = !uiState.isScanning,
                ) {
                    Text("选择文件夹")
                }
                if (uiState.isScanning) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .size(20.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }
        }

        if (uiState.successes.isNotEmpty() || uiState.failures.isNotEmpty()) {
            SectionLabel("扫描结果")
            FitLogCard {
                // 结果列表封顶渲染：扫描目录可能有数百个 .md，逐行全部组合进
                // 非懒加载的滚动 Column 会在扫描回调帧一次性测量，造成明显卡顿。
                // 成功项在前、失败项其后，合计最多展示 MAX_SHOWN_ROWS 行
                val maxRows = MAX_SHOWN_RESULT_ROWS
                val totalRows = uiState.successes.size + uiState.failures.size
                uiState.successes.take(maxRows).forEach { item ->
                    ScanResultRow(
                        fileName = item.fileName,
                        detail = item.date.toString(),
                        success = true,
                    )
                }
                val failureBudget = (maxRows - uiState.successes.size).coerceAtLeast(0)
                uiState.failures.take(failureBudget).forEach { item ->
                    ScanResultRow(
                        fileName = item.fileName,
                        detail = item.reason,
                        success = false,
                    )
                }
                val shownRows = minOf(uiState.successes.size, maxRows) +
                    minOf(uiState.failures.size, failureBudget)
                if (totalRows > shownRows) {
                    Text(
                        "…其余 ${totalRows - shownRows} 条省略（导入不受影响）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    )
                }
            }

            Button(
                onClick = onImport,
                enabled = uiState.successes.isNotEmpty() && !uiState.isImporting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (uiState.isImporting) {
                        "导入中…"
                    } else {
                        "导入 ${uiState.successes.size} 条记录"
                    }
                )
            }
        }
        SectionLabel("导出")
        FitLogCard {
            Text("导出全部训练记录", style = MaterialTheme.typography.titleMedium)
            Text(
                "将所有训练（含结构化明细与导入存档）合并为一个 Markdown 文件，" +
                    "可在任意编辑器查看或长期备份",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = { exportLauncher.launch(MarkdownExporter.suggestedFileName()) },
                enabled = !uiState.isExporting,
            ) {
                Text(if (uiState.isExporting) "导出中…" else "选择位置并导出")
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }

    // 一次性提示（导入结果 / 扫描失败 / 导出结果等）
    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            stackedSnackbarHostState.showSnackbar(it)
            onMessageShown()
        }
    }
}

/** 扫描结果行：文件名 + 日期/失败原因 + 状态图标。 */
@Composable
private fun ScanResultRow(fileName: String, detail: String, success: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(fileName, style = MaterialTheme.typography.bodyMedium)
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = if (success) Icons.Default.Check else Icons.Default.Close,
            contentDescription = null,
            tint = if (success) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            },
        )
    }
}

/**
 * 3. 预览层
 */
@Preview(showBackground = true)
@Composable
private fun DataImportScreenPreview() {
    DataImportScreen(
        uiState = DataImportUiState(
            successes = listOf(
                MarkdownFileScanner.ScannedMarkdown(
                    fileName = "2026-05-07.md",
                    date = LocalDate.of(2026, 5, 7),
                    content = "",
                    sourceKey = "2026-05-07.md",
                ),
            ),
            failures = listOf(
                MarkdownFileScanner.Failure("notes.md", "文件名日期解析失败"),
            ),
        ),
        onBack = {},
        onFolderSelected = {},
        onImport = {},
        onExportTargetSelected = {},
        onMessageShown = {},
    )
}
