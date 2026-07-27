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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.fitlog.ui.theme.FitLogTheme
import kotlin.math.max
import kotlin.math.min

/**
 * 迷你折线的描边样式。
 */
enum class MiniLineStyle {
    /** 实线（常规趋势） */
    Solid,

    /** 长虚线（12 on / 10 off，同全尺寸图表的目标线 dash） */
    Dashed,

    /**
     * 圆点虚线（点径 = 线宽，空数据占位美学）。
     * 配合全零数据（yMin == yMax）呈现为垂直居中的点状平线；
     * 占位场景建议 [MiniLineChart] 的 lineWidth 提到 3.dp，否则点如针尖。
     */
    Dotted,
}

/**
 * 迷你折线图：折线 + 圆点标记，供指标小卡片（如 [com.example.fitlog.ui.components.MetricChartCard]）
 * 展示连续型指标（体重、心率等）。
 *
 * 与全尺寸 [AnimatedBarChart] 共用同一份 [ChartData] 与 [AnimatedChartState] 状态机
 * （同包直接复用 internal 成员）：数据更换时同 id 点滑行变形（折线逐帧随之形变）、
 * 新点错峰生长、旧点原地消散。差异仅在绘制层：
 *
 * - **y 轴区间**：[ChartData.yMin]/[ChartData.yMax] 自动裁剪（体重 70-80kg 类数据不必从 0 起）；
 *   区间取动画中的 yMin/yMax 与当前点值、目标线的并集，过渡中途不裁剪
 * - **退化区间兜底**：span ≈ 0（全等值 / 空态占位）时所有点画在垂直居中——
 *   配合 [MiniLineStyle.Dotted] 即 Samsung 风格的「无数据」点状平线
 * - **路径透明度 = 所有在场点 alpha 的最小值**：错峰入场期间折线整体淡入，
 *   圆点标记左→右依次弹出承担入场节奏（单条 Path 只有一个透明度，此为刻意取舍）
 * - 退场点不参与路径，仅以当前位置/透明度画孤点直至消失
 * - 目标线为 1.5dp 全宽实线（无 dash、无数值标签），取 [ChartData.goalLine]；
 *   与卡片级进度轨道 [com.example.fitlog.ui.components.GoalTrack] 是两种不同的目标语义
 * - 无 x 标签（由卡片层以 composable Row 实现）
 *
 * 边界形态：0 点 = 仅目标线；1 点 = 仅圆点（体重卡「单点」形态）。
 * yMin > 0 时入场点从画布下缘升起（裁剪下自然；不做 y clamp——
 * clamp 会产生「钉在底边再弹出」的伪影）。
 *
 * @param data 图表数据（纯值语义契约见 [ChartData]）
 * @param modifier 修饰符（调用方负责定高）
 * @param state 动画状态持有者；默认运行时生长入场、Preview 渲染定形态
 * @param lineColor 折线颜色
 * @param markerColor 圆点标记颜色
 * @param goalLineColor 目标线颜色
 * @param lineStyle 描边样式（实线/虚线/圆点虚线）
 * @param lineWidth 线宽（Dotted 时点径 = 线宽）
 * @param markerRadius 圆点标记半径
 * @param animationSpec 变形/入场动画规格
 * @param exitSpec 退场动画规格
 * @param maxStaggerMillis 入场错峰总时长
 * @param contentDescription 无障碍摘要；null 则图表对 TalkBack 透明
 */
@Composable
fun MiniLineChart(
    data: ChartData,
    modifier: Modifier = Modifier,
    state: AnimatedChartState = rememberAnimatedChartState(
        // Preview 不执行 LaunchedEffect，播种定形态避免空图；运行时传 null 生长入场
        if (LocalInspectionMode.current) data else null,
    ),
    lineColor: Color = MaterialTheme.colorScheme.primary,
    markerColor: Color = lineColor,
    goalLineColor: Color = MaterialTheme.colorScheme.tertiary,
    lineStyle: MiniLineStyle = MiniLineStyle.Solid,
    lineWidth: Dp = 2.dp,
    markerRadius: Dp = 3.dp,
    animationSpec: AnimationSpec<Float> = tween(450, easing = FastOutSlowInEasing),
    exitSpec: AnimationSpec<Float> = tween(250),
    maxStaggerMillis: Int = 240,
    contentDescription: String? = null,
) {
    LaunchedEffect(data) {
        state.animateTo(data, animationSpec, exitSpec, maxStaggerMillis)
    }

    val linePath = remember { Path() }

    val chartModifier = if (contentDescription != null) {
        modifier.semantics { this.contentDescription = contentDescription }
    } else {
        modifier
    }

    Canvas(modifier = chartModifier) {
        val width = size.width
        val height = size.height
        if (width <= 0f || height <= 0f) return@Canvas

        // 绘图区：上下内缩，保证圆点/描边不被裁切；槽位中心已自带半槽左右内缩
        val lineWidthPx = lineWidth.toPx()
        val markerRadiusPx = markerRadius.toPx()
        val insetY = max(markerRadiusPx, lineWidthPx / 2)
        val plotTop = insetY
        val plotBottom = height - insetY
        val plotHeight = plotBottom - plotTop
        if (plotHeight <= 0f) return@Canvas

        // 在场点按动画中的槽位排序（≤30 项，逐帧排序开销可忽略）；退场点只画孤点
        val (exitingPoints, activePoints) = state.items.values
            .partition { it.isExiting }
        val active = activePoints.sortedBy { it.centerFraction.value }

        // 区间 = 动画中的 yMin/yMax 与当前点值、目标线的并集（过渡中途不裁剪）
        val goal = data.goalLine?.takeIf { it > 0f }
        val activeMax = active.maxOfOrNull { it.value.value } ?: Float.NEGATIVE_INFINITY
        val activeMin = active.minOfOrNull { it.value.value } ?: Float.POSITIVE_INFINITY
        val effectiveMax = max(state.animatedYMax.value, max(activeMax, goal ?: Float.NEGATIVE_INFINITY))
        val effectiveMin = min(state.animatedYMin.value, min(activeMin, goal ?: Float.POSITIVE_INFINITY))
        val span = effectiveMax - effectiveMin

        // 退化区间（全等值/空态）：垂直居中——空态点状平线的几何来源
        fun yFor(value: Float): Float = if (span <= 0.0001f) {
            (plotTop + plotBottom) / 2
        } else {
            plotBottom - ((value - effectiveMin) / span) * plotHeight
        }
        fun xFor(fraction: Float): Float = fraction * width

        // ── 1. 细目标参考线（压在折线之下） ──
        goal?.let {
            drawLine(
                color = goalLineColor,
                start = Offset(0f, yFor(it)),
                end = Offset(width, yFor(it)),
                strokeWidth = 1.5.dp.toPx(),
            )
        }

        // ── 2. 折线路径（≥2 个在场点；整体透明度 = 点 alpha 最小值） ──
        if (active.size >= 2) {
            linePath.rewind()
            active.forEachIndexed { index, point ->
                val x = xFor(point.centerFraction.value)
                val y = yFor(point.value.value)
                if (index == 0) linePath.moveTo(x, y) else linePath.lineTo(x, y)
            }
            val pathEffect = when (lineStyle) {
                MiniLineStyle.Solid -> null
                MiniLineStyle.Dashed -> PathEffect.dashPathEffect(floatArrayOf(12f, 10f))
                MiniLineStyle.Dotted -> PathEffect.dashPathEffect(floatArrayOf(0.1f, lineWidthPx * 2f))
            }
            drawPath(
                path = linePath,
                color = lineColor,
                alpha = active.minOf { it.alpha.value },
                style = Stroke(
                    width = lineWidthPx,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                    pathEffect = pathEffect,
                ),
            )
        }

        // ── 3. 圆点标记（逐点透明度，错峰入场节奏由标记承担） ──
        for (point in active) {
            if (point.alpha.value <= 0.01f) continue
            drawCircle(
                color = markerColor,
                radius = markerRadiusPx,
                center = Offset(xFor(point.centerFraction.value), yFor(point.value.value)),
                alpha = point.alpha.value,
            )
        }

        // ── 4. 退场孤点（不进路径，原地淡出） ──
        for (point in exitingPoints) {
            if (point.alpha.value <= 0.01f) continue
            drawCircle(
                color = markerColor,
                radius = markerRadiusPx,
                center = Offset(xFor(point.centerFraction.value), yFor(point.value.value)),
                alpha = point.alpha.value,
            )
        }
    }
}

// ──────────────────────────────────────
// 预览（LocalInspectionMode 播种，渲染定形态）
// ──────────────────────────────────────

/** 周趋势形态：7 点波动折线 + 细目标线。 */
@Preview(showBackground = true)
@Composable
private fun MiniLineChartWeekPreview() {
    FitLogTheme {
        MiniLineChart(
            data = ChartData(
                entries = listOf("S", "M", "T", "W", "T", "F", "S").mapIndexed { i, label ->
                    ChartEntry(id = "d$i", value = listOf(620f, 700f, 560f, 890f, 760f, 940f, 810f)[i], label = label)
                },
                yMax = 1000f,
                goalLine = 850f,
            ),
            lineColor = Color(0xFF2BB5A0),
            goalLineColor = Color(0xFF2BB5A0),
            modifier = Modifier.fillMaxWidth().height(72.dp),
        )
    }
}

/** 单点形态：体重卡「只有一个记录」的孤点（yMin/yMax 非零区间）。 */
@Preview(showBackground = true)
@Composable
private fun MiniLineChartSinglePointPreview() {
    FitLogTheme {
        MiniLineChart(
            data = ChartData(
                entries = listOf(ChartEntry(id = "2026-07", value = 74f, label = "Jul")),
                yMax = 76f,
                yMin = 72f,
            ),
            lineColor = Color(0xFF2BB5A0),
            modifier = Modifier.fillMaxWidth().height(72.dp),
        )
    }
}

/** 空态占位：全零数据 + Dotted + 3dp 线宽 → 垂直居中的点状平线。 */
@Preview(showBackground = true)
@Composable
private fun MiniLineChartEmptyPlaceholderPreview() {
    FitLogTheme {
        MiniLineChart(
            data = ChartData(
                entries = listOf("S", "M", "T", "W", "T", "F", "S").mapIndexed { i, label ->
                    ChartEntry(id = "d$i", value = 0f, label = label)
                },
                yMax = 0f,
                yMin = 0f,
            ),
            lineColor = Color(0xFF2BB5A0),
            lineStyle = MiniLineStyle.Dotted,
            lineWidth = 3.dp,
            markerRadius = 3.5.dp,
            modifier = Modifier.fillMaxWidth().height(72.dp),
        )
    }
}

/** 暗色主题形态。 */
@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MiniLineChartDarkPreview() {
    FitLogTheme {
        MiniLineChart(
            data = ChartData(
                entries = listOf("S", "M", "T", "W", "T", "F", "S").mapIndexed { i, label ->
                    ChartEntry(id = "d$i", value = listOf(620f, 700f, 560f, 890f, 760f, 940f, 810f)[i], label = label)
                },
                yMax = 1000f,
                goalLine = 850f,
            ),
            lineColor = Color(0xFF2BB5A0),
            goalLineColor = Color(0xFF2BB5A0),
            modifier = Modifier.fillMaxWidth().height(72.dp),
        )
    }
}
