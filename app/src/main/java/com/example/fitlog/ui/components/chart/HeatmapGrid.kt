package com.example.fitlog.ui.components.chart

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import kotlin.math.ceil

/**
 * 热力图网格的纯函数构建器（无 Android 依赖，JVM 可测）。
 *
 * 把「年份窗口 → 周列网格 → 月标签 → 命中测试」的几何与 [ContributionHeatmap] 解耦：
 * 组件只负责绘制与滚动，全部布局推导可独立单测。
 */
object HeatmapGrid {

    /** 色阶档数（0 = 空 + 4 档强度），GitHub 风格。 */
    const val LEVELS = 5

    /** 每周天数（网格行数）。 */
    const val DAYS_PER_WEEK = 7

    /**
     * 月标签：位于 [columnIndex] 列正下方的 [month] 标签。
     */
    data class MonthLabel(val columnIndex: Int, val month: Month)

    /**
     * 构建周列网格：末列 = [endDate] 所在周（周一始，同项目周界约定），向前共 [weekCount] 列；
     * 每列 7 个日期（周一..周日）。末列中 [endDate] 之后的日期为未来格（由绘制层透明处理）。
     *
     * @param endDate 窗口锚点（含）
     * @param weekCount 列数（≥ 1；53 ≈ 一年）
     */
    fun buildWeekColumns(endDate: LocalDate, weekCount: Int): List<List<LocalDate>> {
        require(weekCount >= 1) { "weekCount must be >= 1, was $weekCount" }
        val lastMonday = endDate.with(DayOfWeek.MONDAY)
        return (0 until weekCount).map { col ->
            val monday = lastMonday.minusWeeks((weekCount - 1 - col).toLong())
            (0 until DAYS_PER_WEEK).map { monday.plusDays(it.toLong()) }
        }
    }

    /**
     * 构建月标签：列 0 恒打标（窗口从月中开始时仍有锚点）；之后当列首日期月份与
     * 前一列不同则打标；与上一个已发射标签相距 < [minGapColumns] 则跳过——
     * 这是小屏/小列数下防重叠的守卫（weekCount ≥ 13 时月份间隔 ≥ 4 列，不会触发）。
     *
     * @param columns [buildWeekColumns] 的输出
     * @param minGapColumns 相邻标签的最小列间距
     */
    fun buildMonthLabels(
        columns: List<List<LocalDate>>,
        minGapColumns: Int = 2,
    ): List<MonthLabel> {
        val labels = mutableListOf<MonthLabel>()
        columns.forEachIndexed { index, column ->
            val month = column.first().month
            val isBoundary = index == 0 || month != columns[index - 1].first().month
            val lastEmitted = labels.lastOrNull()
            if (isBoundary && (lastEmitted == null || index - lastEmitted.columnIndex >= minGapColumns)) {
                labels.add(MonthLabel(columnIndex = index, month = month))
            }
        }
        return labels
    }

    /**
     * 命中测试：Canvas 内容坐标 (x, y) → 日期。
     * 落在间距带（pitch 中 cell 之外的部分）或网格外（含月份标签行）返回 null。
     *
     * @param columns [buildWeekColumns] 的输出
     * @param x 点击横坐标（内容系，滚动容器内 Canvas 的 pointerInput 偏移天然是内容系）
     * @param y 点击纵坐标（内容系）
     * @param cellPx 格子边长（px）
     * @param pitchPx 格子步距（cell + spacing，px）
     */
    fun cellAt(
        columns: List<List<LocalDate>>,
        x: Float,
        y: Float,
        cellPx: Float,
        pitchPx: Float,
    ): LocalDate? {
        if (x < 0f || y < 0f) return null
        val col = (x / pitchPx).toInt()
        val row = (y / pitchPx).toInt()
        if (col !in columns.indices || row !in 0 until DAYS_PER_WEEK) return null
        if (x - col * pitchPx > cellPx || y - row * pitchPx > cellPx) return null
        return columns[col][row]
    }
}

/**
 * 热力图强度分档（v1 默认算法：按最大值线性分档）。
 *
 * 算法是可插拔的接缝（「后续探讨」）：调用方经 [ContributionHeatmap] 的
 * `levelOf` 参数替换即可——候选方向：分位数分档（抗单次超高容量的离群值）、
 * 对数分档（量级差异大时）、按周滚动均值归一（体能进步后的自适应）。
 */
object HeatmapLevels {

    /**
     * 线性分档：[value] ≤ 0 或 [max] ≤ 0 → 0；否则按 value/max 比例映射到
     * 1..[HeatmapGrid.LEVELS]-1（超 max 钳到顶档）。
     *
     * @param value 当日训练量
     * @param max 窗口内最大日训练量
     */
    fun linearByMax(value: Float, max: Float): Int {
        if (value <= 0f || max <= 0f) return 0
        return ceil(value / max * (HeatmapGrid.LEVELS - 1)).toInt()
            .coerceIn(1, HeatmapGrid.LEVELS - 1)
    }

    /**
     * 全图分档：max 计算一次，逐日映射；空图 → 空图。
     *
     * @param values 日期 → 当日训练量（kg）
     * @param levelOf 单日分档算法（见 [linearByMax]）
     */
    fun levelsOf(
        values: Map<LocalDate, Float>,
        levelOf: (value: Float, max: Float) -> Int,
    ): Map<LocalDate, Int> {
        val max = values.values.maxOrNull() ?: 0f
        return values.mapValues { (_, value) -> levelOf(value, max) }
    }
}
