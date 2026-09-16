package com.example.fitlog.model.ai

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * AI 结构化解析训练日志的输出 DTO 与容错解析（导入流程「原文 → 结构化明细」）。
 *
 * 与 [CoachInsight] 同一模式：模型按 [WorkoutParsePrompt] 的输出契约返回单个
 * JSON 对象（部分服务商不支持 json_object，可能混入代码块围栏等杂质），
 * 客户端截取首尾花括号后用宽松 Json 解码，失败返回 null 由调用方降级。
 */

/**
 * 单日训练解析结果。
 *
 * 日期不在输出契约内：文件名/节标题日期已由扫描器确定（dateHint），
 * 模型只负责提取动作明细，避免模型改写日期引入错位。
 *
 * @param feelings 原文中的训练感受/备注（状态、体重、RPE 等），没有则 null
 * @param startTime 训练开始时间 "HH:mm"，原文没有则 null
 * @param endTime 训练结束时间 "HH:mm"，原文没有则 null
 * @param exercises 动作列表（顺序与原文一致）；为空时调用方按解析失败降级
 */
@Serializable
data class ParsedWorkoutDto(
    val feelings: String? = null,
    val startTime: String? = null,
    val endTime: String? = null,
    val exercises: List<ParsedExerciseDto> = emptyList(),
)

/**
 * 单个动作的解析结果。
 *
 * @param name 动作名（保留原文写法，通常为中文）；由调用方匹配动作库 key
 * @param sets 组列表（顺序与原文一致）
 */
@Serializable
data class ParsedExerciseDto(
    val name: String? = null,
    val sets: List<ParsedSetDto> = emptyList(),
)

/**
 * 单组训练的解析结果。
 *
 * @param weightKg 重量（kg，自重/无负重为 0，原文未写默认 0）
 * @param reps 次数（正整数；模型输出缺失时由调用方按 0 处理，导入前可编辑修正）
 * @param type "WARMUP"（热身）或 "WORKING"（正式），缺省按正式组处理
 */
@Serializable
data class ParsedSetDto(
    val weightKg: Double? = null,
    val reps: Int? = null,
    val type: String? = null,
)

/** 宽松解码：忽略未知字段 + 容忍非严格 JSON（部分服务商 json_object 输出不规范）。 */
private val workoutParseJson = Json { ignoreUnknownKeys = true; isLenient = true }

/**
 * 从模型原始回复中容错解析训练 JSON。
 *
 * 截取首个 `{` 到最后一个 `}` 的子串再解码，容忍代码块围栏与前后杂文字；
 * 无花括号或结构不符时返回 null，调用方按解析失败降级（该条走仅存档兜底）。
 *
 * @param raw AI 回复的 message.content 原文
 * @return 解析成功返回 [ParsedWorkoutDto]，失败返回 null
 */
fun parseWorkoutJson(raw: String): ParsedWorkoutDto? {
    val start = raw.indexOf('{')
    val end = raw.lastIndexOf('}')
    if (start < 0 || end <= start) return null
    return runCatching {
        workoutParseJson.decodeFromString<ParsedWorkoutDto>(raw.substring(start, end + 1))
    }.getOrNull()
}
