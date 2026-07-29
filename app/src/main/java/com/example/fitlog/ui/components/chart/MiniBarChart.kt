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
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.fitlog.ui.theme.FitLogTheme
import kotlin.math.max
import kotlin.math.min

/**
 * 迷你柱状图：无边距全宽胶囊柱，供指标小卡片（如 [com.example.fitlog.ui.components.MetricChartCard]）
 * 在有限高度（~72dp）内展示趋势。
 *
 * 与全尺寸 [AnimatedBarChart] 共用同一份 [ChartData] 与 [AnimatedChartState] 状态机
 * （同包直接复用 internal 成员）：数据更换时同 id 柱滑行变形、新柱错峰生长、
 * 旧柱原地消散。差异仅在绘制层：
 *
 * - 无 y 轴刻度、无 x 标签（x 标签行由卡片层以 composable Row 实现，便于今日高亮与无障碍）
 * - 柱宽上限 24dp（迷你尺度），画布全宽即绘图区
 * - 目标线为 1.5dp 全宽实线（无 dash、无数值标签），取 [ChartData.goalLine]；
 *   与卡片级进度轨道 [com.example.fitlog.ui.components.GoalTrack] 是两种不同的目标语义
 * - [ChartData.yMin] 被忽略（柱子始终从 0 基线生长）
 *
 * @param data 图表数据（纯值语义契约见 [ChartData]）
 * @param modifier 修饰符（调用方负责定高）
 * @param state 动画状态持有者；默认运行时生长入场、Preview 渲染定形态
 * @param barColor 柱色
 * @param goalLineColor 目标线颜色
 * @param barWidthFraction 柱宽占槽宽比例（0..1）
 * @param animationSpec 变形/入场动画规格
 * @param exitSpec 退场动画规格
 * @param maxStaggerMillis 入场错峰总时长
 * @param contentDescription 无障碍摘要；null 则图表对 TalkBack 透明
 */
@Composable
fun MiniBarChart(
    data: ChartData,
    modifier: Modifier = Modifier,
    state: AnimatedChartState = rememberAnimatedChartState(
        // Preview 不执行 LaunchedEffect，播种定形态避免空图；运行时传 null 生长入场
        if (LocalInspectionMode.current) data else null,
    ),
    barColor: Color = MaterialTheme.colorScheme.primary,
    goalLineColor: Color = MaterialTheme.colorScheme.tertiary,
    barWidthFraction: Float = 0.6f,
    animationSpec: AnimationSpec<Float> = tween(450, easing = FastOutSlowInEasing),
    exitSpec: AnimationSpec<Float> = tween(250),
    maxStaggerMillis: Int = 240,
    contentDescription: String? = null,
) {
    LaunchedEffect(data) {
        state.animateTo(data, animationSpec, exitSpec, maxStaggerMillis)
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

        // 绘图区：全宽；顶部仅留目标线半线宽净空，柱子落在画布底边
        val plotTop = 1.dp.toPx()
        val plotBottom = height
        val plotHeight = plotBottom - plotTop
        if (plotHeight <= 0f) return@Canvas

        // 柱宽随槽数动画收放；24dp 上限（迷你尺度）
        val slotWidth = width / state.animatedSlotCount.value.coerceAtLeast(1f)
        val barWidth = min(slotWidth * barWidthFraction, 24.dp.toPx())

        // yMax 动画中仍可能被旧高柱/目标线超出：取 max 防中途裁剪；1f 兜底全零数据
        val yMaxAnim = state.animatedYMax.value
        val maxBarValue = state.items.values.maxOfOrNull { it.value.value } ?: 0f
        val effectiveMax = max(max(yMaxAnim, maxBarValue), max(data.goalLine ?: 0f, 1f))

        fun yFor(value: Float): Float = plotBottom - (value / effectiveMax) * plotHeight

        // ── 1. 柱子（退场先画，压在进场柱之下） ──
        val (exitingBars, activeBars) = state.items.entries.partition { it.value.isExiting }
        for ((_, bar) in exitingBars + activeBars) {
            val alpha = bar.alpha.value
            if (alpha <= 0.01f) continue
            val barHeight = (bar.value.value / effectiveMax) * plotHeight
            if (barHeight <= 0.5f) continue
            val cx = bar.centerFraction.value * width
            drawRoundRect(
                color = barColor,
                topLeft = Offset(cx - barWidth / 2, plotBottom - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(min(barWidth, barHeight) / 2),
                alpha = alpha,
            )
        }

        // ── 2. 细目标参考线（1.5dp 全宽实线，无数值标签） ──
        data.goalLine?.takeIf { it > 0f }?.let { goal ->
            drawLine(
                color = goalLineColor,
                start = Offset(0f, yFor(goal)),
                end = Offset(width, yFor(goal)),
                strokeWidth = 1.5.dp.toPx(),
            )
        }
    }
}

// ──────────────────────────────────────
// 预览（LocalInspectionMode 播种，渲染定形态）
// ──────────────────────────────────────

/** 周视图形态：7 根胶囊柱 + 细目标线（Energy burned 卡样式）。 */
@Preview(showBackground = true)
@Composable
private fun MiniBarChartWeekPreview() {
    FitLogTheme {
        MiniBarChart(
            data = ChartData(
                entries = listOf("S", "M", "T", "W", "T", "F", "S").mapIndexed { i, label ->
                    ChartEntry(id = "d$i", value = listOf(620f, 0f, 980f, 1140f, 1050f, 730f, 450f)[i], label = label)
                },
                yMax = 1200f,
                goalLine = 1100f,
            ),
            barColor = Color(0xFF2BB5A0),
            goalLineColor = Color(0xFF2BB5A0),
            modifier = Modifier.fillMaxWidth().height(72.dp),
        )
    }
}

/** 密集形态：30 根柱（槽宽收放动画的定形态）。 */
@Preview(showBackground = true)
@Composable
private fun MiniBarChartMonthPreview() {
    FitLogTheme {
        MiniBarChart(
            data = ChartData(
                entries = (1..30).map { i ->
                    ChartEntry(id = "d$i", value = (200 + (i * 37 * 13) % 900).toFloat(), label = "$i")
                },
                yMax = 1200f,
            ),
            barColor = Color(0xFF2BB5A0),
            modifier = Modifier.fillMaxWidth().height(72.dp),
        )
    }
}

/** 暗色主题形态。 */
@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MiniBarChartDarkPreview() {
    FitLogTheme {
        MiniBarChart(
            data = ChartData(
                entries = listOf("S", "M", "T", "W", "T", "F", "S").mapIndexed { i, label ->
                    ChartEntry(id = "d$i", value = listOf(620f, 0f, 980f, 1140f, 1050f, 730f, 450f)[i], label = label)
                },
                yMax = 1200f,
                goalLine = 1100f,
            ),
            barColor = Color(0xFF2BB5A0),
            goalLineColor = Color(0xFF2BB5A0),
            modifier = Modifier.fillMaxWidth().height(72.dp),
        )
    }
}
