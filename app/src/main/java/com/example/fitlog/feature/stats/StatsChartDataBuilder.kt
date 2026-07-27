package com.example.fitlog.feature.stats

import com.example.fitlog.model.SetType
import com.example.fitlog.model.Workout
import com.example.fitlog.ui.components.chart.ChartData
import com.example.fitlog.ui.components.chart.ChartEntry
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/**
 * Stats 图表数据构建器（纯函数对象，无 Android 依赖，JVM 可测）。
 *
 * 把区间训练日志聚合为 AnimatedBarChart 的输入 [ChartData]：
 *
 * - **分桶**：WEEK = 7 日桶 / MONTH = 30 日桶 / THREE_MONTHS = 13 周桶（周一始，
 *   同 TodayViewModel 的周界约定）/ YEAR = 12 个自然月桶；窗口均截至 [today]，
 *   最末桶为不完整桶
 * - **口径**：只累加 [SetType.WORKING] 正式组的 重量kg × 次数
 *   （同 WeekProgressCalculator 的容量口径，热身组不计）
 * - **桶 id 命名空间按档分级**：日桶 = ISO 日期、周桶 = 周一 ISO 日期、月桶 = YearMonth。
 *   W↔M 同为日 id，重叠日期的柱子在 AnimatedBarChart 中滑移变形；
 *   跨命名空间（如 M→Y）零重叠，原地消散 + 错峰入场；
 *   同档位数据更新 id 不变，柱子原地变形
 */
object StatsChartDataBuilder {

    /** 一根柱子的坐标：id/标签 + 覆盖的日期范围（含端点）。 */
    private data class Bucket(
        val id: String,
        val label: String,
        val from: LocalDate,
        val to: LocalDate,
    )

    /**
     * 构建图表状态。
     *
     * @param workouts 训练日志（通常为 [rangeOf] 区间的查询结果；区间外日期自然落不到任何桶，无需预过滤）
     * @param period 周期档位
     * @param today 锚定「今天」（测试注入，保证确定性）
     */
    fun build(workouts: List<Workout>, period: StatsPeriod, today: LocalDate): StatsChartState {
        val buckets = bucketsOf(period, today)

        // 一次性聚合：日 → 当日容量；各桶再按覆盖日期累加（避免 workouts × buckets 嵌套扫描）
        val volumeByDate = mutableMapOf<LocalDate, Double>()
        workouts.forEach { workout ->
            val volume = workout.exercises.sumOf { log ->
                log.sets
                    .filter { it.setType == SetType.WORKING }
                    .sumOf { (it.weightKg * it.reps).toDouble() }
            }
            if (volume > 0.0) {
                volumeByDate.merge(workout.date, volume, Double::plus)
            }
        }

        val values = buckets.map { bucket ->
            var sum = 0.0
            var date = bucket.from
            while (!date.isAfter(bucket.to)) {
                sum += volumeByDate[date] ?: 0.0
                date = date.plusDays(1)
            }
            sum
        }

        val total = values.sum()
        val maxBucket = values.maxOrNull() ?: 0.0
        val range = rangeOf(period, today)
        val days = ChronoUnit.DAYS.between(range.start, range.endInclusive) + 1

        return StatsChartState(
            chartData = ChartData(
                entries = buckets.mapIndexed { index, bucket ->
                    ChartEntry(
                        id = bucket.id,
                        value = values[index].toFloat(),
                        label = bucket.label,
                    )
                },
                // 留白 15% 后按 1-2-5-10 取整，柱顶不贴边且刻度为整数
                yMax = niceCeil(maxBucket * 1.15),
            ),
            averageVolumeText = formatVolume(total / days),
            rangeText = formatRange(range.start, range.endInclusive),
            hasData = total > 0.0,
        )
    }

    /**
     * [period] 截至 [today] 的查询区间（含端点），供 Repository 取数。
     *
     * 与 [bucketsOf] 的覆盖范围一致（THREE_MONTHS 对齐到本周一之前的 12 个周一，
     * YEAR 对齐到 11 个月前的月初）。
     */
    fun rangeOf(period: StatsPeriod, today: LocalDate): ClosedRange<LocalDate> = when (period) {
        StatsPeriod.WEEK -> today.minusDays(6)..today
        StatsPeriod.MONTH -> today.minusDays(29)..today
        StatsPeriod.THREE_MONTHS -> today.with(DayOfWeek.MONDAY).minusWeeks(12)..today
        StatsPeriod.YEAR -> YearMonth.from(today).minusMonths(11).atDay(1)..today
    }

    /**
     * y 轴刻度值的紧凑格式化（刻度空间窄，不带单位后缀，单位在头部摘要给出）：
     * ≥10t 显示整吨（"12t"），≥1t 保留一位小数（"1.5t"），否则整数千克（"850"）。
     *
     * @param volumeKg 刻度值（kg）
     */
    fun formatAxisValue(volumeKg: Float): String = when {
        volumeKg >= 10_000f -> "${(volumeKg / 1000).toInt()}t"
        volumeKg >= 1_000f -> {
            val tonnes = volumeKg / 1000
            if (tonnes % 1f == 0f) "${tonnes.toInt()}t" else "%.1ft".format(tonnes)
        }
        else -> "${volumeKg.toInt()}"
    }

    /** 各档位的分桶定义（窗口均截至 [today]，最末桶可能不完整）。 */
    private fun bucketsOf(period: StatsPeriod, today: LocalDate): List<Bucket> = when (period) {
        StatsPeriod.WEEK -> (0L..6L).map { offset ->
            val date = today.minusDays(6L - offset)
            Bucket(
                id = date.toString(),
                label = date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                from = date,
                to = date,
            )
        }
        StatsPeriod.MONTH -> (0L..29L).map { offset ->
            val date = today.minusDays(29L - offset)
            Bucket(
                id = date.toString(),
                label = date.dayOfMonth.toString(),
                from = date,
                to = date,
            )
        }
        StatsPeriod.THREE_MONTHS -> {
            val thisMonday = today.with(DayOfWeek.MONDAY)
            (0L..12L).map { offset ->
                val monday = thisMonday.minusWeeks(12L - offset)
                Bucket(
                    id = monday.toString(),
                    label = "${monday.monthValue}/${monday.dayOfMonth}",
                    from = monday,
                    to = minOf(monday.plusDays(6), today),
                )
            }
        }
        StatsPeriod.YEAR -> {
            val thisMonth = YearMonth.from(today)
            (0L..11L).map { offset ->
                val month = thisMonth.minusMonths(11L - offset)
                Bucket(
                    id = month.toString(),
                    label = month.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                    from = month.atDay(1),
                    to = minOf(month.atEndOfMonth(), today),
                )
            }
        }
    }

    /**
     * yMax 取整：[raw] 向上取到 1-2-5×10^n 序列（1150→2000、430→500、999→1000），下限 1。
     * 留白系数由调用方乘入。
     */
    internal fun niceCeil(raw: Double): Float {
        if (raw <= 0.0) return 1f
        val magnitude = 10.0.pow(floor(log10(raw)))
        val norm = raw / magnitude
        val nice = when {
            norm <= 1.0 -> 1.0
            norm <= 2.0 -> 2.0
            norm <= 5.0 -> 5.0
            else -> 10.0
        }
        return (nice * magnitude).toFloat()
    }

    /** 头部摘要的容量格式化：≥1000kg 显示吨（一位小数），否则整数千克（沿用 WeekProgressCalculator 口径）。 */
    private fun formatVolume(volumeKg: Double): String =
        if (volumeKg >= 1000) {
            "%.1f 吨".format(volumeKg / 1000)
        } else {
            "${volumeKg.toInt()} kg"
        }

    /** 区间文案：同年省略起点年份（"7月19日 – 7月25日"），跨年两端都带年份。 */
    private fun formatRange(from: LocalDate, to: LocalDate): String {
        fun LocalDate.text(withYear: Boolean): String =
            if (withYear) "${year}年${monthValue}月${dayOfMonth}日" else "${monthValue}月${dayOfMonth}日"
        val sameYear = from.year == to.year
        return "${from.text(withYear = !sameYear)} – ${to.text(withYear = !sameYear)}"
    }
}
