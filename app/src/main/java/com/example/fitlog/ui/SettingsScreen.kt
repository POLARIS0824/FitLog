package com.example.fitlog.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.fitlog.ui.components.SectionLabel
import com.example.fitlog.ui.components.SettingsCard
import com.example.fitlog.ui.components.TonalIcon

/**
 * 1. 容器层
 *
 * Settings 主页是纯导航页（只做分组入口，不含表单），暂无页面状态，
 * 因此不需要 ViewModel。导航回调由上层（NavDisplay）注入。
 */
@Composable
fun SettingsRoute(
    onBack: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    onNavigateToAppearance: () -> Unit = {},
    onNavigateToAISettings: () -> Unit = {},
    onNavigateToDataImport: () -> Unit = {},
    onNavigateToReminder: () -> Unit = {},
    onNavigateToAbout: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    SettingsScreen(
        onBack = onBack,
        onNavigateToProfile = onNavigateToProfile,
        onNavigateToAppearance = onNavigateToAppearance,
        onNavigateToAISettings = onNavigateToAISettings,
        onNavigateToDataImport = onNavigateToDataImport,
        onNavigateToReminder = onNavigateToReminder,
        onNavigateToAbout = onNavigateToAbout,
        modifier = modifier,
    )
}

/**
 * 2. 纯 UI 展示层
 *
 * 布局语言与 AI Configuration 一致：折叠渐变顶栏 + 浅底白卡 +
 * 区块标签 + 分组卡（同一分组的入口行放同一张卡里）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToAppearance: () -> Unit,
    onNavigateToAISettings: () -> Unit,
    onNavigateToDataImport: () -> Unit,
    onNavigateToReminder: () -> Unit,
    onNavigateToAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        topBar = {
            LargeTopAppBar(
                title = { Text("Settings") },
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
            SettingsCard {
                SettingsEntryRow(
                    icon = Icons.Default.Person,
                    title = "个人资料",
                    subtitle = "身高、体重、单位",
                    tonalIndex = 0,
                    onClick = onNavigateToProfile,
                )
                SettingsEntryRow(
                    icon = Icons.Default.Palette,
                    title = "外观",
                    subtitle = "主题、动态取色",
                    tonalIndex = 1,
                    onClick = onNavigateToAppearance,
                )
            }

            SettingsCard {
                SettingsEntryRow(
                    icon = Icons.Default.AutoAwesome,
                    title = "AI Configuration",
                    subtitle = "服务商、凭据、模型",
                    tonalIndex = 2,
                    onClick = onNavigateToAISettings,
                )
            }

            SettingsCard {
                SettingsEntryRow(
                    icon = Icons.Default.Upload,
                    title = "数据导入",
                    subtitle = "从 Markdown 导入训练日志",
                    tonalIndex = 0,
                    onClick = onNavigateToDataImport,
                )
                SettingsEntryRow(
                    icon = Icons.Default.Notifications,
                    title = "训练提醒",
                    subtitle = "定期提醒你训练",
                    tonalIndex = 1,
                    onClick = onNavigateToReminder,
                )
            }

            SettingsCard {
                SettingsEntryRow(
                    icon = Icons.Default.Info,
                    title = "关于 FitLog",
                    subtitle = "版本与开源许可",
                    tonalIndex = 2,
                    onClick = onNavigateToAbout,
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * Settings 入口行：tonal 圆形图标 + 标题 + 副标题 + 尾部 › 示能。
 *
 * 同一分组的多个入口行放在同一张 [SettingsCard] 里（Google 式分组卡）。
 */
@Composable
private fun SettingsEntryRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tonalIndex: Int,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TonalIcon(icon = icon, index = tonalIndex, size = 40.dp)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 3. 预览层
 */
@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    SettingsScreen(
        onBack = {},
        onNavigateToProfile = {},
        onNavigateToAppearance = {},
        onNavigateToAISettings = {},
        onNavigateToDataImport = {},
        onNavigateToReminder = {},
        onNavigateToAbout = {},
    )
}
