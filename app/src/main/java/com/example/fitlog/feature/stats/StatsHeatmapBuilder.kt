package com.example.fitlog.feature.stats

import com.example.fitlog.model.Workout
import com.example.fitlog.util.VolumeAggregator
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Stats 坚持度热力图的数据构建器（纯函数对象，无 Android 依赖，JVM 可测）。
 *
 * 把全年训练日志聚合为 ContributionHeatmap 的输入 [StatsHeatmapState]：
 *
 * - **窗口**：固定 53 周（[windowStart] .. today，周一对齐），独立于周期档位——
 *   热力图的时间语义是「年度一致性」，跟随档位会在周/月档退化成 7/30 格
 * - **口径**：与容量图表一致，只累加 [SetType.WORKING] 正式组；同日多次训练容量合并；
 *   0 容量日不进 map（组件按缺席 = 0 档处理）
 * - **裁剪**：窗口外日期一律剔除——HeatmapLevels.levelsOf 的归一 max 取整份 map
 *   最大值，窗口外离群高容量日会压平所有可见格的颜色等级
 * - **纯度**：每次调用新建 Map；内容相等即不重启组件动画（ContributionHeatmap 的
 *   动画 key 是 values 内容相等），调用方逐帧重建安全
 */
object StatsHeatmapBuilder {

    /** 热力图列数（53 ≈ 一年，同 ContributionHeatmap 默认值）。 */
    const val WEEK_COUNT = 53

    /**
     * 窗口起始日（含）：[today] 所在周周一向前 52 个周一。
     * 是 ViewModel 查询下界与 [build] 裁剪的唯一事实源。
     *
     * @param today 窗口锚点（含）
     */
    fun windowStart(today: LocalDate): LocalDate =
        today.with(DayOfWeek.MONDAY).minusWeeks((WEEK_COUNT - 1).toLong())

    /**
     * 构建热力图状态。
     *
     * @param workouts 训练日志（通常为 [windowStart] 起的查询结果；窗口外日期在此剔除）
     * @param today 锚定「今天」（测试注入，保证确定性）
     */
    fun build(workouts: List<Workout>, today: LocalDate): StatsHeatmapState {
        val start = windowStart(today)

        // 窗口外日期一律剔除——HeatmapLevels.levelsOf 的归一 max 取整份 map 最大值，
        // 窗口外离群高容量日会压平所有可见格的颜色等级
        val values = VolumeAggregator.volumeByDate(workouts)
            .filterKeys { it >= start && it <= today }
            .mapValues { (_, v) -> v.toFloat() }
        return StatsHeatmapState(
            values = values,
            trainedDays = values.size,
            longestStreak = longestStreak(values.keys),
            endDate = today,
        )
    }

    /**
     * 最长连续训练天数：日期集合中最长自然日链的长度（跨周不断，空集为 0）。
     *
     * @param dates 有训练的日期（无需有序）
     */
    internal fun longestStreak(dates: Collection<LocalDate>): Int {
        if (dates.isEmpty()) return 0
        val sorted = dates.sorted()
        var longest = 1
        var current = 1
        for (i in 1 until sorted.size) {
            current = if (sorted[i] == sorted[i - 1].plusDays(1)) current + 1 else 1
            if (current > longest) longest = current
        }
        return longest
    }
}
