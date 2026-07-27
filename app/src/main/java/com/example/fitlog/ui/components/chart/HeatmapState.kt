package com.example.fitlog.ui.components.chart

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import java.time.LocalDate

/**
 * [ContributionHeatmap] 的动画状态持有者：**双快照 lerp 模型**。
 *
 * ## 为什么不复用 [AnimatedChartState]
 *
 * 柱状/折线的 keyed 状态机建立在「条目 id + 槽位中心」之上——条目会移动、增删；
 * 热力图是固定的 371 格 LocalDate 网格，格子永不移动，唯一变化的是强度等级。
 * 为它维护 371 个 per-date Animatable（≈100KB + 371 并发协程）没有对应收益——
 * 主场景（今天记了一次训练）只有 1-2 格变化。
 *
 * ## 模型
 *
 * 持有 `fromLevels`/`toLevels` 两份等级快照与**唯一**的 [progress] Animatable：
 * 数据更新时全格从 from 插值到 to（未变化的格 lerp 恒等、视觉静止）；
 * 入场波形（左→右列扫描）由绘制层用列序修正 [levelOf] 的插值进度实现，
 * 不需要逐格错峰（见 [ContributionHeatmap] 的 SWEEP 数学）。
 *
 * ## 中断自愈
 *
 * [animateTo] 被新数据打断时，`from` 取**打断点上各日期的实际显示值**
 * （按当前 progress 插值解析），再 retarget——与 [AnimatedChartState.animateTo]
 * 的「从当前值继续」哲学一致，绝不跳变。
 *
 * 配置更改（旋转）后状态不保留，入场扫波重放（v1 取舍，同家族惯例）。
 */
class HeatmapState internal constructor(initialLevels: Map<LocalDate, Int>?) {

    /** 过渡起点的等级快照（连续浮点）。 */
    internal var fromLevels: Map<LocalDate, Float> by mutableStateOf(
        initialLevels?.mapValues { it.value.toFloat() } ?: emptyMap(),
    )
        private set

    /** 过渡终点的等级快照（连续浮点）。 */
    internal var toLevels: Map<LocalDate, Float> by mutableStateOf(fromLevels)
        private set

    /** 全格共享的过渡进度 0→1；预览播种时初始为 1（定形态）。 */
    internal val progress: Animatable<Float, AnimationVector1D> =
        Animatable(if (initialLevels == null) 0f else 1f)

    /**
     * 将热力图过渡到新等级（须在协程中调用，由组件内 `LaunchedEffect(levels)` 驱动）。
     *
     * @param levels 目标等级（0..[HeatmapGrid.LEVELS]-1 的整数）
     * @param spec 过渡动画规格
     */
    internal suspend fun animateTo(
        levels: Map<LocalDate, Int>,
        spec: AnimationSpec<Float>,
    ) {
        val t = progress.value
        val target = levels.mapValues { it.value.toFloat() }
        // 中断自愈：from = 打断点上的实际显示值（三份 key 的并集，缺席按 0）
        fromLevels = (fromLevels.keys + toLevels.keys + target.keys).associateWith { date ->
            lerp(fromLevels[date] ?: 0f, toLevels[date] ?: 0f, t)
        }
        toLevels = target
        progress.snapTo(0f)
        progress.animateTo(1f, spec)
    }

    /**
     * 该日期在 [columnProgress]（列扫描修正后的进度）下的当前等级（连续浮点）。
     * 只允许在 draw 作用域读取（逐帧仅触发重绘，不触发重组）。
     */
    internal fun levelOf(date: LocalDate, columnProgress: Float): Float =
        lerp(fromLevels[date] ?: 0f, toLevels[date] ?: 0f, columnProgress)

    private fun lerp(start: Float, stop: Float, fraction: Float): Float =
        start + (stop - start) * fraction
}

/**
 * 创建并记住 [HeatmapState]。
 *
 * @param initialLevels 传入则同步构造定形态（Preview/截图测试用——动画在 Preview 中
 *   不执行，空初始态只能看到全空网格）；运行时传 null，首帧播左→右入场扫波。
 *   与普通 `remember` 语义一致：入参后续变化不会重建状态。
 */
@Composable
fun rememberHeatmapState(initialLevels: Map<LocalDate, Int>? = null): HeatmapState {
    return remember { HeatmapState(initialLevels) }
}
