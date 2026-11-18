package com.example.fitlog.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Insight dashboard, weekly report and exercise history. */
@Immutable
class InsightDimens {
    /** Shared chart drawing: hairline axes and grid, data lines, point marks, bar corners. */
    val chartGridStroke: Dp = 1.dp
    val chartLineStroke: Dp = 3.dp
    val chartPointRadius: Dp = 4.dp
    val chartBarCorner: Dp = 3.dp

    /** Exercise load curve (exercise history). */
    val curveChartHeight: Dp = 160.dp
    val curveChartInset: Dp = 12.dp
    val curveSelectedRingRadius: Dp = 9.dp
    val curveSelectedRingStroke: Dp = 2.dp

    /** Consistency card: weekly bar and line chart. */
    val weeklyChartHeight: Dp = 180.dp
    val weeklyChartInset: Dp = 8.dp
    val weeklyChartPointRadius: Dp = 2.5.dp

    /** Consistency card: contribution-style calendar; cell and label sizes scale with font size. */
    val calendarCell: Dp = 18.dp
    val calendarMonthLabelHeight: Dp = 24.dp
    val calendarCellGap: Dp = 3.dp
    val calendarCellCorner: Dp = 3.dp
    val calendarLegendSwatch: Dp = 12.dp
    val calendarLegendSwatchInset: Dp = 1.dp
    val calendarLegendSwatchCorner: Dp = 2.dp

    /** Ranking card: rank number badge. */
    val rankBadgeSize: Dp = 32.dp

    /** Training balance radar chart. */
    val balanceChartHeight: Dp = 220.dp
    val balanceOutlineStroke: Dp = 2.dp

    /** Activity matrix: weekday label column, week columns and session dots. */
    val matrixMinWidth: Dp = 260.dp
    val matrixDayLabelWidth: Dp = 44.dp
    val matrixWeekWidth: Dp = 56.dp
    val matrixCellHeight: Dp = 40.dp
    val matrixEmptyDotRadius: Dp = 2.dp
    val matrixMinDotRadius: Dp = 5.dp
    val matrixDotRadiusRange: Dp = 10.dp

    /** Weekly report: per-day bars; the column width grows with font size. */
    val weekdayColumnMinWidth: Dp = 40.dp
    val weekdayBarHeight: Dp = 144.dp

    /** Recorded-set quantity bar in weekly trends and exercise distribution. */
    val quantityBarHeight: Dp = 8.dp

    /** Year heatmap: one cell per day, dot radius grows by one step per session. */
    val heatmapCell: Dp = 16.dp
    val heatmapCellGap: Dp = 3.dp
    val heatmapCellCorner: Dp = 2.dp
    val heatmapLegendSwatch: Dp = 13.dp
    val heatmapDotBaseRadius: Dp = 2.dp
    val heatmapDotRadiusStep: Dp = 1.dp

    /** Bottom sheets: list sheets and form sheets (focus, aliases) stop growing at these heights. */
    val listSheetMaxHeight: Dp = 560.dp
    val formSheetMaxHeight: Dp = 640.dp
}
