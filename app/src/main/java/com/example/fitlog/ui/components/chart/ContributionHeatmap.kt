package com.example.fitlog.ui.components.chart

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.example.fitlog.ui.theme.FitLogTheme
import java.time.LocalDate
import java.time.Month
import java.util.Locale
import kotlin.math.floor
import java.time.format.TextStyle as JavaTextStyle

/** 入场扫波的列错峰系数：列进度 = t×(1+SWEEP) − col×SWEEP/(cols−1)。 */
private const val ENTRANCE_SWEEP = 0.35f

/**
 * GitHub 风格的贡献热力图：7 行（周一..周日）× 周列的圆角格子矩阵，
 * 颜色深浅表示当日训练量强度。
 *
 * ## 布局与滚动
 *
 * 左侧固定行标签列（Mon/Wed/Fri，不随滚动）；右侧 `horizontalScroll` 内容区
 * （格子矩阵 + 底部月标签行，同一 Canvas 单一坐标系，月标签与列严格对齐并随内容滚动）。
 * 首帧自动滚到最右（最新一周）；时间轴恒为从左到右（强制 LTR，不受 RTL 影响）。
 *
 * ## 颜色（dynamic color 合规）
 *
 * 色阶 = `lerp(emptyColor → baseColor)` 的 [HeatmapGrid.LEVELS] 档插值，
 * 默认取 `colorScheme.primary` / `surfaceContainerHighest`——
 * 动态取色与回退主题经同一 token 派生，组件内零硬编码色。
 * 格子的动画等级是连续浮点，颜色在相邻档间平滑过渡（见 [HeatmapState] 双快照模型）。
 *
 * ## 强度算法（可插拔）
 *
 * [values] 经 [levelOf]（默认 [HeatmapLevels.linearByMax]）分档为 0..4 级。
 * 「算法后续探讨」的接缝即此参数（分位数/对数/滚动归一等）——注意 [levelOf]
 * **不是**动画 effect 的 key：更换算法需与 [values] 一起更换才会重算（同
 * `valueFormatter` 先例，v1 取舍）。
 *
 * ## 纯度契约
 *
 * 动画状态机的唯一 key 是 `levels = remember(values) { ... }`（Map 内容相等）；
 * [monthLabelOf]/[onDayClick]/[levelOf] 均不参与 key。调用方每次重组新建
 * [values] 实例也安全（内容相等即不重启动画）。
 *
 * ## 注意
 *
 * - [endDate] 之后的未来格完全透明（末列部分周的自然形态）
 * - Preview 不执行滚动 effect，渲染最左（最旧）区域
 * - [endDate] 默认 `LocalDate.now()` 每次重组求值，跨午夜重组网格右移一列（v1 注明）
 * - 配置更改后入场扫波重放（v1 取舍，同家族惯例）
 *
 * @param values 日期 → 当日训练量（kg）；未出现的日期按 0 档（空色）
 * @param modifier 修饰符
 * @param endDate 窗口锚点（含），末列为其所在周（周一始）
 * @param weekCount 列数（53 ≈ 一年，13 ≈ 一季度）
 * @param levelOf 强度分档算法（见 [HeatmapLevels]；非动画 key，见上）
 * @param state 动画状态持有者；默认运行时入场扫波、Preview 渲染定形态
 * @param baseColor 满档色（默认主题 primary）
 * @param emptyColor 空档色（默认主题 surfaceContainerHighest）
 * @param labelColor 行/月标签颜色
 * @param labelStyle 行/月标签文字样式
 * @param cellSize 格子边长
 * @param cellSpacing 格子间距
 * @param weekdayLabels 周一..周日 7 个行标签（仅 0/2/4 索引显示）
 * @param monthLabelOf 月标签文案（非动画 key）
 * @param onDayClick 格子点击回调（未来格不触发）；null 不可点击
 * @param animationSpec 数据过渡动画规格
 * @param scrollToEndInitially 首帧是否自动滚到最右
 * @param contentDescription 无障碍摘要（建议含统计信息，如"过去一年训练热力图，87 天有训练"）；null 则对 TalkBack 透明
 */
@Composable
fun ContributionHeatmap(
    values: Map<LocalDate, Float>,
    modifier: Modifier = Modifier,
    endDate: LocalDate = LocalDate.now(),
    weekCount: Int = 53,
    levelOf: (value: Float, max: Float) -> Int = HeatmapLevels::linearByMax,
    state: HeatmapState = rememberHeatmapState(
        // Preview 不执行 LaunchedEffect，播种定形态避免空图；运行时传 null 播入场扫波
        if (LocalInspectionMode.current) HeatmapLevels.levelsOf(values, levelOf) else null,
    ),
    baseColor: Color = MaterialTheme.colorScheme.primary,
    emptyColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    labelStyle: TextStyle = MaterialTheme.typography.labelSmall,
    cellSize: Dp = 12.dp,
    cellSpacing: Dp = 3.dp,
    weekdayLabels: List<String> = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"),
    monthLabelOf: (Month) -> String = { it.getDisplayName(JavaTextStyle.SHORT, Locale.getDefault()) },
    onDayClick: ((LocalDate) -> Unit)? = null,
    animationSpec: AnimationSpec<Float> = tween(600, easing = FastOutSlowInEasing),
    scrollToEndInitially: Boolean = true,
    contentDescription: String? = null,
) {
    require(weekdayLabels.size == HeatmapGrid.DAYS_PER_WEEK) {
        "weekdayLabels must have ${HeatmapGrid.DAYS_PER_WEEK} entries (Mon..Sun), was ${weekdayLabels.size}"
    }

    val columns = remember(endDate, weekCount) { HeatmapGrid.buildWeekColumns(endDate, weekCount) }
    val monthLabels = remember(columns) { HeatmapGrid.buildMonthLabels(columns) }
    // 动画状态机的唯一 key：Map 内容相等（纯度契约见 KDoc）
    val levels = remember(values) { HeatmapLevels.levelsOf(values, levelOf) }
    LaunchedEffect(levels) {
        state.animateTo(levels, animationSpec)
    }

    // 首帧滚到最右：maxValue 布局后才非零，故以 maxValue 为 key + once 守卫（不覆盖用户滚动）
    val scrollState = rememberScrollState()
    var didInitialScroll by remember { mutableStateOf(false) }
    LaunchedEffect(scrollState.maxValue) {
        if (scrollToEndInitially && !didInitialScroll && scrollState.maxValue > 0) {
            didInitialScroll = true
            scrollState.scrollTo(scrollState.maxValue)
        }
    }

    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    // 行标签列宽 = 最宽标签 + 间距（组合期测量，逐帧零成本）
    val rowLabelWidth = remember(weekdayLabels, labelStyle, density) {
        val maxPx = weekdayLabels.maxOf { textMeasurer.measure(text = it, style = labelStyle).size.width }
        with(density) { maxPx.toDp() } + 6.dp
    }
    // 月标签布局结果（数据期静态，预测量一次）
    val monthLabelLayouts = remember(monthLabels, labelStyle, monthLabelOf) {
        monthLabels.associate { it.columnIndex to textMeasurer.measure(text = monthLabelOf(it.month), style = labelStyle) }
    }
    // 色阶：emptyColor → baseColor 的 LEVELS 档插值（dynamic color 经参数注入）
    val ramp = remember(baseColor, emptyColor) {
        List(HeatmapGrid.LEVELS) { i -> lerp(emptyColor, baseColor, i.toFloat() / (HeatmapGrid.LEVELS - 1)) }
    }

    val pitch = cellSize + cellSpacing
    val gridWidth = cellSize * weekCount + cellSpacing * (weekCount - 1)
    val gridHeight = cellSize * HeatmapGrid.DAYS_PER_WEEK +
        cellSpacing * (HeatmapGrid.DAYS_PER_WEEK - 1)
    val monthRowHeight = remember(labelStyle, density) {
        val textHeight = if (labelStyle.lineHeight == TextUnit.Unspecified) {
            16.dp
        } else {
            with(density) { labelStyle.lineHeight.toDp() }
        }
        4.dp + textHeight
    }

    val currentOnDayClick by rememberUpdatedState(onDayClick)
    val rootModifier = if (contentDescription != null) {
        modifier.semantics { this.contentDescription = contentDescription }
    } else {
        modifier
    }

    // 时间轴恒为从左到右（horizontalScroll 在 RTL 下会翻转滚动方向）
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(modifier = rootModifier) {
            // 固定行标签列（不滚动）：每格一个 Box 与网格行严格同高对齐
            Column(
                modifier = Modifier
                    .width(rowLabelWidth)
                    .height(gridHeight),
                verticalArrangement = Arrangement.spacedBy(cellSpacing),
            ) {
                weekdayLabels.forEachIndexed { index, label ->
                    Box(
                        modifier = Modifier
                            .height(cellSize)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (index == 0 || index == 2 || index == 4) {
                            Text(
                                text = label,
                                style = labelStyle,
                                color = labelColor,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }

            // 滚动内容区：格子矩阵 + 月标签（同一 Canvas；宽度无界约束下必须显式尺寸）
            Box(modifier = Modifier.horizontalScroll(scrollState)) {
                Canvas(
                    modifier = Modifier
                        .width(gridWidth)
                        .height(gridHeight + monthRowHeight)
                        .pointerInput(columns, cellSize, cellSpacing, endDate) {
                            val cellPx = cellSize.toPx()
                            val pitchPx = pitch.toPx()
                            detectTapGestures { offset ->
                                val date = HeatmapGrid.cellAt(columns, offset.x, offset.y, cellPx, pitchPx)
                                if (date != null && !date.isAfter(endDate)) {
                                    currentOnDayClick?.invoke(date)
                                }
                            }
                        },
                ) {
                    val cellPx = cellSize.toPx()
                    val pitchPx = pitch.toPx()
                    val gridHeightPx = gridHeight.toPx()
                    val cols = columns.size
                    val t = state.progress.value

                    // ── 格子（列扫描修正进度；未来格跳过） ──
                    columns.forEachIndexed { col, week ->
                        val colT = if (cols <= 1) {
                            t
                        } else {
                            (t * (1f + ENTRANCE_SWEEP) - col * ENTRANCE_SWEEP / (cols - 1))
                                .coerceIn(0f, 1f)
                        }
                        week.forEachIndexed { row, date ->
                            if (date.isAfter(endDate)) return@forEachIndexed
                            val level = state.levelOf(date, colT)
                            val lo = floor(level).toInt().coerceIn(0, HeatmapGrid.LEVELS - 1)
                            val hi = minOf(lo + 1, HeatmapGrid.LEVELS - 1)
                            drawRoundRect(
                                color = lerp(ramp[lo], ramp[hi], level - lo),
                                topLeft = Offset(col * pitchPx, row * pitchPx),
                                size = Size(cellPx, cellPx),
                                cornerRadius = CornerRadius(cellPx * 0.28f),
                            )
                        }
                    }

                    // ── 月标签（随内容滚动；右缘收敛防溢出） ──
                    monthLabels.forEach { label ->
                        val layout = monthLabelLayouts[label.columnIndex] ?: return@forEach
                        drawText(
                            textLayoutResult = layout,
                            color = labelColor,
                            topLeft = Offset(
                                x = (label.columnIndex * pitchPx)
                                    .coerceIn(0f, size.width - layout.size.width),
                                y = gridHeightPx + 4.dp.toPx(),
                            ),
                        )
                    }
                }
            }
        }
    }
}

// ──────────────────────────────────────
// 预览（LocalInspectionMode 播种，渲染定形态；
//  滚动 effect 不执行，画面停在最左/最旧区域）
// ──────────────────────────────────────

private val previewEndDate: LocalDate = LocalDate.of(2026, 7, 25)

/** 确定性伪随机训练量：约 [attendance] 比例的日子有训练，量值 400..2000 kg。 */
private fun previewValues(daysBack: Long, attendance: Float): Map<LocalDate, Float> =
    (0L until daysBack).mapNotNull { i ->
        val date = previewEndDate.minusDays(i)
        val roll = (date.toEpochDay() * 37 % 100).toFloat() / 100f
        if (roll < attendance) {
            date to 400f + (date.toEpochDay() * 13 % 1600).toFloat()
        } else {
            null
        }
    }.toMap()

/** 全年形态：53 列，约六成的日子有训练。 */
@Preview(showBackground = true)
@Composable
private fun ContributionHeatmapYearPreview() {
    FitLogTheme {
        ContributionHeatmap(
            values = previewValues(daysBack = 364, attendance = 0.6f),
            endDate = previewEndDate,
            contentDescription = "过去一年训练容量热力图",
        )
    }
}

/** 一季度形态：13 列（窄于视口，无需滚动）。 */
@Preview(showBackground = true)
@Composable
private fun ContributionHeatmapQuarterPreview() {
    FitLogTheme {
        ContributionHeatmap(
            values = previewValues(daysBack = 90, attendance = 0.6f),
            endDate = previewEndDate,
            weekCount = 13,
        )
    }
}

/** 全空形态：无数据，仅空色格与月标签。 */
@Preview(showBackground = true)
@Composable
private fun ContributionHeatmapEmptyPreview() {
    FitLogTheme {
        ContributionHeatmap(
            values = emptyMap(),
            endDate = previewEndDate,
            weekCount = 13,
        )
    }
}

/** 稀疏 + 未来格形态：仅本周有训练，末列未来格透明（weekCount=2 免滚动可见）。 */
@Preview(showBackground = true)
@Composable
private fun ContributionHeatmapSparsePreview() {
    FitLogTheme {
        ContributionHeatmap(
            values = previewValues(daysBack = 6, attendance = 0.9f),
            endDate = previewEndDate,
            weekCount = 2,
        )
    }
}

/** 暗色主题形态。 */
@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ContributionHeatmapDarkPreview() {
    FitLogTheme {
        ContributionHeatmap(
            values = previewValues(daysBack = 364, attendance = 0.6f),
            endDate = previewEndDate,
        )
    }
}
