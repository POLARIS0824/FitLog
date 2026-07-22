package com.example.fitlog.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.fitlog.R
import com.example.fitlog.ui.components.SettingsCard
import com.example.fitlog.ui.components.TonalIcon
import kotlinx.coroutines.CancellationException

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
 * 遵循与 [AISettingsScreen] 统一的 Google Material Expressive 动态双标题模式：
 * 1. 顶栏采用常驻 [TopAppBar] + [pinnedScrollBehavior]。
 * 2. 可滚动状态下：大标题 [headlineMedium] 置于滚动内容顶部，带 8dp 左缩进与卡片内部完全对齐。
 * 3. 滚动时大标题自然沉入不透明顶栏下方，顶栏标题根据 [titleFraction] 滚动进度从下至上平滑渐变淡入 (alpha & translationY 联动)。
 * 4. 滚动停止在半折叠状态时，触发 [spring] 弹簧动画自动吸附到最近的稳定边界（0 或 headerHeightPx）。
 * 5. 不可滚动状态下：自动隐藏 Body 重复的大标题，顶栏直接稳定展示本页标题 "Settings"。
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
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val scrollState = rememberScrollState()

    val density = LocalDensity.current
    val extraSpacingPx = remember(density) { with(density) { 12.dp.roundToPx() } }

    // 自适应双态：动态检测页面内容是否能够产生滚动
    val isScrollable by remember { derivedStateOf { scrollState.maxValue > 0 } }

    // 标题切换进度：0 = 完全展开（显示 Body 大标题），1 = 大标题刚好完全滚入顶栏之下。
    var headerHeightPx by remember { mutableIntStateOf(0) }
    val titleFraction by remember {
        derivedStateOf {
            if (!isScrollable || headerHeightPx <= 0) 0f
            else (scrollState.value.toFloat() / headerHeightPx.toFloat()).coerceIn(0f, 1f)
        }
    }

    // 吸附效果：手势/惯性滚动停止后，若大标题处于半折叠的中间态，自动平滑吸附到最近的稳定边界（0 或 headerHeightPx）。
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
                                stiffness = Spring.StiffnessMediumLow,
                            ),
                        )
                    } catch (e: CancellationException) {
                        // 吸附动画被用户新的手势打断，属正常交互
                    }
                }
            }
    }

    // 顶栏背景色随滚动进度在 surfaceContainerLow (展开) 与 surfaceContainer (折叠) 之间平滑过渡
    val topAppBarContainerColor = lerp(
        MaterialTheme.colorScheme.surfaceContainerLow,
        MaterialTheme.colorScheme.surfaceContainer,
        titleFraction,
    )

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        topBar = {
            TopAppBar(
                title = {
                    Box(contentAlignment = Alignment.CenterStart) {
                        Text(
                            text = stringResource(R.string.settings_title),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.graphicsLayer {
                                alpha = if (isScrollable) titleFraction else 1f
                                translationY = if (isScrollable) (1f - titleFraction) * 12.dp.toPx() else 0f
                            },
                        )
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
            // 可滚动页面渲染大标题 Header；不可滚动页面隐藏 Body 重复大标题
            if (isScrollable) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, top = 8.dp, bottom = 4.dp)
                        .onSizeChanged { size ->
                            headerHeightPx = size.height + extraSpacingPx
                        },
                ) {
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                }
            }

            // 分组 1：账号与偏好
            SettingsCard(
                contentPadding = PaddingValues(0.dp),
                verticalArrangement = Arrangement.Top,
            ) {
                SettingsEntryRow(
                    icon = Icons.Default.Person,
                    title = stringResource(R.string.settings_profile_title),
                    subtitle = stringResource(R.string.settings_profile_subtitle),
                    tonalIndex = 0,
                    onClick = onNavigateToProfile,
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                    thickness = 1.dp,
                )
                SettingsEntryRow(
                    icon = Icons.Default.Palette,
                    title = stringResource(R.string.settings_appearance_title),
                    subtitle = stringResource(R.string.settings_appearance_subtitle),
                    tonalIndex = 1,
                    onClick = onNavigateToAppearance,
                )
            }

            // 分组 2：智能引擎
            SettingsCard(
                contentPadding = PaddingValues(0.dp),
                verticalArrangement = Arrangement.Top,
            ) {
                SettingsEntryRow(
                    icon = Icons.Default.AutoAwesome,
                    title = stringResource(R.string.settings_ai_title),
                    subtitle = stringResource(R.string.settings_ai_subtitle),
                    tonalIndex = 2,
                    onClick = onNavigateToAISettings,
                )
            }

            // 分组 3：数据与通知
            SettingsCard(
                contentPadding = PaddingValues(0.dp),
                verticalArrangement = Arrangement.Top,
            ) {
                SettingsEntryRow(
                    icon = Icons.Default.Upload,
                    title = stringResource(R.string.settings_data_import_title),
                    subtitle = stringResource(R.string.settings_data_import_subtitle),
                    tonalIndex = 3,
                    onClick = onNavigateToDataImport,
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                    thickness = 1.dp,
                )
                SettingsEntryRow(
                    icon = Icons.Default.Notifications,
                    title = stringResource(R.string.settings_reminder_title),
                    subtitle = stringResource(R.string.settings_reminder_subtitle),
                    tonalIndex = 4,
                    onClick = onNavigateToReminder,
                )
            }

            // 分组 4：系统与关于
            SettingsCard(
                contentPadding = PaddingValues(0.dp),
                verticalArrangement = Arrangement.Top,
            ) {
                SettingsEntryRow(
                    icon = Icons.Default.Info,
                    title = stringResource(R.string.settings_about_title),
                    subtitle = stringResource(R.string.settings_about_subtitle),
                    tonalIndex = 0,
                    onClick = onNavigateToAbout,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
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
