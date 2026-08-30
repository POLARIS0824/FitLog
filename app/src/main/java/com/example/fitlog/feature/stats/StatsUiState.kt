package com.example.fitlog.feature.stats

import com.example.fitlog.ui.components.chart.ChartData
import java.time.LocalDate

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
 * 概览网格的一项指标（标题 + 大数值，如 "训练次数" / "12 次"）。
 *
 * 纯值语义：Screen 层映射为 MetricChartCardState 时注入 accentColor
 * （ViewModel 不读 MaterialTheme，见 MetricChartCardState KDoc）。
 */
data class StatsOverviewItem(
    val title: String,
    val valueText: String,
)

/**
 * 概览网格区状态：固定 4 项（次数/总容量/平均单次/正式组数），随周期档位联动。
 *
 * @property items 指标列表（顺序固定，见 [StatsOverviewBuilder]）
 */
data class StatsOverviewState(
    val items: List<StatsOverviewItem> = emptyList(),
)

/**
 * 坚持度热力图区状态：53 周窗口的日容量 + 头部摘要。
 *
 * @property values 日期 → 当日正式组容量（kg）；已裁剪到 53 周窗口、仅保留 >0 的日期
 *   （窗口外离群值会压平 HeatmapLevels 的全图归一，见 StatsHeatmapBuilder）
 * @property trainedDays 窗口内有训练的天数
 * @property longestStreak 窗口内最长连续训练天数（自然日链，跨周不断）
 * @property endDate 数据窗口锚点（即 build 时的 today）；UI 网格必须以此为准，
 *   用实时 LocalDate.now() 会在跨零点后与数据窗口发散（旧一周从图上静默消失）
 */
data class StatsHeatmapState(
    val values: Map<LocalDate, Float> = emptyMap(),
    val trainedDays: Int = 0,
    val longestStreak: Int = 0,
    val endDate: LocalDate = LocalDate.now(),
)

/**
 * 体重卡区状态：最新值 + 环比文案 + 折线数据。
 *
 * @property hasData 是否有任何体重记录（决定 Solid/Dotted 折线与 statusText 语义）
 * @property valueText 大字数值（"74.0 kg" / 空态 "暂无记录"）
 * @property deltaText 与上一条记录的差值文案（"较上次 +0.3 kg"）；不足 2 条记录为 null
 * @property chartData 折线数据（最近 30 条记录；空态为 7 个全零点 + yMin==yMax==0 的
 *   Dotted 平线占位，见 MetricChartCard 预览惯例）
 * @property xLabels 与 chartData.entries 等长的标签行（抽稀位为 ""，末位必标）
 */
data class StatsWeightState(
    val hasData: Boolean = false,
    val valueText: String = "",
    val deltaText: String? = null,
    val chartData: ChartData = ChartData(entries = emptyList(), yMax = 0f, yMin = 0f),
    val xLabels: List<String> = emptyList(),
)

/**
 * 体重录入弹层的表单状态（ViewModel 持有，同 ProfileViewModel 的字符串输入先例）。
 *
 * @property input 原始输入字符串（保存时才解析校验）
 * @property error 校验/保存失败的错误文案；null 无错误
 * @property savedTick 保存成功的单调递增信号：Screen 以 LaunchedEffect 收看关弹层。
 *   永不重置——弹层开关是 UI transient，重置会让「再次打开」被视为新保存
 */
data class WeightSheetState(
    val input: String = "",
    val error: String? = null,
    val savedTick: Int = 0,
)

/**
 * Stats 统计页 UI 状态。
 *
 * @property isLoading 首个数据帧到达前为 true（渲染顶部加载条，杜绝默认值假渲染）
 * @property period 当前选中的周期档位（仅驱动 chart 与 overview 两区）
 * @property chart 图表区状态
 * @property overview 概览网格区状态
 * @property heatmap 坚持度热力图区状态（固定 53 周窗口，不随档位变化）
 * @property weight 体重卡区状态（固定 90 天窗口，不随档位变化）
 * @property errorMessage 数据层异常提示（非 null 时弹对话框，关闭即清除；
 *   与 TodayViewModel.dataError 同模式——独立错误通道，不终结数据流）
 */
data class StatsUiState(
    val isLoading: Boolean = true,
    val period: StatsPeriod = StatsPeriod.WEEK,
    val chart: StatsChartState = StatsChartState(),
    val overview: StatsOverviewState = StatsOverviewState(),
    val heatmap: StatsHeatmapState = StatsHeatmapState(),
    val weight: StatsWeightState = StatsWeightState(),
    val errorMessage: String? = null,
)
