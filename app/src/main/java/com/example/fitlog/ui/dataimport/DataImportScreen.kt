package com.example.fitlog.ui.dataimport

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fitlog.R
import com.example.fitlog.data.file.MarkdownFileScanner
import com.example.fitlog.ui.components.SectionLabel
import com.example.fitlog.ui.components.SettingsCard
import com.example.fitlog.ui.components.StackedSnackbarHost
import com.example.fitlog.ui.components.rememberStackedSnackbarHostState
import kotlinx.coroutines.CancellationException
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
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val scrollState = rememberScrollState()
    val stackedSnackbarHostState = rememberStackedSnackbarHostState()

    val density = LocalDensity.current
    val extraSpacingPx = remember(density) { with(density) { 12.dp.roundToPx() } }

    val isScrollable by remember { derivedStateOf { scrollState.maxValue > 0 } }
    var headerHeightPx by remember { mutableIntStateOf(0) }
    val titleFraction by remember {
        derivedStateOf {
            if (!isScrollable || headerHeightPx <= 0) 0f
            else (scrollState.value.toFloat() / headerHeightPx.toFloat()).coerceIn(0f, 1f)
        }
    }

    LaunchedEffect(scrollState, headerHeightPx, isScrollable) {
        if (!isScrollable) return@LaunchedEffect
        snapshotFlow { scrollState.isScrollInProgress }
            .collect { inProgress ->
                if (inProgress) return@collect
                val currentScroll = scrollState.value
                if (headerHeightPx > 0 && currentScroll in 1 until headerHeightPx) {
                    val target = if (currentScroll < headerHeightPx / 2) 0 else headerHeightPx
                    try {
                        scrollState.animateScrollTo(
                            value = target,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioLowBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        )
                    } catch (e: CancellationException) {
                        // 吸附动画被用户手势打断
                    }
                }
            }
    }

    val topAppBarContainerColor = androidx.compose.ui.graphics.lerp(
        MaterialTheme.colorScheme.surfaceContainerLow,
        MaterialTheme.colorScheme.surfaceContainer,
        titleFraction
    )

    // SAF 文件夹选择器
    val folderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let(onFolderSelected)
    }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        snackbarHost = { StackedSnackbarHost(hostState = stackedSnackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (isScrollable) {
                            Text(
                                text = stringResource(R.string.settings_title),
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.graphicsLayer {
                                    alpha = 1f - titleFraction
                                    translationY = -titleFraction * 12.dp.toPx()
                                },
                            )
                            Text(
                                text = stringResource(R.string.data_import_title),
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.graphicsLayer {
                                    alpha = titleFraction
                                    translationY = (1f - titleFraction) * 12.dp.toPx()
                                },
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.data_import_title),
                                style = MaterialTheme.typography.titleLarge,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = topAppBarContainerColor,
                    scrolledContainerColor = topAppBarContainerColor,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (isScrollable) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, top = 8.dp, bottom = 4.dp)
                        .onSizeChanged { size ->
                            headerHeightPx = size.height + extraSpacingPx
                        }
                ) {
                    Text(
                        text = stringResource(R.string.data_import_title),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                }
            }

            SectionLabel(stringResource(R.string.data_import_section_description))
            SettingsCard {
                Text(stringResource(R.string.data_import_card_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.data_import_card_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalButton(
                        onClick = { folderLauncher.launch(null) },
                        enabled = !uiState.isScanning,
                    ) {
                        Text(stringResource(R.string.data_import_select_folder))
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
                SectionLabel(stringResource(R.string.data_import_section_results))
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
                            stringResource(R.string.data_import_importing)
                        } else {
                            stringResource(R.string.data_import_start_import_format, uiState.successes.size)
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
