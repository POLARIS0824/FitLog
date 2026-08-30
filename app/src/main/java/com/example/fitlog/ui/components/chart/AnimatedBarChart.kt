package com.example.fitlog.ui.components.chart

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.fitlog.ui.theme.FitLogTheme
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * 默认 y 轴数值格式化（整数；Locale.US 固定小数点，避免本地化把数字变形）。
 *
 * 具名单例而非参数默认 lambda：默认表达式每次求值都是新实例（lambda 相等性为
 * 身份比较），会使 y 刻度/目标线预测量的 remember 逐帧失效。
 */
private val defaultAxisValueFormatter: (Float) -> String = { String.format(Locale.US, "%.0f", it) }

/**
 * 通用动画柱状图（自定义 Canvas 绘制）。
 *
 * 数据无关的可复用组件：输入一份 [ChartData]，数据更换时柱子以
 * **keyed 共享元素式插值动画**过渡——同一 id 的柱子滑行变形（高度/位置/宽度），
 * 新增柱子错峰生长，移除柱子原地收缩淡出；柱数变化（如周期切换 7↔30↔13↔12）
 * 与数据刷新（同 id 新值）走同一套状态机（见 [AnimatedChartState]）。
 *
 * ## 绘制规格
 *
 * - 胶囊形柱子（圆角 = min(柱宽, 柱高) / 2），柱宽 = 槽宽 × [barWidthFraction]，上限 32dp
 * - y 轴左侧整数刻度（跳过 0 基线），可选虚线目标线（右上数值标签）
 * - x 标签跟随所属柱子的动画位置与透明度；柱数过多时按槽宽自动抽稀（stride）
 * - 时间轴恒为从左到右（手绘坐标，不受 RTL 布局方向影响）
 *
 * ## 注意
 *
 * - [data] 必须纯值语义（不得携带 lambda），否则动画 effect 会在重组时误重启
 * - 首帧自动播放入场生长动画；Preview 中渲染定形态（[LocalInspectionMode] 播种）
 * - 配置更改后入场动画重放（v1 取舍）
 *
 * @param data 图表数据（柱列表 + y 轴上限 + 可选目标线）
 * @param modifier 修饰符（调用方负责定高，如 `fillMaxWidth().height(240.dp)`）
 * @param valueFormatter y 刻度/目标线数值的格式化（不影响 [data] 的相等性）
 * @param state 动画状态持有者；默认运行时生长入场、Preview 渲染定形态
 * @param barColor 柱色
 * @param goalLineColor 目标线颜色
 * @param axisLabelColor 刻度/标签颜色
 * @param axisLabelStyle 刻度/标签文字样式
 * @param barWidthFraction 柱宽占槽宽比例（0..1）
 * @param yTickCount y 轴刻度档数（不含 0 基线）
 * @param animationSpec 变形/入场动画规格
 * @param exitSpec 退场动画规格
 * @param maxStaggerMillis 入场错峰总时长
 * @param contentDescription 无障碍摘要；null 则图表对 TalkBack 透明
 */
@Composable
fun AnimatedBarChart(
    data: ChartData,
    modifier: Modifier = Modifier,
    valueFormatter: (Float) -> String = defaultAxisValueFormatter,
    state: AnimatedChartState = rememberAnimatedChartState(
        // Preview 不执行 LaunchedEffect，播种定形态避免空图；运行时传 null 生长入场
        if (LocalInspectionMode.current) data else null,
    ),
    barColor: Color = MaterialTheme.colorScheme.primary,
    goalLineColor: Color = MaterialTheme.colorScheme.tertiary,
    axisLabelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    axisLabelStyle: TextStyle = MaterialTheme.typography.labelSmall,
    barWidthFraction: Float = 0.55f,
    yTickCount: Int = 4,
    animationSpec: AnimationSpec<Float> = tween(450, easing = FastOutSlowInEasing),
    exitSpec: AnimationSpec<Float> = tween(250),
    maxStaggerMillis: Int = 240,
    contentDescription: String? = null,
) {
    LaunchedEffect(data) {
        state.animateTo(data, animationSpec, exitSpec, maxStaggerMillis)
    }

    val textMeasurer = rememberTextMeasurer()

    // x 标签预测量：同一数据期内静态，算出最大标签宽用于抽稀与底边距
    val xLabelLayouts = remember(data.entries, axisLabelStyle) {
        data.entries.associate { it.id to textMeasurer.measure(text = it.label, style = axisLabelStyle) }
    }
    // y 刻度宽度预估（按目标 yMax）：左内边距在过渡全程保持稳定，不随动画逐帧抖动
    val yTickWidthEstimate = remember(data.yMax, axisLabelStyle, valueFormatter, yTickCount) {
        (1..yTickCount).maxOf { i ->
            textMeasurer.measure(
                text = valueFormatter(data.yMax * i / yTickCount),
                style = axisLabelStyle,
            ).size.width
        }
    }
    val goalLabelLayout = remember(data.goalLine, axisLabelStyle, valueFormatter) {
        data.goalLine?.let { textMeasurer.measure(text = valueFormatter(it), style = axisLabelStyle) }
    }

    val chartModifier = if (contentDescription != null) {
        modifier.semantics { this.contentDescription = contentDescription }
    } else {
        modifier
    }

    Canvas(modifier = chartModifier) {
        val width = size.width
        val height = size.height
        if (width <= 0f || height <= 0f) return@Canvas

        // ── 绘图区布局 ──
        val labelGap = 8.dp.toPx()
        val leftPad = yTickWidthEstimate + labelGap
        val maxXLabelHeight = xLabelLayouts.values.maxOfOrNull { it.size.height }?.toFloat() ?: 0f
        val bottomPad = if (maxXLabelHeight > 0f) maxXLabelHeight + 6.dp.toPx() else 0f
        val plotLeft = leftPad
        val plotRight = width - 4.dp.toPx()
        val plotTop = 12.dp.toPx() // 目标线标签的顶部净空
        val plotBottom = height - bottomPad
        val plotWidth = plotRight - plotLeft
        val plotHeight = plotBottom - plotTop
        if (plotWidth <= 0f || plotHeight <= 0f) return@Canvas

        // 柱宽随槽数动画收放（防柱数突变时宽度跳变）；32dp 上限防宽屏小数据集胖柱
        val slotWidth = plotWidth / state.animatedSlotCount.value.coerceAtLeast(1f)
        val barWidth = min(slotWidth * barWidthFraction, 32.dp.toPx())

        // yMax 动画中仍可能被旧高柱/目标线超出：取 max 防中途裁剪；1f 兜底全零数据
        val yMaxAnim = state.animatedYMax.value
        val maxBarValue = state.items.values.maxOfOrNull { it.value.value } ?: 0f
        val effectiveMax = max(max(yMaxAnim, maxBarValue), max(data.goalLine ?: 0f, 1f))

        fun yFor(value: Float): Float = plotBottom - (value / effectiveMax) * plotHeight
        fun xFor(fraction: Float): Float = plotLeft + fraction * plotWidth

        // ── 1. 柱子（退场先画，压在进场柱之下） ──
        val (exitingBars, activeBars) = state.items.entries.partition { it.value.isExiting }
        for ((_, bar) in exitingBars + activeBars) {
            val alpha = bar.alpha.value
            if (alpha <= 0.01f) continue
            val barHeight = (bar.value.value / effectiveMax) * plotHeight
            if (barHeight <= 0.5f) continue
            val cx = xFor(bar.centerFraction.value)
            drawRoundRect(
                color = barColor,
                topLeft = Offset(cx - barWidth / 2, plotBottom - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(min(barWidth, barHeight) / 2),
                alpha = alpha,
            )
        }

        // ── 2. y 轴刻度（取动画中的 yMax；跳过 0 基线；逐帧现测，≤ 5 个廉价） ──
        val ticks = yTickCount.coerceAtLeast(1)
        for (i in 1..ticks) {
            val value = yMaxAnim * i / ticks
            val layout = textMeasurer.measure(text = valueFormatter(value), style = axisLabelStyle)
            drawText(
                textLayoutResult = layout,
                color = axisLabelColor,
                topLeft = Offset(
                    x = (plotLeft - labelGap - layout.size.width).coerceAtLeast(0f),
                    y = (yFor(value) - layout.size.height / 2f)
                        .coerceIn(0f, height - layout.size.height),
                ),
            )
        }

        // ── 3. 目标线（虚线 + 右上数值标签） ──
        data.goalLine?.takeIf { it > 0f }?.let { goal ->
            val y = yFor(goal)
            drawLine(
                color = goalLineColor,
                start = Offset(plotLeft, y),
                end = Offset(plotRight, y),
                strokeWidth = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)),
            )
            goalLabelLayout?.let { layout ->
                drawText(
                    textLayoutResult = layout,
                    color = goalLineColor,
                    topLeft = Offset(
                        x = (plotRight - layout.size.width).coerceAtLeast(plotLeft),
                        y = (y - layout.size.height - 4.dp.toPx()).coerceAtLeast(0f),
                    ),
                )
            }
        }

        // ── 4. x 标签（跟随柱子的动画槽位与透明度；按槽宽抽稀；退场柱不画） ──
        val maxLabelWidth = xLabelLayouts.values.maxOfOrNull { it.size.width }?.toFloat() ?: 0f
        val settledSlotWidth = plotWidth / data.entries.size.coerceAtLeast(1)
        val stride = if (maxLabelWidth <= 0f || settledSlotWidth <= 0f) {
            1
        } else {
            max(1, ceil((maxLabelWidth + 12.dp.toPx()) / settledSlotWidth).toInt())
        }
        for ((id, bar) in activeBars) {
            if (bar.targetIndex % stride != 0) continue
            val layout = xLabelLayouts[id] ?: continue
            val cx = xFor(bar.centerFraction.value)
            drawText(
                textLayoutResult = layout,
                color = axisLabelColor,
                alpha = bar.alpha.value,
                topLeft = Offset(
                    x = (cx - layout.size.width / 2f).coerceIn(0f, width - layout.size.width),
                    y = plotBottom + 4.dp.toPx(),
                ),
            )
        }
    }
}

// ──────────────────────────────────────
// 预览（LocalInspectionMode 播种，渲染定形态）
// ──────────────────────────────────────

/** 造一组确定性预览数据：值在 300..1900 间伪随机分布。 */
private fun previewChartData(barCount: Int, withGoalLine: Boolean = true): ChartData =
    ChartData(
        entries = (0 until barCount).map { i ->
            ChartEntry(
                id = "bar-$i",
                value = (300 + (i * 37 * 13) % 1600).toFloat(),
                label = "${i + 1}",
            )
        },
        yMax = 2000f,
        goalLine = if (withGoalLine) 1600f else null,
    )

/** 周视图形态：7 根胶囊柱 + 虚线目标线。 */
@Preview(showBackground = true)
@Composable
private fun AnimatedBarChartWeekPreview() {
    FitLogTheme {
        AnimatedBarChart(
            data = previewChartData(7),
            modifier = Modifier.fillMaxWidth().height(240.dp),
        )
    }
}

/** 月视图形态：30 根柱，x 标签自动抽稀。 */
@Preview(showBackground = true)
@Composable
private fun AnimatedBarChartMonthPreview() {
    FitLogTheme {
        AnimatedBarChart(
            data = previewChartData(30, withGoalLine = false),
            modifier = Modifier.fillMaxWidth().height(240.dp),
        )
    }
}

/** 年视图形态：12 根柱（32dp 柱宽上限生效）。 */
@Preview(showBackground = true)
@Composable
private fun AnimatedBarChartYearPreview() {
    FitLogTheme {
        AnimatedBarChart(
            data = previewChartData(12, withGoalLine = false),
            modifier = Modifier.fillMaxWidth().height(240.dp),
        )
    }
}

/** 空数据形态：仅刻度区，无柱。 */
@Preview(showBackground = true)
@Composable
private fun AnimatedBarChartEmptyPreview() {
    FitLogTheme {
        AnimatedBarChart(
            data = ChartData(entries = emptyList(), yMax = 1000f),
            modifier = Modifier.fillMaxWidth().height(240.dp),
        )
    }
}

/** 暗色主题形态。 */
@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AnimatedBarChartDarkPreview() {
    FitLogTheme {
        AnimatedBarChart(
            data = previewChartData(7),
            modifier = Modifier.fillMaxWidth().height(240.dp),
        )
    }
}
