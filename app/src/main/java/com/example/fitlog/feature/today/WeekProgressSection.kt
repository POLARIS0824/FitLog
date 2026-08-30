package com.example.fitlog.feature.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.fitlog.ui.components.FitLogCard
import com.example.fitlog.ui.components.LargeMetricCard
import com.example.fitlog.ui.components.MetricDashboardGrid
import com.example.fitlog.ui.components.MetricPageIndicator
import com.example.fitlog.ui.components.SectionLabel
import com.example.fitlog.ui.components.SmallMetricCard

/** 小卡槽位的装饰图标（按槽位固定，仅装饰不承载数据语义）。 */
private val smallCardIcons = listOf(
    Icons.Default.Star,
    Icons.Default.Favorite,
    Icons.Default.AutoAwesome,
)

/**
 * 本周进度区块：HorizontalPager 卡片横向滑动 + 底部 MetricPageIndicator 纯展示指示点。
 * 性能优化：通过 [androidx.compose.runtime.snapshotFlow] 仅监听 [androidx.compose.foundation.pager.PagerState.settledPage]
 * （即手势释放且页面停稳后），避免在拖拽中途频繁触发 ViewModel 状态刷新与数据库查询导致的滑动卡顿。
 */
@Composable
internal fun WeekProgressSection(
    weekProgress: WeekProgressState,
    onDisplayModeSelected: (WeekProgressDisplayMode) -> Unit,
    onLogClick: () -> Unit = {},
    onStartClick: () -> Unit = {},
    onEditClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val modes = WeekProgressDisplayMode.entries
    val initialPage = remember { modes.indexOf(weekProgress.displayMode).coerceAtLeast(0) }
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { modes.size },
    )

    // 仅在拖拽释放、动画结束停稳后 (settledPage) 才通知 ViewModel 切换显示模式，彻底解决滑动卡顿。
    // 守卫用的 displayMode 必须经 rememberUpdatedState 取最新值：本 LaunchedEffect 不随重组
    // 重启，直接捕获 weekProgress 会拿到首次组合的旧值——滑走再滑回时判等守卫误短路，
    // VM 的 displayMode 与 Pager 实际页脱钩
    val guardDisplayMode by rememberUpdatedState(weekProgress.displayMode)
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            val selectedMode = modes[page]
            if (selectedMode != guardDisplayMode) {
                onDisplayModeSelected(selectedMode)
            }
        }
    }

    val currentMode = modes[pagerState.currentPage]

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionLabel(currentMode.label)

        // 卡片横向滑动 Pager（使用预计算好的 itemsMap，让每一页数据即刻就位，滑动无延迟）
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth(),
            pageSpacing = 12.dp,
        ) { page ->
            val mode = modes[page]
            val pageItems = weekProgress.itemsMap[mode] ?: weekProgress.items
            WeekProgressDashboard(
                weekProgress = weekProgress.copy(
                    displayMode = mode,
                    items = pageItems,
                )
            )
        }

        // 底部指示点：纯展示状态，不开启点击事件以防误触
        MetricPageIndicator(
            pageCount = modes.size,
            currentPage = pagerState.currentPage,
        )

        // 快捷操作按钮组：Log / Start / 编辑
        MetricActionButtons(
            onLogClick = onLogClick,
            onStartClick = onStartClick,
            onEditClick = onEditClick,
        )
    }
}

/**
 * 本周进度仪表盘：[WeekProgressState.items] 契约固定 4 个——
 * item[0] 进左侧大卡（title=标题、valueText=主数值、subtitle=副标题，
 * progress 驱动水波，ringSegments 非空时渲染环形图），
 * item[1..3] 进右侧小卡；不足 4 个时小卡槽位填占位（防御性兜底）。
 */
@Composable
private fun WeekProgressDashboard(weekProgress: WeekProgressState) {
    val items = weekProgress.items
    if (items.isEmpty()) {
        FitLogCard {
            Text(
                text = "完成首次训练或选择一套计划后，这里会展示你的本周进度",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val smallColors = listOf(
        MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer,
        MaterialTheme.colorScheme.surfaceContainerHigh to MaterialTheme.colorScheme.onSurfaceVariant,
        MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer,
    )

    MetricDashboardGrid(
        largeCardLeft = { gridModifier ->
            val head = items[0]
            // 大卡主数值恒取 valueText（Calculator 契约：四种模式的 head 均设置该字段）
            LargeMetricCard(
                title = head.title,
                value = head.valueText ?: head.subtitle,
                subtitle = head.subtitle,
                icon = Icons.Default.FitnessCenter,
                progress = head.progress,
                ringSegments = head.ringSegments,
                modifier = gridModifier,
            )
        },
        smallCardTop = { gridModifier ->
            SmallMetricCardSlot(items.getOrNull(1), smallColors[0], smallCardIcons[0], gridModifier)
        },
        smallCardMiddle = { gridModifier ->
            SmallMetricCardSlot(items.getOrNull(2), smallColors[1], smallCardIcons[1], gridModifier)
        },
        smallCardBottom = { gridModifier ->
            SmallMetricCardSlot(items.getOrNull(3), smallColors[2], smallCardIcons[2], gridModifier)
        },
    )
}

/** 小卡槽位：有数据渲染真实指标，无数据渲染占位。 */
@Composable
private fun SmallMetricCardSlot(
    item: ProgressItemState?,
    colors: Pair<Color, Color>,
    icon: ImageVector,
    modifier: Modifier,
) {
    SmallMetricCard(
        title = item?.title ?: "—",
        value = item?.subtitle ?: "暂无数据",
        icon = icon,
        containerColor = colors.first,
        contentColor = colors.second,
        badgeContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        badgeContentColor = colors.second,
        modifier = modifier,
    )
}
