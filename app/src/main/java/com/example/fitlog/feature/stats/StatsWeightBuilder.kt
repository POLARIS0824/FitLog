package com.example.fitlog.feature.stats

import com.example.fitlog.model.BodyMetric
import com.example.fitlog.ui.components.chart.ChartData
import com.example.fitlog.ui.components.chart.ChartEntry
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.max

/**
 * Stats 体重卡的数据构建器（纯函数对象，无 Android 依赖，JVM 可测）。
 *
 * 把 body_metrics 记录聚合为体重折线卡的输入 [StatsWeightState]：
 *
 * - **窗口**：近 [WINDOW_DAYS] 天；**点数**：取最近 [MAX_POINTS] 条记录——
 *   MiniLineChart 逐帧排序的舒适区上限是 ~30 点（见其绘制注释）
 * - **y 区间**：span × 15% 留白，下限 ±0.8kg（单条记录 span=0 时不退化成平线贴边）；
 *   折线渲染器按 yMin/yMax 自动裁剪（体重不必从 0 起）
 * - **x 标签**：与 entries 等长（MetricChartCard 标签行按 weight 与槽位对齐、无抽稀），
 *   step = ceil(n/6) 抽稀、末位必标、空位 ""
 * - **空态**：7 个全零 entries + yMin==yMax==0——配合 MiniLineStyle.Dotted 即
 *   Samsung 风格的点状平线占位（MetricChartCard 预览惯例）
 */
object StatsWeightBuilder {

    /** 查询窗口天数（近 90 天）。 */
    const val WINDOW_DAYS = 90L

    /** 折线点数上限（MiniLineChart 逐帧排序的舒适区）。 */
    const val MAX_POINTS = 30

    /** x 标签可见个数上限。 */
    private const val MAX_VISIBLE_LABELS = 6

    /** 空态占位的全零点数。 */
    private const val EMPTY_PLACEHOLDER_POINTS = 7

    /**
     * 窗口起始日（含），供 Repository 取数。
     *
     * @param today 窗口锚点（含）
     */
    fun windowStart(today: LocalDate): LocalDate = today.minusDays(WINDOW_DAYS - 1)

    /**
     * 构建体重卡状态。
     *
     * @param metrics 体重记录（[windowStart] 区间查询结果，日期升序由 DAO 保证，
     *   此处仍排序防御）；同日重复记录已按天去重（date 为 PK）
     * @param today 锚定「今天」（测试注入，保证确定性）
     */
    fun build(metrics: List<BodyMetric>, today: LocalDate): StatsWeightState {
        val sorted = metrics
            .filter { !it.date.isBefore(windowStart(today)) && !it.date.isAfter(today) }
            .sortedBy { it.date }
            .takeLast(MAX_POINTS)

        if (sorted.isEmpty()) return emptyState()

        val latest = sorted.last()
        val deltaText = if (sorted.size >= 2) {
            val delta = latest.weightKg - sorted[sorted.size - 2].weightKg
            "较上次 %+.1f kg".format(delta)
        } else {
            null
        }

        val values = sorted.map { it.weightKg }
        val min = values.min()
        val max = values.max()
        val pad = max((max - min) * 0.15f, 0.8f)

        return StatsWeightState(
            hasData = true,
            valueText = "%.1f kg".format(latest.weightKg),
            deltaText = deltaText,
            chartData = ChartData(
                entries = sorted.map { metric ->
                    ChartEntry(
                        id = metric.date.toString(),
                        value = metric.weightKg,
                        label = "${metric.date.monthValue}/${metric.date.dayOfMonth}",
                    )
                },
                yMax = max + pad,
                yMin = min - pad,
            ),
            xLabels = sparseLabels(sorted.size) { i ->
                "${sorted[i].date.monthValue}/${sorted[i].date.dayOfMonth}"
            },
        )
    }

    /** 空态：Dotted 平线占位（全零点 + 退化区间），引导文案由 Screen 层决定。 */
    private fun emptyState(): StatsWeightState = StatsWeightState(
        hasData = false,
        valueText = "暂无记录",
        deltaText = null,
        chartData = ChartData(
            entries = (1..EMPTY_PLACEHOLDER_POINTS).map { i ->
                ChartEntry(id = "empty-$i", value = 0f, label = "")
            },
            yMax = 0f,
            yMin = 0f,
        ),
        xLabels = emptyList(),
    )

    /**
     * 等长抽稀标签：可见数 ≤ [MAX_VISIBLE_LABELS]，首位与末位必标，其余空位 ""。
     * 步长按 (MAX-1) 等分，保证网格标签 + 末位标签合计不超限。
     *
     * @param count 数据点数
     * @param labelOf 第 i 点的完整标签
     */
    private fun sparseLabels(count: Int, labelOf: (Int) -> String): List<String> {
        if (count <= 0) return emptyList()
        val step = ceil(count.toDouble() / (MAX_VISIBLE_LABELS - 1)).toInt().coerceAtLeast(1)
        return (0 until count).map { i ->
            if (i % step == 0 || i == count - 1) labelOf(i) else ""
        }
    }
}
