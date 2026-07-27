package com.example.fitlog.feature.stats

import com.example.fitlog.ui.components.chart.ChartData

/**
 * 统计周期档位。
 *
 * 分桶粒度随档位变化：周/月档按日聚合，三月档按周聚合（周一始），年档按自然月聚合；
 * 各档窗口均截至今天，最末桶为不完整桶。桶 id 命名空间按档分级
 * （日桶 ISO 日期 / 周桶周一日期 / 月桶 YearMonth），是 AnimatedBarChart
 * 跨档切换插值动画的匹配锚点（见 [StatsChartDataBuilder]）。
 */
enum class StatsPeriod(val label: String) {
    /** 最近 7 天（按日分桶） */
    WEEK("周"),

    /** 最近 30 天（按日分桶） */
    MONTH("月"),

    /** 最近 13 周（按周分桶，周一始） */
    THREE_MONTHS("3 月"),

    /** 最近 12 个自然月（按月分桶） */
    YEAR("年"),
}

/**
 * 图表区状态：聚合完成的柱状图数据 + 头部摘要文案。
 *
 * @property chartData 喂给 AnimatedBarChart 的图表数据（纯值语义）
 * @property averageVolumeText 日均容量（含单位，如 "1.2 吨" / "850 kg"）
 * @property rangeText 区间文案（如 "7月19日 – 7月25日"）
 * @property hasData 区间内是否有任何正式组容量（false 时页面展示占位文案而非图表）
 */
data class StatsChartState(
    val chartData: ChartData = ChartData(entries = emptyList(), yMax = 1f),
    val averageVolumeText: String = "",
    val rangeText: String = "",
    val hasData: Boolean = false,
)

/**
 * Stats 统计页 UI 状态。
 *
 * @property isLoading 首个数据帧到达前为 true（渲染顶部加载条，杜绝默认值假渲染）
 * @property period 当前选中的周期档位
 * @property chart 图表区状态
 */
data class StatsUiState(
    val isLoading: Boolean = true,
    val period: StatsPeriod = StatsPeriod.WEEK,
    val chart: StatsChartState = StatsChartState(),
)
