package com.example.fitlog.ui.settings.dataimport

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import com.example.fitlog.util.findActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.example.fitlog.ui.theme.FitLogTheme
import java.time.LocalDate

/**
 * 1. 容器层 (Stateful)
 *
 * 绑定 Activity 作用域的 [DataImportViewModel]，与独立的 [ImportReviewRoute] 无缝共享状态。
 */
@Composable
fun DataImportRoute(
    onBack: () -> Unit = {},
    onNavigateToAiSettings: () -> Unit = {},
    onNavigateToReview: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val activity = LocalContext.current.findActivity()
    val viewModel: DataImportViewModel = if (activity != null) hiltViewModel(activity) else hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var justScanned by remember { mutableStateOf(false) }

    // SAF 文件夹选择器
    val folderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            justScanned = true
            viewModel.onFolderSelected(uri)
        }
    }

    // 扫描完成且有条目时，自动跳转到独立的导入审核工作台
    LaunchedEffect(uiState.isScanning, uiState.items.size) {
        if (!uiState.isScanning && justScanned && uiState.items.isNotEmpty()) {
            justScanned = false
            onNavigateToReview()
        }
    }

    // SAF 建档导出
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/markdown"),
    ) { uri ->
        uri?.let(viewModel::onExportTargetSelected)
    }

    DataImportScreen(
        uiState = uiState,
        onBack = onBack,
        onSelectFolder = { folderLauncher.launch(null) },
        onNavigateToReview = onNavigateToReview,
        onClearBatch = viewModel::onClearBatch,
        onExport = { exportLauncher.launch(MarkdownExporter.suggestedFileName()) },
        onMessageShown = viewModel::onMessageShown,
        modifier = modifier,
    )
}

/**
 * 2. 纯 UI 展示层 (Stateless)
 *
 * 数据导入导出设置页入口：
 * - 契约：继承 [CollapsingTitleScaffold] 规范的双标题与平滑折叠
 * - 导入：轻量入口卡片（选择文件夹扫描、展示当前批次概览与直达工作台链接）
 * - 导出：全量 Markdown 数据导出
 */
@Composable
fun DataImportScreen(
    uiState: DataImportUiState,
    onBack: () -> Unit,
    onSelectFolder: () -> Unit,
    onNavigateToReview: () -> Unit,
    onClearBatch: () -> Unit,
    onExport: () -> Unit,
    onMessageShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val stackedSnackbarHostState = rememberStackedSnackbarHostState()

    CollapsingTitleScaffold(
        title = "Data Import",
        onBack = onBack,
        parentTitle = "Settings",
        modifier = modifier.fillMaxSize(),
        snackbarHost = { StackedSnackbarHost(hostState = stackedSnackbarHostState) },
    ) {
        SectionLabel("说明")
        FitLogCard {
            Text("从 Markdown 导入训练日志", style = MaterialTheme.typography.titleMedium)
            Text(
                "选择存放日志的文件夹（每个文件代表一天训练，文件名需为日期格式，" +
                    "如 2026-05-07.md；扫描根目录，子文件夹不递归）。" +
                    "扫描后将自动进入导入审核工作台，调用 AI 逐条解析动作与组数明细，确认后写入数据库；" +
                    "未配置 AI 时可先在设置中配置密钥。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionLabel("导入工作台")
        FitLogCard {
            when {
                uiState.isScanning -> {
                    Text("正在扫描文件夹…", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "正在检索 Markdown 文件并校验命名与内容结构，请稍候…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (LocalInspectionMode.current) {
                            CircularProgressIndicator(
                                progress = { 0.7f },
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("扫描中…", style = MaterialTheme.typography.bodyMedium)
                    }
                }

                    uiState.hasActiveBatch -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("当前导入批次", style = MaterialTheme.typography.titleMedium)
                                val total = uiState.items.size + uiState.failures.size
                                Text(
                                    "共 $total 篇日志 · 就绪 ${uiState.readyCount} · 待解析 ${uiState.pendingCount} · 失败 ${uiState.failedCount + uiState.failures.size}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            FilledTonalButton(onClick = onNavigateToReview) {
                                Text("进入工作台")
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }

                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            modifier = Modifier.padding(vertical = 12.dp),
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedButton(onClick = onSelectFolder) {
                                Text("重新选择文件夹")
                            }
                            TextButton(onClick = onClearBatch) {
                                Text("清空批次", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }

                    else -> {
                        Text("选择日志文件夹", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "点击下方按钮选择文件夹。系统将自动扫描并跳转至全屏导入审核工作台，提供分类筛选、明细修改与批量入库。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        FilledTonalButton(onClick = onSelectFolder) {
                            Text("选择文件夹并扫描")
                        }
                    }
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
                    onClick = onExport,
                    enabled = !uiState.isExporting,
                ) {
                    Text(if (uiState.isExporting) "导出中…" else "选择位置并导出")
                }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            stackedSnackbarHostState.showSnackbar(it)
            onMessageShown()
        }
    }
}

// ── Previews ──

@Preview(showBackground = true)
@Composable
private fun DataImportScreenIdlePreview() {
    FitLogTheme(dynamicColor = false) {
        DataImportScreen(
            uiState = DataImportUiState(),
            onBack = {},
            onSelectFolder = {},
            onNavigateToReview = {},
            onClearBatch = {},
            onExport = {},
            onMessageShown = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun DataImportScreenScanningPreview() {
    FitLogTheme(dynamicColor = false) {
        DataImportScreen(
            uiState = DataImportUiState(isScanning = true),
            onBack = {},
            onSelectFolder = {},
            onNavigateToReview = {},
            onClearBatch = {},
            onExport = {},
            onMessageShown = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun DataImportScreenActiveBatchPreview() {
    FitLogTheme(dynamicColor = false) {
        DataImportScreen(
            uiState = DataImportUiState(
                items = listOf(
                    ImportItemState(
                        sourceKey = "2026-05-07.md",
                        fileName = "2026-05-07.md",
                        date = LocalDate.of(2026, 5, 7),
                        status = ImportItemStatus.PARSED,
                        checked = true,
                    ),
                    ImportItemState(
                        sourceKey = "2026-05-06.md",
                        fileName = "2026-05-06.md",
                        date = LocalDate.of(2026, 5, 6),
                        status = ImportItemStatus.PENDING,
                    ),
                ),
                failures = listOf(
                    MarkdownFileScanner.Failure(
                        fileName = "invalid.md",
                        reason = "格式不正确",
                    ),
                ),
            ),
            onBack = {},
            onSelectFolder = {},
            onNavigateToReview = {},
            onClearBatch = {},
            onExport = {},
            onMessageShown = {},
        )
    }
}
