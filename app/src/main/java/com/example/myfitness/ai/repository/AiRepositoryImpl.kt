package com.example.myfitness.ai.repository

import com.example.myfitness.ai.config.AiConfig
import com.example.myfitness.ai.model.AiMessage
import com.example.myfitness.ai.model.ChatCompletionRequest
import com.example.myfitness.ai.model.ParsedWorkout
import com.example.myfitness.ai.model.ResponseFormat
import com.example.myfitness.ai.remote.AiApi
import com.example.myfitness.domain.model.DailyCheckIn
import com.example.myfitness.domain.model.ExerciseEntry
import com.example.myfitness.domain.model.WorkoutSet
import kotlinx.serialization.json.Json
import java.time.LocalDate
import javax.inject.Inject

/**
 * [AiRepository] 的实现，通过大模型 API 解析非结构化的 Markdown 训练日记。
 */
class AiRepositoryImpl @Inject constructor(
    private val aiApi: AiApi,
    private val json: Json,
    private val aiConfig: AiConfig,
) : AiRepository {

    override suspend fun parseMarkdownToWorkout(markdown: String, date: LocalDate): DailyCheckIn? {
        val request = ChatCompletionRequest(
            model = aiConfig.model,
            messages = listOf(
                AiMessage(role = "system", content = buildSystemPrompt()),
                AiMessage(role = "user", content = buildUserPrompt(markdown, date)),
            ),
            responseFormat = ResponseFormat(type = "json_object"),
        )

        val response = aiApi.chatCompletions(request)
        val content = response.choices.firstOrNull()?.message?.content ?: return null

        return parseContent(content, date)
    }

    /**
     * 解析 AI 返回的 JSON 字符串为领域模型。
     */
    private fun parseContent(content: String, fallbackDate: LocalDate): DailyCheckIn? {
        return try {
            val parsed = json.decodeFromString(ParsedWorkout.serializer(), content)
            DailyCheckIn(
                id = 0L,
                date = runCatching { LocalDate.parse(parsed.date) }.getOrDefault(fallbackDate),
                exercises = parsed.exercises.map { ex ->
                    ExerciseEntry(
                        name = ex.name,
                        sets = ex.sets.map { WorkoutSet(it.weightKg, it.reps) },
                    )
                },
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun buildSystemPrompt(): String {
        return """
            你是一个健身数据解析助手。请根据用户提供的训练日记，解析出结构化数据。
            只输出 JSON，不要输出任何其他文字。
            JSON 格式如下：
            {
              "date": "YYYY-MM-DD",
              "exercises": [
                {
                  "name": "动作名称",
                  "sets": [
                    {"weightKg": 100.0, "reps": 5},
                    {"weightKg": 100.0, "reps": 5}
                  ]
                }
              ]
            }
        """.trimIndent()
    }

    private fun buildUserPrompt(markdown: String, date: LocalDate): String {
        return """
            日期: $date
            训练日记:
            $markdown
        """.trimIndent()
    }
}
