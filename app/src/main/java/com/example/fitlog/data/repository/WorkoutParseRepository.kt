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
import java.time.OffsetDateTime
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
open class WorkoutParseRepository @Inject constructor(
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
    open suspend fun parse(content: String, dateHint: LocalDate): Result<Workout> {
        val isoMetadata = try {
            extractIsoTimeMetadata(content)
        } catch (e: Exception) {
            FitLog.w(TAG, "ISO 时间元数据解析异常：${e.message}", e)
            return Result.failure(e)
        }

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

        val finalStartedAt: Long?
        val finalEndedAt: Long?

        if (isoMetadata != null) {
            // 确定性本地逻辑优先：使用 ISO 元数据，不依赖 AI 推断，完全覆盖 AI 返回的任何时间
            finalStartedAt = isoMetadata.startedAt
            finalEndedAt = isoMetadata.endedAt
        } else {
            // 兼容未携带 ISO 元数据的旧 Markdown：走既有 AI 解析与兜底逻辑
            val parsedStart = dto.startTime?.toEpochMillis(dateHint)
            val parsedEnd = dto.endTime?.toEpochMillis(dateHint)
            val timeInverted = parsedStart != null && parsedEnd != null && parsedEnd < parsedStart
            if (timeInverted) {
                FitLog.w(TAG, "导入解析时间倒挂：start=$parsedStart end=$parsedEnd，两端时间戳弃用")
            }
            finalStartedAt = parsedStart?.takeIf { !timeInverted }
            finalEndedAt = if (timeInverted) defaultEndedAt(dateHint) else (parsedEnd ?: defaultEndedAt(dateHint))
        }

        return Result.success(
            Workout(
                id = 0,
                userId = 0,
                date = dateHint,
                feelings = dto.feelings?.trim()?.takeIf { it.isNotBlank() },
                startedAt = finalStartedAt,
                endedAt = finalEndedAt,
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
    open suspend fun resolveExerciseKey(name: String): String? {
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

    data class IsoTimeMetadata(
        val startedAt: Long?,
        val endedAt: Long?,
    )

    companion object {
        private const val TAG = "WorkoutParseRepository"
        private val START_TIME_REGEX = """(?m)^[-*+]?\s*开始时间[：:]\s*(.+)$""".toRegex()
        private val END_TIME_REGEX = """(?m)^[-*+]?\s*结束时间[：:]\s*(.+)$""".toRegex()

        /**
         * 从 Markdown 文本中提取 ISO offset datetime 元数据。
         *
         * @return [IsoTimeMetadata]；若两项元数据均未出现则返回 null（走旧解析逻辑）
         * @throws IllegalArgumentException 当时间格式损坏或结束时间早于开始时间时抛出
         */
        fun extractIsoTimeMetadata(content: String): IsoTimeMetadata? {
            val startMatch = START_TIME_REGEX.find(content)
            val endMatch = END_TIME_REGEX.find(content)
            if (startMatch == null && endMatch == null) {
                return null
            }

            val startRaw = startMatch?.groupValues?.get(1)?.trim()
            val startedAt = when {
                startRaw == null || startRaw == "空" || startRaw.isEmpty() -> null
                else -> try {
                    OffsetDateTime.parse(startRaw).toInstant().toEpochMilli()
                } catch (e: Exception) {
                    throw IllegalArgumentException("开始时间格式错误：\"$startRaw\"", e)
                }
            }

            val endRaw = endMatch?.groupValues?.get(1)?.trim()
            val endedAt = when {
                endRaw == null || endRaw == "空" || endRaw.isEmpty() -> null
                else -> try {
                    OffsetDateTime.parse(endRaw).toInstant().toEpochMilli()
                } catch (e: Exception) {
                    throw IllegalArgumentException("结束时间格式错误：\"$endRaw\"", e)
                }
            }

            if (startedAt != null && endedAt != null && endedAt < startedAt) {
                throw IllegalArgumentException("结束时间早于开始时间：start=$startedAt, end=$endedAt")
            }

            return IsoTimeMetadata(startedAt, endedAt)
        }
    }
}
