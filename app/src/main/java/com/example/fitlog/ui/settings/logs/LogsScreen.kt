package com.example.fitlog.ui.settings.logs

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fitlog.ui.components.FitLogCard
import com.example.fitlog.ui.theme.FitLogTheme
import com.example.fitlog.ui.theme.fitLogColors
import com.example.fitlog.util.log.LogEntry
import com.example.fitlog.util.log.LogLevel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 1. 容器层 (Stateful)
 *
 * 绑定 [LogsViewModel]，接线 SAF 导出建档器与一次性提示 Snackbar。
 */
@Composable
fun LogsRoute(
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val viewModel: LogsViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onMessageShown()
        }
    }

    // SAF 建档导出（合并全部日志文件为单个 txt）
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        uri?.let(viewModel::onExportTargetSelected)
    }

    // 清空确认弹窗是 UI 瞬态（语义同导入工作台的破坏性操作确认）
    var showClearDialog by remember { mutableStateOf(false) }
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("清空全部日志？") },
            text = { Text("将删除设备上保留的全部日志文件（最近 7 天），操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    showClearDialog = false
                    viewModel.onClearLogs()
                }) { Text("清空") }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("取消") }
            },
        )
    }

    LogsScreen(
        uiState = uiState,
        onBack = onBack,
        onQueryChange = viewModel::onQueryChange,
        onLevelSelected = viewModel::onLevelSelected,
        onExport = { exportLauncher.launch("fitlog-logs.txt") },
        onClear = { showClearDialog = true },
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

/**
 * 2. 纯 UI 展示层 (Stateless)
 *
 * 与设置页群的 [com.example.fitlog.ui.components.CollapsingTitleScaffold]
 * 刻意不同型：日志条目可达 1000 条，折叠标题的整页滚动 Column 会全量组合
 * 节点；本页改用 [LazyColumn] 惰性组合 + 常驻 [TopAppBar]（颜色/排版仍与
 * 设置页群一致）。
 *
 * 结构：顶栏（导出/清空动作）→ 搜索框 + 级别筛选行 + 命中计数 → 日志列表
 * （最新在前，条目点按展开完整堆栈）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(
    uiState: LogsUiState,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onLevelSelected: (LogLevel?) -> Unit,
    onExport: () -> Unit,
    onClear: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.fitLogColors.pageBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Logs") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (uiState.isExporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            strokeWidth = 2.5.dp,
                        )
                    } else {
                        IconButton(onClick = onExport) {
                            Icon(Icons.Outlined.FileDownload, contentDescription = "导出日志")
                        }
                        IconButton(onClick = onClear) {
                            Icon(Icons.Outlined.DeleteSweep, contentDescription = "清空日志")
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        ) {
            OutlinedTextField(
                value = uiState.query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("搜索 tag / 消息 / 堆栈") },
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = uiState.minLevel == null,
                    onClick = { onLevelSelected(null) },
                    label = { Text("全部") },
                )
                FilterChip(
                    selected = uiState.minLevel == LogLevel.DEBUG,
                    onClick = { onLevelSelected(LogLevel.DEBUG) },
                    label = { Text("调试") },
                )
                FilterChip(
                    selected = uiState.minLevel == LogLevel.INFO,
                    onClick = { onLevelSelected(LogLevel.INFO) },
                    label = { Text("信息") },
                )
                FilterChip(
                    selected = uiState.minLevel == LogLevel.WARN,
                    onClick = { onLevelSelected(LogLevel.WARN) },
                    label = { Text("警告") },
                )
                FilterChip(
                    selected = uiState.minLevel == LogLevel.ERROR,
                    onClick = { onLevelSelected(LogLevel.ERROR) },
                    label = { Text("错误") },
                )
            }

            Text(
                text = if (uiState.entries.size == uiState.totalEntries) {
                    "${uiState.entries.size} 条"
                } else {
                    "${uiState.entries.size} / ${uiState.totalEntries} 条"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            FitLogCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                if (uiState.entries.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "暂无日志",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        // 稳定 key（进程内唯一自增序号）：新日志行插入会让纯位置索引
                        // 的行整体移位，展开态因行号变化丢失；timeMillis+message 作 key
                        // 会因同毫秒重复日志碰撞而崩溃，seq 由 FitLog 构造时分配保证唯一
                        itemsIndexed(
                            uiState.entries,
                            key = { _, entry -> entry.seq },
                        ) { index, entry ->
                            LogEntryItem(entry = entry)
                            if (index < uiState.entries.lastIndex) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                    thickness = 1.dp,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * 单条日志项：级别徽标 + tag + 时间 + 消息（等宽字体），点按展开/收起完整堆栈。
 *
 * @param entry 日志条目
 */
@Composable
private fun LogEntryItem(entry: LogEntry) {
    var expanded by rememberSaveable(entry.seq) { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = entry.stackTrace != null) { expanded = !expanded }
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "[${entry.level.label}]",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = levelColor(entry.level),
            )
            Text(
                text = entry.tag,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
            )
            Text(
                text = TIME_FORMAT.format(
                    Instant.ofEpochMilli(entry.timeMillis).atZone(ZoneId.systemDefault()),
                ),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = entry.message,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
            // 堆栈存在时点按切换展开；无堆栈的纯文本条目保持完整展示
            maxLines = if (expanded) Int.MAX_VALUE else if (entry.stackTrace != null) 3 else 8,
            overflow = TextOverflow.Ellipsis,
        )
        entry.stackTrace?.let { stack ->
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stack,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (expanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    )
                    .padding(8.dp),
            )
        }
    }
}

/** 级别配色：调试弱化、信息主色、警告三级、错误红色。 */
@Composable
private fun levelColor(level: LogLevel) = when (level) {
    LogLevel.DEBUG -> MaterialTheme.colorScheme.onSurfaceVariant
    LogLevel.INFO -> MaterialTheme.colorScheme.primary
    LogLevel.WARN -> MaterialTheme.colorScheme.tertiary
    LogLevel.ERROR -> MaterialTheme.colorScheme.error
}

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss.SSS")

/**
 * 3. 预览层
 */
@Preview(name = "Logs 空态", showBackground = true)
@Composable
private fun LogsScreenEmptyPreview() {
    FitLogTheme {
        LogsScreen(
            uiState = LogsUiState(),
            onBack = {},
            onQueryChange = {},
            onLevelSelected = {},
            onExport = {},
            onClear = {},
            snackbarHostState = remember { SnackbarHostState() },
        )
    }
}
