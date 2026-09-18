package com.example.fitlog.data.repository

import com.example.fitlog.model.ExerciseLog
import com.example.fitlog.model.SetLog
import com.example.fitlog.model.SetType
import com.example.fitlog.model.Workout
import com.example.fitlog.model.ai.ParsedExerciseDto
import com.example.fitlog.model.ai.ParsedSetDto
import com.example.fitlog.model.ai.WorkoutParsePrompt
import com.example.fitlog.model.ai.parseWorkoutJson
import com.example.fitlog.util.ExerciseDisplayName
import com.example.fitlog.util.log.FitLog
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject

/**
 * AI 结构化解析训练日志的仓库（导入流程「原文 → 结构化明细」管线）。
 *
 * 单轮 JSON 调用（非流式）：[AIChatRepository.chat]（jsonMode）→ 容错解析
 * （[parseWorkoutJson]）→ 动作名匹配动作库 → 组装 domain [Workout]。
 * 本仓库不落库：写入仍由调用方走 [WorkoutRepository].insert/update，
 * 解析结果在用户确认前仅存在于 UI 编辑缓冲中。
 */
class WorkoutParseRepository @Inject constructor(
    private val aiChatRepository: AIChatRepository,
    private val exerciseRepository: ExerciseRepository,
) {

    /**
     * 把一天的 Markdown 原文解析为结构化训练记录。
     *
     * @param content 预处理后的原文（MarkdownParser.preprocess 产物）
     * @param dateHint 扫描器确定的日期（文件名/节标题），作为记录日期与时间换算基准
     * @return [Result.success] 含已匹配动作库的 Workout（id=0，未落库，rawContent 已填）；
     *     解析不出任何动作明细时按失败处理（调用方走「仅存档」兜底）
     */
    suspend fun parse(content: String, dateHint: LocalDate): Result<Workout> {
        val reply = aiChatRepository.chat(
            messages = WorkoutParsePrompt.buildMessages(dateHint, content),
            temperature = 0.1,
            maxTokens = 2000,
            jsonMode = true,
        ).getOrElse {
            FitLog.w(TAG, "导入解析 AI 请求失败", it)
            return Result.failure(it)
        }
        val dto = parseWorkoutJson(reply.content)
        if (dto == null) {
            FitLog.w(TAG, "导入解析失败：AI 返回内容无法解析为 JSON（回复长度=${reply.content.length}）")
            return Result.failure(IllegalStateException("AI 返回内容无法解析为训练记录"))
        }
        if (dto.exercises.isEmpty()) {
            FitLog.w(TAG, "导入解析失败：AI 未能从原文解析出动作明细")
            return Result.failure(IllegalStateException("AI 未能从原文解析出动作明细"))
        }
        val exercises = dto.exercises.mapNotNull { it.toExerciseLog() }
        if (exercises.isEmpty()) {
            FitLog.w(TAG, "导入解析失败：动作名全部为空白，无有效动作")
            return Result.failure(IllegalStateException("AI 未能从原文解析出有效的动作"))
        }
        // 时间自洽性：模型可能输出 endTime < startTime（笔误/12 小时制混乱），
        // 倒挂的时间轴会产出负时长与"结束早于开始"的脏数据——单边时间戳没有
        // 意义，两端一并弃用，endedAt 走当日 23:59 兜底（缺失时的既有语义）
        val parsedStart = dto.startTime?.toEpochMillis(dateHint)
        val parsedEnd = dto.endTime?.toEpochMillis(dateHint)
        val timeInverted = parsedStart != null && parsedEnd != null && parsedEnd < parsedStart
        if (timeInverted) {
            FitLog.w(TAG, "导入解析时间倒挂：start=$parsedStart end=$parsedEnd，两端时间戳弃用")
        }
        return Result.success(
            Workout(
                id = 0,
                userId = 0,
                date = dateHint,
                feelings = dto.feelings?.trim()?.takeIf { it.isNotBlank() },
                startedAt = parsedStart?.takeIf { !timeInverted },
                // 结束时间缺失时取当日 23:59：导入的历史记录无真实结束时刻可考，
                // 但 endedAt 为空会让 isCountable=false，整段历史不计入完成次数
                endedAt = if (timeInverted) defaultEndedAt(dateHint) else (parsedEnd ?: defaultEndedAt(dateHint)),
                exercises = exercises,
                rawContent = content,
            ),
        )
    }

    /**
     * 动作名 → 动作库 kebab-case key 的多级匹配（解析与编辑保存共用）：
     * 中文名反查（[ExerciseDisplayName.keyForDisplayName]）→ 库内验证 →
     * 英文展示名精确 → 模糊搜索首条。全部未命中返回 null。
     *
     * 铁律：绝不返回未经验证存在于动作库的 key——exercise_logs.exerciseKey
     * 有外键约束，脏 key 会让整条 insert 事务回滚。
     *
     * @param name 动作名（AI 解析结果或用户编辑后的输入）
     * @return 动作库 key；未匹配返回 null（自由文本名入库，合法但无关联统计）
     */
    suspend fun resolveExerciseKey(name: String): String? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        // 1. 中文名反查（用户日志里的动作名通常是 ExerciseDisplayName 维护的中文名）
        ExerciseDisplayName.keyForDisplayName(trimmed)?.let { key ->
            if (exerciseRepository.getById(key) != null) return key
        }
        // 2. 英文展示名精确匹配（编辑弹层从动作库选择、或 AI 直接输出了库内英文名）
        exerciseRepository.getByName(trimmed)?.let { return it.id }
        // 3. 模糊搜索兜底：LIKE 命中多条时取首条（导入场景足够，用户可在编辑弹层修正）
        val fuzzy = exerciseRepository.searchByName(trimmed).firstOrNull()?.id
        if (fuzzy == null) {
            // 全部匹配层级落空：动作将以自由文本名入库（合法但无关联统计），
            // 此前完全不可见——补一条留痕供事后核对数据质量
            FitLog.w(TAG, "动作名未匹配到动作库，将以自由文本入库：\"$trimmed\"")
        } else {
            FitLog.d(TAG, "动作名模糊匹配命中：\"$trimmed\" → $fuzzy")
        }
        return fuzzy
    }

    /** 单条解析动作 → [ExerciseLog]：name 为空/空白的条目丢弃。 */
    private suspend fun ParsedExerciseDto.toExerciseLog(): ExerciseLog? {
        val exerciseName = name?.trim().takeUnless { it.isNullOrEmpty() } ?: return null
        return ExerciseLog(
            name = exerciseName,
            exerciseKey = resolveExerciseKey(exerciseName),
            sets = sets.map { it.toSetLog() },
        )
    }

    /** 单条解析组 → [SetLog]：缺省重量 0、缺省次数 0（占位，导入前可编辑/会被清洗）。 */
    private fun ParsedSetDto.toSetLog(): SetLog = SetLog(
        weightKg = (weightKg ?: 0.0).toFloat().coerceAtLeast(0f),
        reps = (reps ?: 0).coerceAtLeast(0),
        setType = if (type.equals("WARMUP", ignoreCase = true)) SetType.WARMUP else SetType.WORKING,
    )

    /** 未解析到结束时间时的兜底：当日 23:59（epoch millis）。 */
    private fun defaultEndedAt(date: LocalDate): Long =
        date.atTime(23, 59).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** "HH:mm" → 当日 epoch millis；模型输出的时间格式不可信，非法时返回 null 忽略。 */
    private fun String.toEpochMillis(date: LocalDate): Long? = runCatching {
        LocalTime.parse(trim()).atDate(date).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }.getOrNull()

    private companion object {
        private const val TAG = "WorkoutParseRepository"
    }
}
