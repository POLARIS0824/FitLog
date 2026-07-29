package com.example.fitlog.ui.components.chart

/**
 * 图表的一个数据条目（柱状图的一根柱 / 折线图的一个点）。
 *
 * [id] 是跨数据集变更的匹配锚点：同一 id 在新旧两份 [ChartData] 之间被视为
 * 「同一条目」，其高度/水平位置以插值动画过渡；仅存在于一侧的 id 走入场/退场动画。
 * 因此 id 必须在数据更新间保持稳定且有业务含义（如日桶用 ISO 日期 "2026-07-25"），
 * 不要用列表下标——下标在条目数变化时会产生错误的配对。
 */
data class ChartEntry(
    /** 跨数据集稳定的唯一标识（动画配对锚点） */
    val id: String,
    /** 条目值（原始单位，y 轴范围见 [ChartData.yMax]/[ChartData.yMin]） */
    val value: Float,
    /** x 轴标签（全尺寸图表在条目过多时按槽宽自动抽稀显示；迷你图表不绘制标签） */
    val label: String,
)

/**
 * 动画图表的一份完整输入数据；更换实例即触发组件内部的插值动画。
 *
 * 渲染器无关的通用模型：同一份数据可喂给 [AnimatedBarChart]（全尺寸柱状）、
 * [MiniBarChart]（迷你柱状）或 [MiniLineChart]（迷你折线）。
 *
 * ## 纯值语义契约
 *
 * 本类不得持有 lambda / 函数引用——实例是组件内 `LaunchedEffect(data)` 的 key，
 * 引用不等的 lambda 会让 effect 在每次重组时误判「数据变了」并重启动画状态机。
 * 值格式化等函数请走 composable 参数（如 `valueFormatter`）。
 *
 * @property entries 条目列表，从左到右排列；id 必须唯一
 * @property yMax y 轴上限，由调用方留白取整，需 ≥ 最大条目值及 [goalLine]
 * @property yMin y 轴下限（默认 0）。**仅折线渲染器使用**（体重等数据围绕非零区间波动，
 *   自动裁剪到数据区间）；柱状渲染器始终从 0 基线生长，忽略本字段
 * @property goalLine 可选目标参考线（y 值）。细线样式由渲染器绘制
 *   （全尺寸 = 虚线 + 数值标签；迷你 = 实线无标签），null 不绘制。
 *   与卡片级进度轨道 [com.example.fitlog.ui.components.GoalTrack] 是两种不同的目标语义
 */
data class ChartData(
    val entries: List<ChartEntry>,
    val yMax: Float,
    val yMin: Float = 0f,
    val goalLine: Float? = null,
)
