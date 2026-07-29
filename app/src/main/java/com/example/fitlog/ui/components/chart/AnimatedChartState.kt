package com.example.fitlog.ui.components.chart

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 单个图表条目（柱/点）的动画态：三个并行 [Animatable] + 快照元数据。
 *
 * 所有动画值只允许在 draw 作用域读取（逐帧仅触发重绘，不触发重组）。
 */
internal class ChartAnimState(
    value: Float,
    centerFraction: Float,
    alpha: Float,
    targetIndex: Int,
    label: String,
) {
    /** 条目值（原始单位），绘制时按图表 effective 区间换算为纵坐标。 */
    val value: Animatable<Float, AnimationVector1D> = Animatable(value)

    /** 槽位中心（0..1 归一化，相对绘图区宽度）；位置动画让条目在布局变化时「滑行」。 */
    val centerFraction: Animatable<Float, AnimationVector1D> = Animatable(centerFraction)

    /** 整体透明度（入场淡入 / 退场淡出 / 复活回升）。 */
    val alpha: Animatable<Float, AnimationVector1D> = Animatable(alpha)

    /** 在当前数据集中的槽位索引（x 标签抽稀取模用）；退场条目保持旧值不再更新。 */
    var targetIndex by mutableIntStateOf(targetIndex)

    /** x 轴标签文案；MATCHED 分支刷新为最新值。 */
    var label by mutableStateOf(label)

    /** 退场标记：true 时不绘制 x 标签/不参与折线路径，且后续 [AnimatedChartState.animateTo] 的 EXIT 分支持续收割。 */
    var isExiting by mutableStateOf(false)
}

/**
 * 动画图表的状态持有者（对齐 `LazyListState` 等 Compose 状态惯例）。
 *
 * 渲染器无关：同一份状态驱动 [AnimatedBarChart]（柱状）、[MiniBarChart]（迷你柱状）
 * 与 [MiniLineChart]（折线）——条目都是 `value / centerFraction / alpha` 三元组，
 * 差异只在绘制层。
 *
 * ## 状态机
 *
 * 内部维护 `id → [ChartAnimState]` 的 keyed map；[animateTo] 将新数据与 map 当前内容
 * （而非上一份数据）做三方 diff：
 *
 * - **MATCHED**（id 两侧都在，含退场中复活）：value/centerFraction/alpha 从当前值
 *   retarget 到新值——条目滑行变形；[Animatable.animateTo] 互斥取消同实例上的旧动画，
 *   中途变向也不跳变
 * - **ENTER**（仅新数据）：在目标槽位以 0 值 0 透明度插入，按索引错峰生长
 * - **EXIT**（仅 map）：centerFraction 冻结在原槽位（过渡期新旧两套槽位布局共存），
 *   收缩淡出，动画正常结束后从 map 移除
 *
 * ## 清理与中断规则
 *
 * 1. map 条目仅在退场动画**正常完成**后移除；
 * 2. 协程被取消（新数据到达）留下的「半退场」孤儿条目，由下一轮 [animateTo] 的
 *    EXIT 分支从冻结值继续收割——自愈，不泄漏；
 * 3. 同一时刻只有一个 orchestrator，由调用方的 `LaunchedEffect(data)` 保证；
 * 4. map 是 remember 状态，随组合销毁，无需额外清理。
 *
 * 配置更改（旋转）后 map 不保留，入场动画会重放（v1 取舍，不配 Saver）。
 */
class AnimatedChartState internal constructor(initialData: ChartData?) {

    internal val items: SnapshotStateMap<String, ChartAnimState> = mutableStateMapOf()

    /** y 轴上限的动画值；柱状渲染器绘制时与当前条目值取 max 防过渡中途裁剪。 */
    internal val animatedYMax: Animatable<Float, AnimationVector1D> =
        Animatable(initialData?.yMax ?: 1f)

    /** y 轴下限的动画值（仅折线渲染器使用；柱状渲染器始终 0 基线）。 */
    internal val animatedYMin: Animatable<Float, AnimationVector1D> =
        Animatable(initialData?.yMin ?: 0f)

    /**
     * 槽位数量的动画值（条目宽 = 绘图区宽 / 槽数 × 宽度比）。
     * 条目数变化（7↔30↔13↔12）时让全体宽度平滑收放，避免与位置动画不同步的宽度突变。
     */
    internal val animatedSlotCount: Animatable<Float, AnimationVector1D> =
        Animatable(initialData?.entries?.size?.coerceAtLeast(1)?.toFloat() ?: 1f)

    init {
        // 预览/截图测试用：同步构造已定形状态（LaunchedEffect 在 Preview 中不执行）
        initialData?.let { data ->
            val n = data.entries.size
            data.entries.forEachIndexed { index, entry ->
                items[entry.id] = ChartAnimState(
                    value = entry.value,
                    centerFraction = slotCenter(index, n),
                    alpha = 1f,
                    targetIndex = index,
                    label = entry.label,
                )
            }
        }
    }

    /**
     * 将图表过渡到新数据（须在协程中调用，由组件内 `LaunchedEffect(data)` 驱动）。
     *
     * @param data 目标数据（纯值语义契约见 [ChartData]）
     * @param spec 条目值/槽位/槽数/y 轴范围/入场淡入的动画规格
     * @param exitSpec 退场收缩淡出的动画规格
     * @param maxStaggerMillis 入场错峰总时长（每条目步进 = 总时长 / 条目数，多寡波形同宽）
     */
    internal suspend fun animateTo(
        data: ChartData,
        spec: AnimationSpec<Float>,
        exitSpec: AnimationSpec<Float>,
        maxStaggerMillis: Int,
    ) = coroutineScope {
        val n = data.entries.size
        val targets = data.entries.mapIndexed { index, entry ->
            entry.id to Target(slotCenter(index, n), entry.value, entry.label, index)
        }
        val targetIds = targets.mapTo(HashSet()) { it.first }

        launch { animatedYMax.animateTo(data.yMax, spec) }
        launch { animatedYMin.animateTo(data.yMin, spec) }
        launch { animatedSlotCount.animateTo(n.coerceAtLeast(1).toFloat(), spec) }

        // 快照当前 map：diff 基于本轮开始时的内容，子协程的增删不影响迭代
        val existing = items.toMap()

        // EXIT：id 不在新数据中 → 冻结槽位，原地收缩淡出，完成后移除
        for ((id, item) in existing) {
            if (id in targetIds) continue
            item.isExiting = true
            launch { item.value.animateTo(0f, exitSpec) }
            launch {
                item.alpha.animateTo(0f, exitSpec)
                items.remove(id)
            }
        }

        for ((id, target) in targets) {
            val item = existing[id]
            if (item != null) {
                // MATCHED（含退场中复活）：从当前动画值继续，绝不跳变
                item.isExiting = false
                item.targetIndex = target.index
                item.label = target.label
                launch { item.centerFraction.animateTo(target.slot, spec) }
                launch { item.value.animateTo(target.value, spec) }
                launch { item.alpha.animateTo(1f, spec) }
            } else {
                // ENTER：目标槽位从零生长，错峰延迟
                val entering = ChartAnimState(
                    value = 0f,
                    centerFraction = target.slot,
                    alpha = 0f,
                    targetIndex = target.index,
                    label = target.label,
                )
                items[id] = entering
                val staggerMs = if (n <= 1) 0L else maxStaggerMillis.toLong() * target.index / n
                launch {
                    delay(staggerMs)
                    launch { entering.value.animateTo(target.value, spec) }
                    entering.alpha.animateTo(1f, spec)
                }
            }
        }
    }

    /** 新数据中一个条目的动画目标。 */
    private data class Target(
        val slot: Float,
        val value: Float,
        val label: String,
        val index: Int,
    )

    internal companion object {
        /** 第 [index] 个条目（共 [count] 个）的槽位中心（0..1 归一化）。 */
        internal fun slotCenter(index: Int, count: Int): Float =
            (index + 0.5f) / count.coerceAtLeast(1)
    }
}

/**
 * 创建并记住 [AnimatedChartState]。
 *
 * @param initialData 传入则同步构造定形态（Preview/截图测试用——动画在 Preview 中
 *   不执行，空初始态只能看到空图）；运行时传 null，首帧走 ENTER 分支生长入场。
 *   与普通 `remember` 语义一致：入参后续变化不会重建状态。
 */
@Composable
fun rememberAnimatedChartState(initialData: ChartData? = null): AnimatedChartState {
    return remember { AnimatedChartState(initialData) }
}
