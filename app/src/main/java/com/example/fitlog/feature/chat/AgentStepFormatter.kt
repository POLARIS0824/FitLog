package com.example.fitlog.feature.chat

import java.util.Locale

/**
 * Agent 过程步骤的展示格式化：工具函数名 → 中文名，参数 Map → 摘要文本。
 *
 * 工具清单的事实源是 [com.example.fitlog.feature.agent.tools.FitnessTools] 的
 * `@Tool` 方法；此处按函数名映射展示文案，未知工具降级为原样展示函数名
 * （新增工具而未更新映射时优雅降级，不崩溃）。
 */
object AgentStepFormatter {

    /** 参数摘要的最大长度，超长截断（防模型传超长 query 撑爆时间线）。 */
    private const val MAX_DETAIL_LENGTH = 40

    /** 需要确认的写工具（与 FitnessTools 中 requireConfirmation = true 的一致）。 */
    private val WRITE_TOOLS = setOf("logBodyWeight", "setActivePlan")

    /**
     * 工具函数名 → 中文名；未登记的工具返回函数名本身。
     */
    fun toolLabel(toolKey: String): String = when (toolKey) {
        "getUserProfile" -> "读取个人资料"
        "getRecentWorkouts" -> "查询最近训练"
        "getWorkoutDetail" -> "查看训练明细"
        "getImportedWorkoutContent" -> "读取训练笔记"
        "getActivePlan" -> "查看当前计划"
        "getAllPlans" -> "查看计划列表"
        "getBodyMetrics" -> "查询体重趋势"
        "searchExercises" -> "搜索动作"
        "getExerciseStats" -> "查询动作水平"
        "getWeeklySummary" -> "本周训练对比"
        "logBodyWeight" -> "记录体重"
        "setActivePlan" -> "切换训练计划"
        else -> toolKey
    }

    /**
     * 是否为需要确认的写工具（时间线上用不同图标提示"改数据"性质）。
     */
    fun isWriteTool(toolKey: String): Boolean = toolKey in WRITE_TOOLS

    /**
     * 生成工具参数摘要（时间线副文本）。
     *
     * 已知字段转成业务语言（如 count=5 → "最近 5 次"），未知字段按 `key=value`
     * 原样拼接；无参数返回 null（纯读取类工具多数无参）。
     *
     * @param toolKey 工具函数名
     * @param args 工具参数（ADK 传出的 Map<String, Any?>）
     */
    fun argsSummary(toolKey: String, args: Map<String, Any?>): String? {
        if (args.isEmpty()) return null
        val summary = when (toolKey) {
            "getRecentWorkouts" -> args["count"]?.let { "最近 ${formatNumber(it)} 次" }
            "getBodyMetrics" -> args["days"]?.let { "近 ${formatNumber(it)} 天" }
            "getWorkoutDetail", "getImportedWorkoutContent" ->
                args["workoutId"]?.let { "记录 #${formatNumber(it)}" }
            "getExerciseStats" -> args["exerciseKey"]?.toString()
            "searchExercises" -> {
                val query = args["query"]?.toString()
                val bodyPart = args["bodyPart"]?.toString()
                listOfNotNull(query, bodyPart).joinToString(" · ").ifEmpty { null }
            }
            "logBodyWeight" -> args["weightKg"]?.let { "${formatNumber(it)} kg" }
            "setActivePlan" -> args["planId"]?.toString()
            else -> null
        } ?: args.entries.joinToString("、") { (k, v) -> "$k=${formatValue(v)}" }
        return truncate(summary)
    }

    /** 数字类参数去掉无意义的小数尾巴（5.0 → 5），其余原样。 */
    private fun formatNumber(value: Any?): String = when (value) {
        is Double -> if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
        is Float -> if (value == value.toInt().toFloat()) value.toInt().toString() else value.toString()
        else -> value.toString()
    }

    private fun formatValue(value: Any?): String = when (value) {
        null -> "null"
        is Number, is Boolean -> formatNumber(value)
        else -> value.toString()
    }

    /** 摘要超长截断并加省略号。 */
    private fun truncate(text: String): String =
        if (text.length <= MAX_DETAIL_LENGTH) text else text.take(MAX_DETAIL_LENGTH) + "…"

    /**
     * 把毫秒耗时格式化为时间线头部的展示文本（"45s" / "1m 20s"；不足 1 秒视为 0s）。
     */
    fun formatDuration(durationMs: Long): String {
        val totalSeconds = durationMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return if (minutes > 0) {
            String.format(Locale.ROOT, "%dm %02ds", minutes, seconds)
        } else {
            "${seconds}s"
        }
    }
}
