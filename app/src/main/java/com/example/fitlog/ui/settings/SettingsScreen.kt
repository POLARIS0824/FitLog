package com.example.fitlog.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.fitlog.ui.components.CollapsingTitleScaffold
import com.example.fitlog.ui.components.FitLogCard
import com.example.fitlog.ui.components.TonalIcon

/**
 * 1. 容器层
 *
 * Settings 主页是纯导航页（只做分组入口，不含表单），暂无页面状态，
 * 因此不需要 ViewModel。导航回调由上层（NavDisplay）注入。
 *
 * @param onBack 返回回调
 * @param onNavigateToProfile 跳转个人资料回调
 * @param onNavigateToAppearance 跳转外观回调
 * @param onNavigateToAISettings 跳转 AI 配置回调
 * @param onNavigateToDataImport 跳转数据导入回调
 * @param onNavigateToReminder 跳转训练提醒回调
 * @param onNavigateToAbout 跳转关于页面回调
 * @param modifier 修饰符
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
 * 采用与设置页群统一的 Material Expressive 动态双标题模式
 * （交互契约见 [CollapsingTitleScaffold]）。
 *
 * @param onBack 返回上一页
 * @param onNavigateToProfile 导航至个人资料
 * @param onNavigateToAppearance 导航至外观
 * @param onNavigateToAISettings 导航至 AI 配置
 * @param onNavigateToDataImport 导航至数据导入
 * @param onNavigateToReminder 导航至训练提醒
 * @param onNavigateToAbout 导航至关于
 * @param modifier 修饰符
 */
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
    CollapsingTitleScaffold(
        title = "Settings",
        onBack = onBack,
        modifier = modifier,
    ) {
        // 分组 1：账号与偏好
        FitLogCard(
            contentPadding = PaddingValues(0.dp),
            verticalArrangement = Arrangement.Top,
        ) {
            SettingsEntryRow(
                icon = Icons.Default.Person,
                title = "个人资料",
                subtitle = "身高、体重、单位",
                tonalIndex = 0,
                onClick = onNavigateToProfile,
            )
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                thickness = 1.dp,
            )
            SettingsEntryRow(
                icon = Icons.Default.Palette,
                title = "外观",
                subtitle = "主题、动态取色",
                tonalIndex = 1,
                onClick = onNavigateToAppearance,
            )
        }

        // 分组 2：智能引擎
        FitLogCard(
            contentPadding = PaddingValues(0.dp),
            verticalArrangement = Arrangement.Top,
        ) {
            SettingsEntryRow(
                icon = Icons.Default.AutoAwesome,
                title = "AI Configuration",
                subtitle = "服务商、凭据、模型",
                tonalIndex = 2,
                onClick = onNavigateToAISettings,
            )
        }

        // 分组 3：数据与通知
        FitLogCard(
            contentPadding = PaddingValues(0.dp),
            verticalArrangement = Arrangement.Top,
        ) {
            SettingsEntryRow(
                icon = Icons.Default.Upload,
                title = "数据导入",
                subtitle = "从 Markdown 导入训练日志",
                tonalIndex = 3,
                onClick = onNavigateToDataImport,
            )
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                thickness = 1.dp,
            )
            SettingsEntryRow(
                icon = Icons.Default.Notifications,
                title = "训练提醒",
                subtitle = "定期提醒你训练",
                tonalIndex = 4,
                onClick = onNavigateToReminder,
            )
        }

        // 分组 4：系统与关于
        FitLogCard(
            contentPadding = PaddingValues(0.dp),
            verticalArrangement = Arrangement.Top,
        ) {
            SettingsEntryRow(
                icon = Icons.Default.Info,
                title = "关于 FitLog",
                subtitle = "版本与开源许可",
                tonalIndex = 0,
                onClick = onNavigateToAbout,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * Settings 入口行：Material Expressive 风格行项（tonal 圆形图标 + 标题 + 副标题）。
 *
 * 按照 Pixel 原生设置规范，采用 16dp 水平与 12dp 垂直内边距，提升视觉精致度。
 *
 * @param icon 矢量图标
 * @param title 入口主标题
 * @param subtitle 入口副标题说明
 * @param tonalIndex 图标彩色底图色相序号
 * @param onClick 点击事件
 * @param modifier 修饰符
 */
@Composable
private fun SettingsEntryRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tonalIndex: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TonalIcon(
            icon = icon,
            index = tonalIndex,
            size = 44.dp,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
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
