package com.example.fitlog.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.fitlog.ui.components.chart.ChartData
import com.example.fitlog.ui.components.chart.ChartEntry
import com.example.fitlog.ui.components.chart.MiniBarChart
import com.example.fitlog.ui.components.chart.MiniLineChart
import com.example.fitlog.ui.components.chart.MiniLineStyle
import com.example.fitlog.ui.theme.FitLogTheme

/**
 * 指标图表卡片的图表区内容（柱状或折线）。
 *
 * 纯值语义契约同 [ChartData]：不得持有 lambda（[ChartData] 实例是迷你图表
 * `LaunchedEffect` 的 key）；[Dp] 是 inline value class，可安全持有。
 */
sealed interface MetricChart {
    /** 迷你胶囊柱状图（[ChartData.goalLine] 呈现为细参考线）。 */
    data class Bars(val data: ChartData) : MetricChart

    /**
     * 迷你折线图（圆点标记，[ChartData.yMin]/[ChartData.yMax] 自动区间）。
     *
     * @property style 描边样式；空数据占位用 [MiniLineStyle.Dotted] 并把
     *   [lineWidth] 提到 3.dp（Dotted 点径 = 线宽，默认 2.dp 会细如针尖）
     */
    data class Line(
        val data: ChartData,
        val style: MiniLineStyle = MiniLineStyle.Solid,
        val lineWidth: Dp = 2.dp,
    ) : MetricChart
}

/**
 * 指标图表卡片的完整展示状态（Samsung Health「Key metrics」小卡形态）。
 *
 * 纯值语义契约：不得持有 lambda——[MetricChart] 内的 [ChartData] 是迷你图表
 * `LaunchedEffect` 的 key，lambda 会让动画状态机在重组时误重启；
 * 点击回调走 [MetricChartCard] 的 onClick 参数。
 *
 * 各字段缺省即隐藏对应区域，可组合出参考图中的全部形态：
 * 数据卡（Energy burned）、稀疏折线卡（Weight）、空态卡（"No data" + 空轨道
 * + 点状平线/无图表 + "No data" pill）。
 *
 * @property title 指标名（如 "Energy burned"）
 * @property valueText 大字体数值（如 "985 cal"；"No data" 也走这里，组件无特判）
 * @property chart 图表区内容；null = 无图表（如 Calories intake 空态卡）
 * @property xLabels 卡片级 x 标签行（与图表槽位按 weight 对齐；无抽稀，建议 ≤ 12 个短标签）
 * @property todayIndex 高亮 [xLabels] 中「今天」的索引（胶囊底色），越界自动忽略
 * @property goalTrackProgress 粗目标进度轨（0..1，卡片级行）；null = 无轨道行，0f = 空轨道
 * @property statusText 左下状态 pill 文案（如 "1,815 cal left" / "No data"）
 * @property accentColor 强调色：驱动柱/折线/目标线/轨道/今日 pill/状态 pill 配色；
 *   null 取主题 primary。放在状态里（而非参数）是为了 [MetricChartCardGrid] 支持异色卡片混排；
 *   由屏幕/预览层注入（ViewModel 不读 MaterialTheme）
 */
data class MetricChartCardState(
    val title: String,
    val valueText: String,
    val chart: MetricChart? = null,
    val xLabels: List<String> = emptyList(),
    val todayIndex: Int? = null,
    val goalTrackProgress: Float? = null,
    val statusText: String? = null,
    val accentColor: Color? = null,
)

/**
 * 指标图表卡片：标题 + 大数值 + 可选目标进度轨 + 迷你图表 + x 标签行 + 状态 pill。
 *
 * 布局自上而下（可选区域缺省即隐藏）：
 * 1. 头部：title（小字灰）/ valueText（大字粗）
 * 2. [GoalTrack] 粗目标进度轨（[MetricChartCardState.goalTrackProgress] 非 null 时）
 * 3. 图表槽：[MiniBarChart] / [MiniLineChart]（[MetricChartCardState.chart] 非 null 时）
 * 4. x 标签行：[MetricChartCardState.xLabels] 非空时
 * 5. 状态 pill：[MetricChartCardState.statusText] 非 null 时
 *
 * x 标签行为何放在卡片层（而非画进图表 Canvas）：迷你卡的粒度固定，槽位不动，
 * 标签无需随数据点动画；卡片级可用 Surface/Text 轻易实现今日高亮与无障碍；
 * 且 [MetricChartCardState.chart] 为 null 时标签行依然存在（Calories intake 空态形态）。
 *
 * @param state 卡片展示状态（纯值语义，见 [MetricChartCardState]）
 * @param modifier 修饰符
 * @param onClick 点击回调；null 不可点击（同 LargeMetricCard 先例）
 * @param chartHeight 图表区高度
 */
@Composable
fun MetricChartCard(
    state: MetricChartCardState,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    chartHeight: Dp = 72.dp,
) {
    val accent = state.accentColor ?: MaterialTheme.colorScheme.primary
    val cardModifier = if (onClick != null) {
        modifier.clickable(onClick = onClick)
    } else {
        modifier
    }

    FitLogCard(
        modifier = cardModifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 头部：标题 + 大数值
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = state.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Text(
                text = state.valueText,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // 粗目标进度轨（卡片级行；细参考线由图表内部绘制，见 ChartData.goalLine）
        state.goalTrackProgress?.let { progress ->
            GoalTrack(progress = progress, fillColor = accent)
        }

        // 图表槽
        state.chart?.let { chart ->
            when (chart) {
                is MetricChart.Bars -> MiniBarChart(
                    data = chart.data,
                    modifier = Modifier.fillMaxWidth().height(chartHeight),
                    barColor = accent,
                    goalLineColor = accent,
                )
                is MetricChart.Line -> MiniLineChart(
                    data = chart.data,
                    modifier = Modifier.fillMaxWidth().height(chartHeight),
                    lineColor = accent,
                    goalLineColor = accent,
                    lineStyle = chart.style,
                    lineWidth = chart.lineWidth,
                )
            }
        }

        // x 标签行（weight 格子与上方 Canvas 槽位天然对齐）
        if (state.xLabels.isNotEmpty()) {
            ChartXLabelRow(
                labels = state.xLabels,
                todayIndex = state.todayIndex,
                accent = accent,
            )
        }

        // 状态 pill
        state.statusText?.let { text ->
            StatusPill(text = text, accent = accent)
        }
    }
}

/**
 * 两列等高的指标卡片网格。
 *
 * `cards.chunked(2)` 逐行排布；行用 [IntrinsicSize.Max] 保证同行两卡等高
 * （MetricDashboardGrid 先例）。奇数尾行补 [Spacer] weight 防止单卡拉伸满宽。
 *
 * 不用 LazyVerticalGrid：网格常嵌在 verticalScroll 页面内（惰性网格在滚动容器里
 * 测量错误），且卡片数量有限（6-8 张）无需懒加载。
 *
 * @param cards 卡片状态列表（支持异色 accent 混排）
 * @param modifier 修饰符
 * @param onCardClick 卡片点击回调（携被点卡片的状态）；null 全部不可点击
 * @param horizontalSpacing 列间距
 * @param verticalSpacing 行间距
 */
@Composable
fun MetricChartCardGrid(
    cards: List<MetricChartCardState>,
    modifier: Modifier = Modifier,
    onCardClick: ((MetricChartCardState) -> Unit)? = null,
    horizontalSpacing: Dp = 12.dp,
    verticalSpacing: Dp = 12.dp,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(verticalSpacing),
    ) {
        cards.chunked(2).forEach { rowCards ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(horizontalSpacing),
            ) {
                rowCards.forEach { cardState ->
                    MetricChartCard(
                        state = cardState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        onClick = onCardClick?.let { click -> { click(cardState) } },
                    )
                }
                if (rowCards.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * 卡片级 x 标签行：每个标签独占 weight(1f) 格子（与图表槽位中心对齐），
 * [todayIndex] 命中的格子渲染强调色胶囊底。固定 24dp 行高，胶囊不外扩挤压邻居。
 */
@Composable
private fun ChartXLabelRow(
    labels: List<String>,
    todayIndex: Int?,
    accent: Color,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        labels.forEachIndexed { index, label ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (todayIndex != null && index == todayIndex) {
                    Surface(
                        shape = CircleShape,
                        color = accent.copy(alpha = 0.15f),
                        contentColor = accent,
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                } else {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** 左下状态 pill：12% 强调色底 + 强调色文案。 */
@Composable
private fun StatusPill(
    text: String,
    accent: Color,
) {
    Surface(
        shape = CircleShape,
        color = accent.copy(alpha = 0.12f),
        contentColor = accent,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

// ──────────────────────────────────────
// 预览：四种 Samsung Health 类比形态 + 网格 + 暗色
// ──────────────────────────────────────

private val weekLabels = listOf("S", "M", "T", "W", "T", "F", "S")

private val tealAccent = Color(0xFF2BB5A0)

/** Energy burned 类比：柱状 + 细目标参考线 + 今日高亮 + 剩余量 pill。 */
private fun energyBurnedState() = MetricChartCardState(
    title = "Energy burned",
    valueText = "985 cal",
    chart = MetricChart.Bars(
        ChartData(
            entries = weekLabels.mapIndexed { i, label ->
                ChartEntry(id = "d$i", value = listOf(620f, 0f, 980f, 1140f, 1050f, 730f, 450f)[i], label = label)
            },
            yMax = 1200f,
            goalLine = 1100f,
        ),
    ),
    xLabels = weekLabels,
    todayIndex = 6,
    statusText = "1,815 cal left",
    accentColor = tealAccent,
)

/** Weight 类比：折线 + 非零自动区间 + 月标签（无目标线/轨道/pill）。 */
private fun weightState() = MetricChartCardState(
    title = "Weight",
    valueText = "74 kg",
    chart = MetricChart.Line(
        ChartData(
            entries = listOf("Apr", "May", "Jun", "Jul").mapIndexed { i, label ->
                ChartEntry(id = "m$i", value = listOf(74.8f, 74.2f, 73.6f, 74f)[i], label = label)
            },
            yMax = 76f,
            yMin = 72f,
        ),
    ),
    xLabels = listOf("Apr", "May", "Jun", "Jul"),
    todayIndex = 3,
    accentColor = tealAccent,
)

/** Carbs 空态类比：空轨道 + Dotted 点状平线 + "No data"。 */
@Composable
private fun carbsEmptyState() = MetricChartCardState(
    title = "Carbs",
    valueText = "No data",
    chart = MetricChart.Line(
        data = ChartData(
            entries = weekLabels.mapIndexed { i, label ->
                ChartEntry(id = "d$i", value = 0f, label = label)
            },
            yMax = 0f,
            yMin = 0f,
        ),
        style = MiniLineStyle.Dotted,
        lineWidth = 3.dp,
    ),
    xLabels = weekLabels,
    todayIndex = 6,
    goalTrackProgress = 0f,
    statusText = "No data",
    accentColor = MaterialTheme.colorScheme.outline,
)

/** Calories intake 空态类比：空轨道 + 无图表（标签行仍在）。 */
@Composable
private fun caloriesIntakeEmptyState() = MetricChartCardState(
    title = "Calories intake",
    valueText = "No data",
    chart = null,
    xLabels = weekLabels,
    todayIndex = 6,
    goalTrackProgress = 0f,
    statusText = "No data",
    accentColor = MaterialTheme.colorScheme.outline,
)

/** 数据卡形态（Energy burned 类比）。 */
@Preview(showBackground = true)
@Composable
private fun MetricChartCardEnergyPreview() {
    FitLogTheme {
        MetricChartCard(
            state = energyBurnedState(),
            modifier = Modifier.padding(16.dp),
        )
    }
}

/** 稀疏折线卡形态（Weight 类比）。 */
@Preview(showBackground = true)
@Composable
private fun MetricChartCardWeightPreview() {
    FitLogTheme {
        MetricChartCard(
            state = weightState(),
            modifier = Modifier.padding(16.dp),
        )
    }
}

/** 空态卡形态（Carbs 类比：空轨道 + 点状平线）。 */
@Preview(showBackground = true)
@Composable
private fun MetricChartCardEmptyPreview() {
    FitLogTheme {
        MetricChartCard(
            state = carbsEmptyState(),
            modifier = Modifier.padding(16.dp),
        )
    }
}

/** 2×2 网格：四种形态混排（含无图表卡与异色 accent）。 */
@Preview(showBackground = true)
@Composable
private fun MetricChartCardGridPreview() {
    FitLogTheme {
        MetricChartCardGrid(
            cards = listOf(
                weightState(),
                energyBurnedState(),
                caloriesIntakeEmptyState(),
                carbsEmptyState(),
            ),
            modifier = Modifier.padding(16.dp),
        )
    }
}

/** 暗色主题网格。 */
@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MetricChartCardGridDarkPreview() {
    FitLogTheme {
        MetricChartCardGrid(
            cards = listOf(
                weightState(),
                energyBurnedState(),
                caloriesIntakeEmptyState(),
                carbsEmptyState(),
            ),
            modifier = Modifier.padding(16.dp),
        )
    }
}
