package com.example.myfitness.ai.repository

import com.example.myfitness.domain.model.DailyCheckIn
import java.time.LocalDate

/**
 * AI 解析能力的领域层接口。
 */
interface AiRepository {
    /**
     * 将 Markdown 格式的训练日记解析为结构化训练记录。
     *
     * @param markdown 原始 Markdown 文本
     * @param date 训练日期，用于校验 AI 解析结果
     * @return 解析后的 [DailyCheckIn]，若解析失败则返回 null
     */
    suspend fun parseMarkdownToWorkout(markdown: String, date: LocalDate): DailyCheckIn?
}
