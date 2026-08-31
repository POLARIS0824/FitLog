package com.example.fitlog.feature.stats

import com.example.fitlog.model.Workout
import com.example.fitlog.util.VolumeAggregator
import java.time.LocalDate

/**
 * Stats 概览网格的数据构建器（纯函数对象，无 Android 依赖，JVM 可测）。
 *
 * 把区间训练日志聚合为 2×2 指标卡的输入 [StatsOverviewState]，固定四项：
 * 训练次数 / 总容量 / 平均单次容量 / 正式组数。
 *
 * - **口径**：容量与组数只累加 [SetType.WORKING] 正式组（同 StatsChartDataBuilder /
 *   WeekProgressCalculator 的全局约定，热身组不计）
 * - **窗口防御**：内部按 [StatsChartDataBuilder.rangeOf] 再过滤一次——
 *   计数类指标不过滤会被窗口外记录虚增（图表分桶天然落桶免疫，计数没有这层保护）
 * - 空数据降级为零值文案（"0 次"/"0 kg"），无需特判空态
 */
object StatsOverviewBuilder {

    /**
     * 构建概览状态。
     *
     * @param workouts 训练日志（通常已是区间查询结果；内部仍按窗口过滤，见类 KDoc）
     * @param period 周期档位（决定窗口）
     * @param today 锚定「今天」（测试注入，保证确定性）
     */
    fun build(workouts: List<Workout>, period: StatsPeriod, today: LocalDate): StatsOverviewState {
        val range = StatsChartDataBuilder.rangeOf(period, today)
        // 统一走 isCountable 口径：空动作记录（Markdown 导入的表头存档）与
        // 进行中的会话行（startedAt 已写、endedAt 为空，含 0 次占位组）都不计入，
        // 否则次数/组数被虚增且进行中会话在结束前永久抬高区间均值
        val inWindow = workouts.filter { it.date in range && it.isCountable }

        val sessionCount = inWindow.size
        // 容量/组数聚合统一走 VolumeAggregator 收口，消除与 Stats 图表、
        // Agent 工具路径的手写口径漂移面
        val totalVolume = inWindow.sumOf { VolumeAggregator.workingVolumeOf(it) }
        val workingSets = inWindow.sumOf { VolumeAggregator.workingSetCountOf(it) }
        val averageVolume = if (sessionCount > 0) totalVolume / sessionCount else 0.0

        return StatsOverviewState(
            items = listOf(
                StatsOverviewItem(title = "训练次数", valueText = "$sessionCount 次"),
                StatsOverviewItem(
                    title = "总容量",
                    valueText = StatsChartDataBuilder.formatVolume(totalVolume),
                ),
                StatsOverviewItem(
                    title = "平均单次",
                    valueText = StatsChartDataBuilder.formatVolume(averageVolume),
                ),
                StatsOverviewItem(title = "正式组数", valueText = "$workingSets 组"),
            ),
        )
    }
}
