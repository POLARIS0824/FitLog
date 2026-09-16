package com.example.fitlog.ui.settings.dataimport

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fitlog.data.file.MarkdownFileScanner
import com.example.fitlog.model.BodyPart
import com.example.fitlog.model.Exercise
import com.example.fitlog.model.Muscle
import com.example.fitlog.model.SetType
import com.example.fitlog.ui.components.StackedSnackbarHost
import com.example.fitlog.ui.components.rememberStackedSnackbarHostState
import com.example.fitlog.ui.theme.FitLogTheme
import com.example.fitlog.ui.theme.fitLogColors
import com.example.fitlog.util.VolumeFormatter
import com.example.fitlog.util.findActivity
import java.time.LocalDate

/**
 * 1. 容器层 (Stateful)
 *
 * 绑定 Activity 作用域的 [DataImportViewModel]，与设置入口页无缝共享扫描与解析流水线状态。
 */
@Composable
fun ImportReviewRoute(
    onBack: () -> Unit = {},
    onNavigateToAiSettings: () -> Unit = {},
    onNavigateToToday: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val activity = LocalContext.current.findActivity()
    val viewModel: DataImportViewModel = if (activity != null) hiltViewModel(activity) else hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val editCallbacks = ImportEditCallbacks(
        onFeelingsChange = viewModel::onDraftFeelingsChange,
        onExerciseNameChange = viewModel::onDraftExerciseNameChange,
        onSetChange = viewModel::onDraftSetChange,
        onToggleSetType = viewModel::onToggleDraftSetType,
        onRemoveSet = viewModel::onRemoveDraftSet,
        onAddSet = viewModel::onAddDraftSet,
        onRemoveExercise = viewModel::onRemoveDraftExercise,
        onAddExercise = viewModel::onAddDraftExercise,
        onSave = viewModel::onSaveEdit,
        onDismiss = viewModel::onDismissEdit,
    )

    ImportReviewScreen(
        uiState = uiState,
        onBack = onBack,
        onNavigateToAiSettings = onNavigateToAiSettings,
        onNavigateToToday = onNavigateToToday,
        onSelectFilter = viewModel::onSelectFilter,
        onToggleItemChecked = viewModel::onToggleItemChecked,
        onSelectAll = viewModel::onSelectAll,
        onDeselectAll = viewModel::onDeselectAll,
        onParse = viewModel::onParse,
        onRetryFailed = viewModel::onRetryFailed,
        onCancelParse = viewModel::onCancelParse,
        onToggleParseProgressHidden = viewModel::onToggleParseProgressHidden,
        onAiNotConfiguredDismiss = viewModel::onAiNotConfiguredDismiss,
        onStartEdit = viewModel::onStartEdit,
        onConfirmImport = viewModel::onConfirmImport,
        onDismissResultSummary = viewModel::onDismissResultSummary,
        editCallbacks = editCallbacks,
        onMessageShown = viewModel::onMessageShown,
        modifier = modifier,
    )
}

/**
 * 2. 纯 UI 展示层 (Stateless)
 *
 * 导入审核独立工作台（遵循 Material 3 Expressive 设计规范）：
 * - 顶部：M3E TopAppBar（带返回、层级标题与快捷全选/清空）
 * - 吸顶：M3E 风格横向过滤 Chips（集成数值徽标与选中态）
 * - 进度卡：M3E 表达型卡片与圆角进度条
 * - 列表：卡片化条目流（Card Stream），采用 16dp 圆角与微交互边框反馈
 * - 吸底：M3E 24dp 顶圆角常驻操作栏，配合胶囊动作按钮与即时状态统计
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ImportReviewScreen(
    uiState: DataImportUiState,
    onBack: () -> Unit,
    onNavigateToAiSettings: () -> Unit,
    onNavigateToToday: () -> Unit,
    onSelectFilter: (ImportFilterCategory) -> Unit,
    onToggleItemChecked: (String) -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onParse: () -> Unit,
    onRetryFailed: () -> Unit,
    onCancelParse: () -> Unit,
    onToggleParseProgressHidden: () -> Unit,
    onAiNotConfiguredDismiss: () -> Unit,
    onStartEdit: (String) -> Unit,
    onConfirmImport: () -> Unit,
    onDismissResultSummary: () -> Unit,
    editCallbacks: ImportEditCallbacks,
    onMessageShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val stackedSnackbarHostState = rememberStackedSnackbarHostState()
    val expandedKeys = remember { mutableStateListOf<String>() }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "导入审核",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        )
                        val totalFiles = uiState.items.size + uiState.failures.size
                        Text(
                            text = "共 $totalFiles 篇日志 · 已选 ${uiState.checkedCount} 项",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = onSelectAll,
                        enabled = uiState.items.any {
                            it.status == ImportItemStatus.PARSED || it.status == ImportItemStatus.FAILED
                        },
                    ) {
                        Text("全选", style = MaterialTheme.typography.labelLarge)
                    }
                    TextButton(
                        onClick = onDeselectAll,
                        enabled = uiState.checkedCount > 0,
                    ) {
                        Text("清空", style = MaterialTheme.typography.labelLarge)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.fitLogColors.pageBackground,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
        bottomBar = {
            ImportReviewBottomBar(
                uiState = uiState,
                onParse = onParse,
                onRetryFailed = onRetryFailed,
                onConfirmImport = onConfirmImport,
            )
        },
        snackbarHost = { StackedSnackbarHost(hostState = stackedSnackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // 1. 置顶常驻：AI 解析进度卡片（随滚动常驻吸顶可见）
            AnimatedVisibility(
                visible = uiState.isParsing,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                ) {
                    if (uiState.isParseProgressHidden) {
                        ParseProgressCompactRow(
                            uiState = uiState,
                            onToggleParseProgressHidden = onToggleParseProgressHidden,
                            onCancelParse = onCancelParse,
                        )
                    } else {
                        ParseProgressRow(
                            uiState = uiState,
                            onToggleParseProgressHidden = onToggleParseProgressHidden,
                            onCancelParse = onCancelParse,
                        )
                    }
                }
            }

            // 2. 固顶常驻：分类过滤栏（完全脱离列表滚动与 Overscroll 弹性拉伸，彻底稳固在顶部）
            ImportFilterChipsBar(
                uiState = uiState,
                onSelectFilter = onSelectFilter,
            )

            // 3. 虚拟化滚动列表
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
            ) {

                // 空态提示
                val filteredItems = uiState.filteredItems
                val filteredFailures = uiState.filteredFailures
                if (filteredItems.isEmpty() && filteredFailures.isEmpty()) {
                    item {
                        ImportReviewEmptyState()
                    }
                }

                // 导入项卡片列表
                items(
                    items = filteredItems,
                    key = { it.sourceKey },
                ) { item ->
                    ImportItemRow(
                        item = item,
                        expanded = item.sourceKey in expandedKeys,
                        onToggleExpand = {
                            if (item.sourceKey in expandedKeys) {
                                expandedKeys.remove(item.sourceKey)
                            } else {
                                expandedKeys.add(item.sourceKey)
                            }
                        },
                        onToggleChecked = { onToggleItemChecked(item.sourceKey) },
                        onEdit = { onStartEdit(item.sourceKey) },
                    )
                }

                // 扫描失败条目卡片
                items(
                    items = filteredFailures,
                    key = { it.fileName },
                ) { failure ->
                    ScanFailureRow(failure = failure)
                }
            }
        }
    }

    // 结算完成弹窗（展示导入结果并提供导航）
    uiState.lastResultSummary?.let { summary ->
        AlertDialog(
            onDismissRequest = onDismissResultSummary,
            icon = {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
            },
            shape = RoundedCornerShape(28.dp),
            title = {
                Text(
                    "导入完成",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "共处理 ${summary.totalProcessed} 篇训练日志：",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    )
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            if (summary.imported > 0) {
                                Text("• 新增训练记录：${summary.imported} 条")
                            }
                            if (summary.upgraded > 0) {
                                Text("• 升级空明细存档：${summary.upgraded} 条")
                            }
                            if (summary.archived > 0) {
                                Text("• 仅存档记录：${summary.archived} 条")
                            }
                            if (summary.skipped > 0) {
                                Text("• 跳过（已存在）：${summary.skipped} 条")
                            }
                            if (summary.invalid > 0) {
                                Text(
                                    "• 无效条目（请修改）：${summary.invalid} 条",
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDismissResultSummary()
                        onNavigateToToday()
                    },
                    shape = CircleShape,
                ) {
                    Text("查看训练主页")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismissResultSummary,
                    shape = CircleShape,
                ) {
                    Text("留在工作台")
                }
            },
        )
    }

    // AI 未配置引导弹窗
    uiState.aiNotConfiguredMessage?.let { message ->
        AlertDialog(
            onDismissRequest = onAiNotConfiguredDismiss,
            icon = {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
            },
            shape = RoundedCornerShape(28.dp),
            title = {
                Text(
                    "AI 未配置",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                )
            },
            text = { Text(message) },
            confirmButton = {
                Button(
                    onClick = {
                        onAiNotConfiguredDismiss()
                        onNavigateToAiSettings()
                    },
                    shape = CircleShape,
                ) {
                    Text("去配置")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onAiNotConfiguredDismiss,
                    shape = CircleShape,
                ) {
                    Text("取消")
                }
            },
        )
    }

    // 编辑弹层
    val editingItem = uiState.editingSourceKey
        ?.let { key -> uiState.items.firstOrNull { it.sourceKey == key } }
    if (editingItem != null && editingItem.draft != null) {
        if (LocalInspectionMode.current) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f)),
                contentAlignment = Alignment.BottomCenter,
            ) {
                ImportEditSheet(
                    title = "${editingItem.fileName} · ${editingItem.date}",
                    draft = editingItem.draft,
                    catalog = uiState.exerciseCatalog,
                    callbacks = editCallbacks,
                )
            }
        } else {
            ImportEditSheet(
                title = "${editingItem.fileName} · ${editingItem.date}",
                draft = editingItem.draft,
                catalog = uiState.exerciseCatalog,
                callbacks = editCallbacks,
            )
        }
    }

    // 一次性提示展示
    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            stackedSnackbarHostState.showSnackbar(it)
            onMessageShown()
        }
    }
}

/**
 * 粘性吸顶状态过滤栏（M3 Expressive 风格，集成胶囊数值徽标）。
 */
@Composable
private fun ImportFilterChipsBar(
    uiState: DataImportUiState,
    onSelectFilter: (ImportFilterCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.fitLogColors.pageBackground,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ImportFilterCategory.entries.forEach { category ->
                    val count = when (category) {
                        ImportFilterCategory.ALL -> uiState.items.size + uiState.failures.size
                        ImportFilterCategory.READY -> uiState.readyCount
                        ImportFilterCategory.PENDING -> uiState.pendingCount
                        ImportFilterCategory.ALREADY -> uiState.alreadyCount
                        ImportFilterCategory.FAILED -> uiState.failedCount + uiState.failures.size
                    }
                    val selected = uiState.selectedFilter == category
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectFilter(category) },
                        shape = CircleShape,
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selected,
                            borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            selectedBorderColor = Color.Transparent,
                        ),
                        leadingIcon = if (selected) {
                            {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        } else {
                            null
                        },
                        label = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(category.label, style = MaterialTheme.typography.labelMedium)
                                Surface(
                                    shape = CircleShape,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.16f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHighest
                                    },
                                ) {
                                    Text(
                                        text = "$count",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.onSecondaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                            }
                        },
                    )
                }
            }
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
            )
        }
    }
}

/**
 * 工作台常驻吸底操作栏（M3 Expressive 风格，带吸附式自适应按钮组）。
 */
@Composable
private fun ImportReviewBottomBar(
    uiState: DataImportUiState,
    onParse: () -> Unit,
    onRetryFailed: () -> Unit,
    onConfirmImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val failedChecked = uiState.items.count { it.checked && it.status == ImportItemStatus.FAILED }
    val showParseButton = uiState.pendingCount > 0 && !uiState.isParsing
    val showRetryButton = uiState.failedCount > 0 && !uiState.isParsing && uiState.pendingCount == 0
    val hasSecondaryButton = showParseButton || showRetryButton

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 上层：已选项统计与就绪状态信息栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "已选 ${uiState.checkedCount} / ${uiState.items.size} 项",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    )
                    Surface(
                        shape = CircleShape,
                        color = if (failedChecked > 0) {
                            MaterialTheme.colorScheme.tertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        },
                    ) {
                        Text(
                            text = if (failedChecked > 0) "含 $failedChecked 项仅存档" else "全明细入库",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                            color = if (failedChecked > 0) {
                                MaterialTheme.colorScheme.onTertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }

                if (uiState.readyCount > 0) {
                    Text(
                        text = "${uiState.readyCount} 篇就绪",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            // 下层：自适应吸附按钮组（Connected Button Group）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (hasSecondaryButton) {
                    // 左侧按钮（主导动作或重试动作：左侧 24dp 全圆角、右侧 4dp 微圆角中缝吸附）
                    if (showParseButton) {
                        Button(
                            onClick = onParse,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(
                                topStart = 24.dp,
                                bottomStart = 24.dp,
                                topEnd = 4.dp,
                                bottomEnd = 4.dp,
                            ),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "AI 解析 (${uiState.pendingCount})",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    } else if (showRetryButton) {
                        Button(
                            onClick = onRetryFailed,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(
                                topStart = 24.dp,
                                bottomStart = 24.dp,
                                topEnd = 4.dp,
                                bottomEnd = 4.dp,
                            ),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "重试失败 (${uiState.failedCount})",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    // 右侧导入按钮（左侧 4dp 微圆角中缝吸附、右侧 24dp 全圆角）
                    Button(
                        onClick = onConfirmImport,
                        enabled = uiState.checkedCount > 0 && !uiState.isImporting && !uiState.isParsing,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(
                            topStart = 4.dp,
                            bottomStart = 4.dp,
                            topEnd = 24.dp,
                            bottomEnd = 24.dp,
                        ),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                    ) {
                        if (uiState.isImporting) {
                            if (LocalInspectionMode.current) {
                                CircularProgressIndicator(
                                    progress = { 0.7f },
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            } else {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("导入中…", style = MaterialTheme.typography.labelLarge)
                        } else {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "确认导入 (${uiState.checkedCount})",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                } else {
                    // 仅单个操作时：全宽胶囊按钮（CircleShape）
                    Button(
                        onClick = onConfirmImport,
                        enabled = uiState.checkedCount > 0 && !uiState.isImporting && !uiState.isParsing,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                    ) {
                        if (uiState.isImporting) {
                            if (LocalInspectionMode.current) {
                                CircularProgressIndicator(
                                    progress = { 0.7f },
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                            } else {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("导入中…", style = MaterialTheme.typography.labelLarge)
                        } else {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "确认导入 (${uiState.checkedCount})",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 展开态的解析进度区（M3 Expressive）。 */
@Composable
private fun ParseProgressRow(
    uiState: DataImportUiState,
    onToggleParseProgressHidden: () -> Unit,
    onCancelParse: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "AI 智能解析进行中",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "已完成 ${uiState.parseCompleted} / ${uiState.parseTotal} 篇",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onToggleParseProgressHidden) {
                Text("收起", style = MaterialTheme.typography.labelMedium)
            }
            TextButton(onClick = onCancelParse) {
                Text(
                    "取消",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        LinearProgressIndicator(
            progress = {
                if (uiState.parseTotal > 0) {
                    uiState.parseCompleted.toFloat() / uiState.parseTotal
                } else {
                    0f
                }
            },
            strokeCap = StrokeCap.Round,
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape),
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
    }
}

/** 收起态的解析进度（M3 Expressive）。 */
@Composable
private fun ParseProgressCompactRow(
    uiState: DataImportUiState,
    onToggleParseProgressHidden: () -> Unit,
    onCancelParse: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        if (LocalInspectionMode.current) {
            CircularProgressIndicator(
                progress = { 0.7f },
                modifier = Modifier
                    .padding(end = 12.dp)
                    .size(18.dp),
                strokeWidth = 2.5.dp,
            )
        } else {
            CircularProgressIndicator(
                modifier = Modifier
                    .padding(end = 12.dp)
                    .size(18.dp),
                strokeWidth = 2.5.dp,
            )
        }
        Text(
            text = "AI 解析中 (${uiState.parseCompleted}/${uiState.parseTotal})",
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onToggleParseProgressHidden) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "展开解析进度",
            )
        }
        IconButton(onClick = onCancelParse) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "取消解析",
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/**
 * 单条导入项行（遵循 Material 3 Expressive 卡片化列表项规范）。
 */
@Composable
internal fun ImportItemRow(
    item: ImportItemState,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onToggleChecked: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isSelectable = item.status == ImportItemStatus.PARSED || item.status == ImportItemStatus.FAILED

    val animatedContainerColor by animateColorAsState(
        targetValue = if (item.checked) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        label = "itemContainerColor",
    )

    val animatedBorderColor by animateColorAsState(
        targetValue = if (item.checked) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
        } else {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        },
        label = "itemBorderColor",
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .then(
                if (isSelectable) {
                    Modifier.clickable(onClick = onToggleChecked)
                } else {
                    Modifier
                }
            ),
        shape = RoundedCornerShape(16.dp),
        color = animatedContainerColor,
        border = BorderStroke(
            width = if (item.checked) 1.5.dp else 1.dp,
            color = animatedBorderColor,
        ),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                leadingContent = {
                    when (item.status) {
                        ImportItemStatus.PARSED, ImportItemStatus.FAILED -> {
                            Checkbox(
                                checked = item.checked,
                                onCheckedChange = { onToggleChecked() },
                            )
                        }

                        ImportItemStatus.PARSING -> {
                            Box(
                                modifier = Modifier.size(40.dp),
                                contentAlignment = Alignment.Center,
                            ) {
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
                            }
                        }

                        ImportItemStatus.IMPORTED -> {
                            StatusBadge(
                                icon = Icons.Default.Check,
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                description = "已导入",
                            )
                        }

                        ImportItemStatus.ARCHIVED -> {
                            StatusBadge(
                                icon = Icons.Default.Archive,
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                description = "已存档",
                            )
                        }

                        ImportItemStatus.ALREADY_IMPORTED -> {
                            StatusBadge(
                                icon = Icons.Default.Check,
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                description = "已导入过",
                            )
                        }

                        ImportItemStatus.PENDING -> {
                            Box(
                                modifier = Modifier.size(40.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Surface(
                                    modifier = Modifier.size(10.dp),
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                ) {}
                            }
                        }
                    }
                },
                content = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = item.fileName,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        ItemStatusPill(status = item.status)
                    }
                },
                supportingContent = {
                    Column(
                        modifier = Modifier.padding(top = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (item.status == ImportItemStatus.PARSED && item.draft != null) {
                            val draft = item.draft
                            val totalSets = draft.exercises.sumOf { it.sets.size }
                            val volume = draft.exercises.sumOf { ex ->
                                ex.sets.filter { it.setType == SetType.WORKING }
                                    .sumOf { (it.weightKg * it.reps).toDouble() }
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                MetricChip(text = "${draft.exercises.size} 动作")
                                MetricChip(text = "$totalSets 组")
                                if (volume > 0.0) {
                                    MetricChip(
                                        text = VolumeFormatter.formatVolume(volume),
                                        highlight = true,
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = detailTextOf(item),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (item.status == ImportItemStatus.FAILED) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                trailingContent = {
                    if (item.status == ImportItemStatus.PARSED && item.draft != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            if (expanded) {
                                FilledTonalButton(
                                    onClick = onEdit,
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                    modifier = Modifier.height(32.dp),
                                    shape = CircleShape,
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("编辑", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            IconButton(onClick = onToggleExpand) {
                                val rotation by animateFloatAsState(
                                    targetValue = if (expanded) 180f else 0f,
                                    label = "chevronRotation",
                                )
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = if (expanded) "收起明细" else "展开明细",
                                    modifier = Modifier.rotate(rotation),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                },
            )

            // 展开的草稿明细（带平滑展开与收起动效）
            AnimatedVisibility(
                visible = expanded && item.status == ImportItemStatus.PARSED && item.draft != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                val draft = item.draft
                if (draft != null) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.45f),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            if (draft.feelings.isNotBlank()) {
                                Text(
                                    text = "心得：${draft.feelings}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            draft.exercises.forEach { exercise ->
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.FitnessCenter,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = exercise.name,
                                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        if (exercise.exerciseKey == null) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                                                modifier = Modifier.padding(start = 6.dp),
                                            ) {
                                                Text(
                                                    text = "未关联动作库",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                                )
                                            }
                                        }
                                    }
                                    exercise.sets.forEachIndexed { index, set ->
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier.padding(start = 22.dp),
                                        ) {
                                            Surface(
                                                shape = CircleShape,
                                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                                modifier = Modifier.size(18.dp),
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(
                                                        text = "${index + 1}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                }
                                            }
                                            if (set.setType == SetType.WARMUP) {
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                                                ) {
                                                    Text(
                                                        text = "热身",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                                    )
                                                }
                                            }
                                            Text(
                                                text = if (set.weightKg > 0f) {
                                                    "${VolumeFormatter.formatWeightKg(set.weightKg)} kg × ${set.reps} 次"
                                                } else {
                                                    "${set.reps} 次（自重）"
                                                },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * M3 Expressive 风格的条目状态胶囊徽标。
 */
@Composable
private fun ItemStatusPill(
    status: ImportItemStatus,
    modifier: Modifier = Modifier,
) {
    val (bgColor, textColor, text) = when (status) {
        ImportItemStatus.PARSED -> Triple(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            "已就绪",
        )
        ImportItemStatus.FAILED -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            "解析失败",
        )
        ImportItemStatus.IMPORTED -> Triple(
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
            "已入库",
        )
        ImportItemStatus.ARCHIVED -> Triple(
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
            "已存档",
        )
        ImportItemStatus.ALREADY_IMPORTED -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            "已存在",
        )
        ImportItemStatus.PENDING -> Triple(
            MaterialTheme.colorScheme.surfaceContainerHighest,
            MaterialTheme.colorScheme.onSurfaceVariant,
            "待解析",
        )
        ImportItemStatus.PARSING -> Triple(
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
            MaterialTheme.colorScheme.primary,
            "解析中",
        )
    }

    Surface(
        shape = CircleShape,
        color = bgColor,
        modifier = modifier,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
            color = textColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

/**
 * M3 Expressive 风格的数据指示小胶囊（用于动作数、组数、容量等）。
 */
@Composable
private fun MetricChip(
    text: String,
    highlight: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val containerColor = if (highlight) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f)
    }
    val contentColor = if (highlight) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = containerColor,
        modifier = modifier,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
            color = contentColor,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/** 状态图标徽标（M3 Expressive 圆角容器）。 */
@Composable
internal fun StatusBadge(
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    description: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.size(36.dp),
        shape = RoundedCornerShape(10.dp),
        color = containerColor,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                tint = contentColor,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** 扫描失败行（M3 Expressive 卡片风格）。 */
@Composable
internal fun ScanFailureRow(
    failure: MarkdownFileScanner.Failure,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = {
                StatusBadge(
                    icon = Icons.Default.Close,
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    description = "扫描失败",
                )
            },
            content = {
                Text(
                    text = failure.fileName,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            supportingContent = {
                Text(
                    text = failure.reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            },
        )
    }
}

/** 空状态占位视图（M3 Expressive 风格）。 */
@Composable
private fun ImportReviewEmptyState(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 64.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                modifier = Modifier.size(72.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Inbox,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    )
                }
            }
            Text(
                text = "暂无匹配日志",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "当前分类下没有训练日志，可切换上方分类或重新选择文件夹扫描",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** 单条状态副文案。 */
private fun detailTextOf(item: ImportItemState): String = when (item.status) {
    ImportItemStatus.PENDING -> "待 AI 解析"
    ImportItemStatus.PARSING -> "正在调用 AI 解析…"
    ImportItemStatus.PARSED -> {
        val draft = item.draft
        if (draft == null || draft.validExerciseCount == 0) {
            "未解析出有效动作"
        } else {
            val totalSets = draft.exercises.sumOf { it.sets.size }
            val volume = draft.exercises.sumOf { ex ->
                ex.sets.filter { it.setType == SetType.WORKING }
                    .sumOf { (it.weightKg * it.reps).toDouble() }
            }
            val volumeText = if (volume > 0.0) " · ${VolumeFormatter.formatVolume(volume)}" else ""
            "${draft.exercises.size} 个动作 · $totalSets 组$volumeText"
        }
    }

    ImportItemStatus.FAILED -> "解析失败：${item.parseError ?: "未知错误"}（勾选可仅存档原文）"
    ImportItemStatus.ALREADY_IMPORTED -> "已存在同名记录（exercises 非空），跳过"
    ImportItemStatus.IMPORTED -> "已成功导入完整记录"
    ImportItemStatus.ARCHIVED -> "已作为纯文本存档导入（无动作明细）"
}

// ── Previews ──

private val previewExercises = listOf(
    Exercise(
        id = "bench-press",
        name = "杠铃卧推",
        bodyPart = BodyPart.CHEST,
        primaryMuscles = listOf(Muscle.CHEST),
    ),
    Exercise(
        id = "pull-up",
        name = "引体向上",
        bodyPart = BodyPart.BACK,
        primaryMuscles = listOf(Muscle.LATS),
    ),
)

private val previewDraft = ImportDraftWorkout(
    feelings = "状态拉满，卧推轻松",
    exercises = listOf(
        ImportDraftExercise(
            localId = 1L,
            name = "杠铃卧推",
            exerciseKey = "bench-press",
            sets = listOf(
                ImportDraftSet(101L, 60f, 12, SetType.WARMUP),
                ImportDraftSet(102L, 80f, 8, SetType.WORKING),
            ),
        ),
    ),
)

private val previewWorkbenchState = DataImportUiState(
    items = listOf(
        ImportItemState(
            sourceKey = "2026-05-07.md",
            fileName = "2026-05-07.md",
            date = LocalDate.of(2026, 5, 7),
            status = ImportItemStatus.PARSED,
            draft = previewDraft,
            checked = true,
        ),
        ImportItemState(
            sourceKey = "2026-05-06.md",
            fileName = "2026-05-06.md",
            date = LocalDate.of(2026, 5, 6),
            status = ImportItemStatus.PENDING,
        ),
        ImportItemState(
            sourceKey = "2026-05-05.md",
            fileName = "2026-05-05.md",
            date = LocalDate.of(2026, 5, 5),
            status = ImportItemStatus.FAILED,
            parseError = "网络请求超时",
        ),
    ),
    failures = listOf(
        MarkdownFileScanner.Failure(
            fileName = "invalid-name.md",
            reason = "文件名需为 YYYY-MM-DD.md 日期格式",
        ),
    ),
    exerciseCatalog = previewExercises,
)

private val emptyEditCallbacks = ImportEditCallbacks(
    onFeelingsChange = {},
    onExerciseNameChange = { _, _ -> },
    onSetChange = { _, _, _, _ -> },
    onToggleSetType = { _, _ -> },
    onRemoveSet = { _, _ -> },
    onAddSet = {},
    onRemoveExercise = {},
    onAddExercise = {},
    onSave = {},
    onDismiss = {},
)

@Preview(name = "混合状态 (默认)", showBackground = true)
@Composable
private fun ImportReviewScreenDefaultPreview() {
    FitLogTheme(dynamicColor = false) {
        ImportReviewScreen(
            uiState = previewWorkbenchState,
            onBack = {},
            onNavigateToAiSettings = {},
            onNavigateToToday = {},
            onSelectFilter = {},
            onToggleItemChecked = {},
            onSelectAll = {},
            onDeselectAll = {},
            onParse = {},
            onRetryFailed = {},
            onCancelParse = {},
            onToggleParseProgressHidden = {},
            onAiNotConfiguredDismiss = {},
            onStartEdit = {},
            onConfirmImport = {},
            onDismissResultSummary = {},
            editCallbacks = emptyEditCallbacks,
            onMessageShown = {},
        )
    }
}

@Preview(name = "AI 解析中", showBackground = true)
@Composable
private fun ImportReviewScreenParsingPreview() {
    FitLogTheme(dynamicColor = false) {
        ImportReviewScreen(
            uiState = previewWorkbenchState.copy(
                isParsing = true,
                parseCompleted = 1,
                parseTotal = 3,
            ),
            onBack = {},
            onNavigateToAiSettings = {},
            onNavigateToToday = {},
            onSelectFilter = {},
            onToggleItemChecked = {},
            onSelectAll = {},
            onDeselectAll = {},
            onParse = {},
            onRetryFailed = {},
            onCancelParse = {},
            onToggleParseProgressHidden = {},
            onAiNotConfiguredDismiss = {},
            onStartEdit = {},
            onConfirmImport = {},
            onDismissResultSummary = {},
            editCallbacks = emptyEditCallbacks,
            onMessageShown = {},
        )
    }
}

@Preview(name = "全部就绪待导入", showBackground = true)
@Composable
private fun ImportReviewScreenAllReadyPreview() {
    FitLogTheme(dynamicColor = false) {
        ImportReviewScreen(
            uiState = previewWorkbenchState.copy(
                items = previewWorkbenchState.items.map {
                    it.copy(status = ImportItemStatus.PARSED, draft = previewDraft, checked = true)
                },
                failures = emptyList(),
            ),
            onBack = {},
            onNavigateToAiSettings = {},
            onNavigateToToday = {},
            onSelectFilter = {},
            onToggleItemChecked = {},
            onSelectAll = {},
            onDeselectAll = {},
            onParse = {},
            onRetryFailed = {},
            onCancelParse = {},
            onToggleParseProgressHidden = {},
            onAiNotConfiguredDismiss = {},
            onStartEdit = {},
            onConfirmImport = {},
            onDismissResultSummary = {},
            editCallbacks = emptyEditCallbacks,
            onMessageShown = {},
        )
    }
}

@Preview(name = "仅看失败与异常", showBackground = true)
@Composable
private fun ImportReviewScreenFailedOnlyPreview() {
    FitLogTheme(dynamicColor = false) {
        ImportReviewScreen(
            uiState = previewWorkbenchState.copy(
                selectedFilter = ImportFilterCategory.FAILED,
            ),
            onBack = {},
            onNavigateToAiSettings = {},
            onNavigateToToday = {},
            onSelectFilter = {},
            onToggleItemChecked = {},
            onSelectAll = {},
            onDeselectAll = {},
            onParse = {},
            onRetryFailed = {},
            onCancelParse = {},
            onToggleParseProgressHidden = {},
            onAiNotConfiguredDismiss = {},
            onStartEdit = {},
            onConfirmImport = {},
            onDismissResultSummary = {},
            editCallbacks = emptyEditCallbacks,
            onMessageShown = {},
        )
    }
}

@Preview(name = "导入完成结算弹窗", showBackground = true)
@Composable
private fun ImportReviewScreenResultSummaryPreview() {
    FitLogTheme(dynamicColor = false) {
        ImportReviewScreen(
            uiState = previewWorkbenchState.copy(
                lastResultSummary = ImportResultSummary(
                    imported = 2,
                    upgraded = 1,
                    archived = 0,
                    skipped = 0,
                    invalid = 0,
                ),
            ),
            onBack = {},
            onNavigateToAiSettings = {},
            onNavigateToToday = {},
            onSelectFilter = {},
            onToggleItemChecked = {},
            onSelectAll = {},
            onDeselectAll = {},
            onParse = {},
            onRetryFailed = {},
            onCancelParse = {},
            onToggleParseProgressHidden = {},
            onAiNotConfiguredDismiss = {},
            onStartEdit = {},
            onConfirmImport = {},
            onDismissResultSummary = {},
            editCallbacks = emptyEditCallbacks,
            onMessageShown = {},
        )
    }
}

@Preview(name = "空态", showBackground = true)
@Composable
private fun ImportReviewScreenEmptyPreview() {
    FitLogTheme(dynamicColor = false) {
        ImportReviewScreen(
            uiState = DataImportUiState(),
            onBack = {},
            onNavigateToAiSettings = {},
            onNavigateToToday = {},
            onSelectFilter = {},
            onToggleItemChecked = {},
            onSelectAll = {},
            onDeselectAll = {},
            onParse = {},
            onRetryFailed = {},
            onCancelParse = {},
            onToggleParseProgressHidden = {},
            onAiNotConfiguredDismiss = {},
            onStartEdit = {},
            onConfirmImport = {},
            onDismissResultSummary = {},
            editCallbacks = emptyEditCallbacks,
            onMessageShown = {},
        )
    }
}
