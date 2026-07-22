package com.example.fitlog.ui.dataimport

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fitlog.data.file.MarkdownFileScanner
import com.example.fitlog.ui.components.SectionLabel
import com.example.fitlog.ui.components.SettingsCard
import java.time.LocalDate

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
        onMessageShown = viewModel::onMessageShown,
        modifier = modifier,
    )
}

/**
 * 2. 纯 UI 展示层 (Stateless)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataImportScreen(
    uiState: DataImportUiState,
    onBack: () -> Unit,
    onFolderSelected: (android.net.Uri) -> Unit,
    onImport: () -> Unit,
    onMessageShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val snackbarHostState = remember { SnackbarHostState() }

    // SAF 文件夹选择器
    val folderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let(onFolderSelected)
    }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            LargeTopAppBar(
                title = { Text("数据导入") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionLabel("说明")
            SettingsCard {
                Text("从 Markdown 导入训练日志", style = MaterialTheme.typography.titleMedium)
                Text(
                    "选择存放日志的文件夹，每个文件代表一天训练，" +
                        "文件名需为日期格式，如 2026-05-07.md",
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
                SettingsCard {
                    uiState.successes.forEach { item ->
                        ScanResultRow(
                            fileName = item.fileName,
                            detail = item.date.toString(),
                            success = true,
                        )
                    }
                    uiState.failures.forEach { item ->
                        ScanResultRow(
                            fileName = item.fileName,
                            detail = item.reason,
                            success = false,
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
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // 一次性提示（导入结果 / 扫描失败等）
    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
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
                ),
            ),
            failures = listOf(
                MarkdownFileScanner.Failure("notes.md", "文件名日期解析失败"),
            ),
        ),
        onBack = {},
        onFolderSelected = {},
        onImport = {},
        onMessageShown = {},
    )
}
