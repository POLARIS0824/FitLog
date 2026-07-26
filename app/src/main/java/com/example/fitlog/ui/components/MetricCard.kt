package com.example.fitlog.ui.components

import android.content.res.Configuration
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fitlog.ui.theme.FitLogTheme
import kotlin.math.min
import kotlin.math.sin

/**
 * 环形图分段 UI 模型（纯 Kotlin，不含 Compose 类型，可安全放入 UiState）。
 *
 * @property label 图例标签（如 "力量"）
 * @property fraction 占比（0.0f ~ 1.0f）；≤ 0 时分段不绘制但图例仍保留
 * @property colorKey 语义色键（"strength" / "cardio" / "recovery"），由组件映射为主题色
 * @property valueText 图例数值文本（如 "60%"、"—"）
 */
data class RingSegment(
    val label: String,
    val fraction: Float,
    val colorKey: String,
    val valueText: String,
)

/**
 * 大号指标卡片（如 Cardio load）：
 * 位于仪表盘左侧，采用突出纵向布局，包含顶部 Pill 胶囊图标徽章、中底部主数值及状态信息。
 * 支持传入 [progress] 开启水桶波浪装水进度填充效果（0.0f ~ 1.0f）。
 * 遵循 Material 3 Expressive 设计规范，注意圆角、尺寸、字重分级与 Dynamic Color 适配。
 *
 * @param title 指标标题（例如 "Cardio load"）
 * @param value 主指标数值（例如 "0%"）
 * @param modifier 修饰符
 * @param subtitle 底部副标题或状态信息（例如 "Calibrating"）
 * @param icon 矢量图标
 * @param progress 可选装水进度百分比（0.0f ~ 1.0f），为 null 时展示传统静态背景卡片
 * @param liquidColor 水流波浪颜色，为 null 时默认提取 [contentColor] 的适当透明度
 * @param ringSegments 环形图分段；非空时进入环形图模式：忽略 [progress]/[liquidColor] 水波，
 *     中部渲染环形图 + 标题数值，底部渲染分段图例
 * @param containerColor 卡片背景色，默认采用 MaterialTheme.colorScheme.primaryContainer 支持动态色彩
 * @param contentColor 内容文本与图标主要颜色，默认采用 MaterialTheme.colorScheme.onPrimaryContainer
 * @param badgeContainerColor 顶部胶囊图标背景色，默认采用 MaterialTheme.colorScheme.surface
 * @param badgeContentColor 顶部胶囊图标内容颜色，默认与 [contentColor] 一致
 * @param onClick 卡片点击事件回调
 */
@Composable
fun LargeMetricCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    progress: Float? = null,
    liquidColor: Color? = null,
    ringSegments: List<RingSegment>? = null,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    badgeContainerColor: Color = MaterialTheme.colorScheme.surfaceContainerLowest,
    badgeContentColor: Color = contentColor,
    onClick: (() -> Unit)? = null,
) {
    Card(
        modifier = modifier
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
            ),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (progress != null && ringSegments == null) {
                val animatedProgress by animateFloatAsState(
                    targetValue = progress.coerceIn(0f, 1f),
                    animationSpec = tween(durationMillis = 800),
                    label = "waterLevelProgress",
                )
                val infiniteTransition = rememberInfiniteTransition(label = "liquidWave")
                val wavePhase by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = (2 * Math.PI).toFloat(),
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 2500, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart,
                    ),
                    label = "wavePhase",
                )

                val waveColor = liquidColor ?: contentColor.copy(alpha = 0.22f)

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val width = size.width
                    val height = size.height
                    if (width > 0f && height > 0f) {
                        val baseWaterLevelY = height * (1f - animatedProgress)
                        val amplitude = 5.dp.toPx()

                        val path = Path().apply {
                            moveTo(0f, height)
                            lineTo(0f, baseWaterLevelY)
                            var x = 0f
                            val step = 4f
                            while (x <= width) {
                                val y = baseWaterLevelY + sin((x / width) * 2 * Math.PI + wavePhase).toFloat() * amplitude
                                lineTo(x, y)
                                x += step
                            }
                            lineTo(width, height)
                            close()
                        }
                        drawPath(path = path, color = waveColor)
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(start = 7.dp, top = 7.dp, end = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                // 顶部图标胶囊徽章（紧贴卡片边距，去除厚重卡框感）
                if (icon != null) {
                    Box(
                        modifier = Modifier
                            .width(32.dp)
                            .height(46.dp)
                            .clip(CircleShape)
                            .background(badgeContainerColor),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = title,
                            tint = badgeContentColor,
                            modifier = Modifier.size(17.dp),
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.height(16.dp))
                }

                if (ringSegments != null) {
                    // 环形图模式：中部环形图 + 标题数值，底部分段图例
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        MetricRingChart(
                            segments = ringSegments,
                            contentColor = contentColor,
                            modifier = Modifier.size(84.dp),
                        )
                        LargeMetricCardTexts(
                            title = title,
                            value = value,
                            subtitle = subtitle,
                            contentColor = contentColor,
                        )
                    }
                    RingChartLegend(
                        segments = ringSegments,
                        contentColor = contentColor,
                        modifier = Modifier.padding(start = 5.dp),
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))

                    // 底部文字内容
                    LargeMetricCardTexts(
                        title = title,
                        value = value,
                        subtitle = subtitle,
                        contentColor = contentColor,
                        modifier = Modifier.padding(start = 5.dp, bottom = 2.dp),
                    )
                }
            }
        }
    }
}

/** 大卡文字块：标题 + 主数值 + 可选副标题（水波/环形两种模式共用同一套字级样式）。 */
@Composable
private fun LargeMetricCardTexts(
    title: String,
    value: String,
    subtitle: String?,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                lineHeight = 18.sp,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
            ),
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 34.sp,
                lineHeight = 38.sp,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
            ),
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (!subtitle.isNullOrEmpty()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                ),
                color = contentColor.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 环形占比图：中性轨道圆环 + 按 [RingSegment.fraction] 划分的彩色分段弧。
 * 分段自正上方（-90°）起顺时针排布，多段之间留 3° 间隔；
 * 首次进入时带 800ms 顺时针扫入动画（与水波动画时长一致）。
 *
 * @param segments 分段列表（fraction ≤ 0 的分段不绘制）
 * @param contentColor 卡片内容色，用于推导轨道色
 * @param modifier 修饰符
 * @param strokeWidth 圆环描边宽度
 */
@Composable
private fun MetricRingChart(
    segments: List<RingSegment>,
    contentColor: Color,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 12.dp,
) {
    // 颜色解析须在 Canvas 绘制作用域之外完成（draw lambda 非 Composable）
    val resolved = segments.map { it to ringSegmentColor(it.colorKey) }
    val trackColor = contentColor.copy(alpha = 0.10f)
    val animatedSweep by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 800),
        label = "ringSweep",
    )

    Canvas(modifier = modifier) {
        val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Butt)
        val diameter = min(size.width, size.height) - stroke.width
        if (diameter <= 0f) return@Canvas
        val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
        val arcSize = Size(diameter, diameter)

        // 中性轨道底环
        drawArc(
            color = trackColor,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = stroke,
        )

        // 分段弧：随 animatedSweep 从 0 顺时针扫入
        val visible = resolved.filter { it.first.fraction > 0f }
        val gapDegrees = if (visible.size > 1) 3f else 0f
        var startAngle = -90f
        visible.forEach { (segment, color) ->
            val span = segment.fraction * 360f * animatedSweep
            val sweep = (span - gapDegrees).coerceAtLeast(0f)
            if (sweep > 0f) {
                drawArc(
                    color = color,
                    startAngle = startAngle + gapDegrees / 2f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = stroke,
                )
            }
            startAngle += span
        }
    }
}

/**
 * 环形图图例：色块 + "标签 数值" 横向排列，展示全部分段（含 0 占比的保留位）。
 *
 * @param segments 分段列表
 * @param contentColor 卡片内容色（文本以其 0.8 透明度呈现）
 * @param modifier 修饰符
 */
@Composable
private fun RingChartLegend(
    segments: List<RingSegment>,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        segments.forEach { segment ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(ringSegmentColor(segment.colorKey)),
                )
                Text(
                    text = "${segment.label} ${segment.valueText}",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = contentColor.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** [RingSegment.colorKey] → 主题色映射（M3 Expressive Dynamic Color 安全）。 */
@Composable
private fun ringSegmentColor(colorKey: String): Color = when (colorKey) {
    "strength" -> MaterialTheme.colorScheme.primary
    "cardio" -> MaterialTheme.colorScheme.tertiary
    "recovery" -> MaterialTheme.colorScheme.outlineVariant
    else -> MaterialTheme.colorScheme.secondary
}

/**
 * 小号指标卡片（如 Steps, Readiness, Sleep duration）：
 * 位于仪表盘右侧，横向卡片布局，包含左侧椭圆/胶囊图标徽章及右侧两行标题与数值信息。
 * 遵循 Material 3 Expressive 规范，支持多色调色彩方案与 Dynamic Color。
 *
 * @param title 指标标题（例如 "Steps"）
 * @param value 指标数值或状态描述（例如 "162", "Calibrating", "No data"）
 * @param modifier 修饰符
 * @param icon 矢量图标
 * @param containerColor 卡片背景色，默认采用 MaterialTheme.colorScheme.secondaryContainer
 * @param contentColor 内容文本与图标主要颜色，默认采用 MaterialTheme.colorScheme.onSecondaryContainer
 * @param badgeContainerColor 图标徽章背景色，默认使用 [contentColor] 的透明度混合
 * @param badgeContentColor 图标徽章图标颜色，默认与 [contentColor] 一致
 * @param onClick 卡片点击事件回调
 */
@Composable
fun SmallMetricCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    badgeContainerColor: Color = MaterialTheme.colorScheme.surfaceContainerLowest,
    badgeContentColor: Color = contentColor,
    onClick: (() -> Unit)? = null,
) {
    Card(
        modifier = modifier
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
            ),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 5.dp, top = 5.dp, end = 12.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 左侧瘦长椭圆胶囊徽章（增加高度至44dp提升卡片高度与纵向比例，同时紧贴边框边距消除厚重感）
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .width(26.dp)
                        .height(44.dp)
                        .clip(CircleShape)
                        .background(badgeContainerColor),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = badgeContentColor,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            // 右侧标题与数值/状态
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                    ),
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                    ),
                    color = contentColor.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * 仪表盘指标卡片网格布局：
 * 将左侧 [LargeMetricCard] 与右侧三个 [SmallMetricCard] 组合排布，完美还原设计图结构。
 * 使用 [IntrinsicSize.Max] 确保左右两列高度等高对齐。
 *
 * @param largeCardLeft 左侧大卡片组件
 * @param smallCardTop 右侧上层小卡片组件
 * @param smallCardMiddle 右侧中层小卡片组件
 * @param smallCardBottom 右侧下层小卡片组件
 * @param modifier 修饰符
 */
@Composable
fun MetricDashboardGrid(
    largeCardLeft: @Composable (Modifier) -> Unit,
    smallCardTop: @Composable (Modifier) -> Unit,
    smallCardMiddle: @Composable (Modifier) -> Unit,
    smallCardBottom: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Max),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // 左列大卡片
        largeCardLeft(
            Modifier
                .weight(1.05f)
                .fillMaxHeight()
        )

        // 右列 3 张小卡片
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            smallCardTop(Modifier.weight(1f))
            smallCardMiddle(Modifier.weight(1f))
            smallCardBottom(Modifier.weight(1f))
        }
    }
}

/**
 * 卡片组下方分页指示器点（Page Indicator）：
 * 首个指示点呈椭圆胶囊形状，其余为小圆形点。
 *
 * @param pageCount 总页数
 * @param currentPage 当前选中页索引
 * @param modifier 修饰符
 * @param activeColor 选中指示点颜色
 * @param inactiveColor 未选中指示点颜色
 */
@Composable
fun MetricPageIndicator(
    pageCount: Int,
    currentPage: Int,
    modifier: Modifier = Modifier,
    activeColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    inactiveColor: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (i in 0 until pageCount) {
            val isSelected = i == currentPage
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .height(7.dp)
                    .width(if (isSelected) 18.dp else 7.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) activeColor else inactiveColor)
            )
        }
    }
}

/**
 * 带有底部分页指示器的完整仪表盘指标卡片区域组件。
 *
 * @param modifier 修饰符
 * @param onCardClick 点击卡片触发的回调
 */
@Composable
fun MetricDashboardSection(
    modifier: Modifier = Modifier,
    onCardClick: ((String) -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MetricDashboardGrid(
            largeCardLeft = { gridModifier ->
                LargeMetricCard(
                    title = "Cardio load",
                    value = "0%",
                    subtitle = "Calibrating",
                    icon = Icons.Default.Favorite,
                    containerColor = Color(0xFFD6E4FF),
                    contentColor = Color(0xFF003067),
                    badgeContainerColor = Color.White,
                    badgeContentColor = Color(0xFF0056B3),
                    modifier = gridModifier,
                    onClick = { onCardClick?.invoke("Cardio load") },
                )
            },
            smallCardTop = { gridModifier ->
                SmallMetricCard(
                    title = "Steps",
                    value = "162",
                    icon = Icons.AutoMirrored.Filled.DirectionsRun,
                    containerColor = Color(0xFFB2F5EA),
                    contentColor = Color(0xFF004D40),
                    badgeContainerColor = Color.White,
                    badgeContentColor = Color(0xFF004D40),
                    modifier = gridModifier,
                    onClick = { onCardClick?.invoke("Steps") },
                )
            },
            smallCardMiddle = { gridModifier ->
                SmallMetricCard(
                    title = "Readiness",
                    value = "Calibrating",
                    icon = Icons.Default.SelfImprovement,
                    containerColor = Color(0xFFE2E8F0),
                    contentColor = Color(0xFF2D3748),
                    badgeContainerColor = Color.White,
                    badgeContentColor = Color(0xFF2D3748),
                    modifier = gridModifier,
                    onClick = { onCardClick?.invoke("Readiness") },
                )
            },
            smallCardBottom = { gridModifier ->
                SmallMetricCard(
                    title = "Sleep duration",
                    value = "No data",
                    icon = Icons.Default.NightsStay,
                    containerColor = Color(0xFFF3E8FF),
                    contentColor = Color(0xFF4A154B),
                    badgeContainerColor = Color.White,
                    badgeContentColor = Color(0xFF4A154B),
                    modifier = gridModifier,
                    onClick = { onCardClick?.invoke("Sleep duration") },
                )
            },
        )

        MetricPageIndicator(
            pageCount = 3,
            currentPage = 0,
        )
    }
}

// ==========================================
// Previews
// ==========================================

/**
 * 亮色模式与精确定制色彩下仪表盘指标卡片区域组件 Preview
 */
@Preview(name = "Metric Dashboard Section - Light", showBackground = true)
@Composable
fun MetricDashboardSectionPreview() {
    FitLogTheme(darkTheme = false) {
        Surface(
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.background,
        ) {
            MetricDashboardSection()
        }
    }
}

/**
 * 暗色模式（Dark Theme）与 Dynamic Color 下仪表盘指标卡片区域组件 Preview
 */
@Preview(name = "Metric Dashboard Section - Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun MetricDashboardSectionDarkPreview() {
    FitLogTheme(darkTheme = true) {
        Surface(
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.background,
        ) {
            MetricDashboardSection()
        }
    }
}

/**
 * Dynamic Color（直接提取 Theme 的色彩）下卡片排布 Preview
 */
@Preview(name = "Metric Dashboard - Dynamic M3 Color", showBackground = true)
@Composable
fun MetricDashboardDynamicColorPreview() {
    FitLogTheme {
        Surface(
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.background,
        ) {
            MetricDashboardGrid(
                largeCardLeft = { modifier ->
                    LargeMetricCard(
                        title = "Cardio load",
                        value = "0%",
                        subtitle = "Calibrating",
                        icon = Icons.Default.Favorite,
                        modifier = modifier,
                    )
                },
                smallCardTop = { modifier ->
                    SmallMetricCard(
                        title = "Steps",
                        value = "162",
                        icon = Icons.AutoMirrored.Filled.DirectionsRun,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = modifier,
                    )
                },
                smallCardMiddle = { modifier ->
                    SmallMetricCard(
                        title = "Readiness",
                        value = "Calibrating",
                        icon = Icons.Default.SelfImprovement,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = modifier,
                    )
                },
                smallCardBottom = { modifier ->
                    SmallMetricCard(
                        title = "Sleep duration",
                        value = "No data",
                        icon = Icons.Default.NightsStay,
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = modifier,
                    )
                },
            )
        }
    }
}

/**
 * 大卡环形图模式（训练分布）Preview：力量/有氧双色分段 + 恢复保留位图例。
 */
@Preview(name = "Large Metric Card - Ring Chart", showBackground = true)
@Composable
fun LargeMetricCardRingPreview() {
    FitLogTheme {
        Surface(
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.background,
        ) {
            LargeMetricCard(
                title = "训练分布",
                value = "3 次",
                subtitle = "力量 2 · 有氧 1",
                icon = Icons.Default.Favorite,
                ringSegments = listOf(
                    RingSegment(label = "力量", fraction = 0.6f, colorKey = "strength", valueText = "60%"),
                    RingSegment(label = "有氧", fraction = 0.3f, colorKey = "cardio", valueText = "30%"),
                    RingSegment(label = "恢复", fraction = 0f, colorKey = "recovery", valueText = "—"),
                ),
                modifier = Modifier
                    .width(200.dp)
                    .height(220.dp),
            )
        }
    }
}
