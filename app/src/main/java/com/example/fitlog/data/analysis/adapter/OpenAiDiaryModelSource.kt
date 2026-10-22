package com.example.fitlog.data.analysis.adapter

import com.example.fitlog.data.analysis.toDiaryFailure
import com.example.fitlog.data.llm.ChatMessage
import com.example.fitlog.data.llm.ChatOutcome
import com.example.fitlog.data.llm.ChatSpec
import com.example.fitlog.data.llm.OpenAiChatClient

/**
 * Diary extraction expressed on top of the shared OpenAI-compatible client.
 *
 * Everything protocol-level (URL, bearer auth, timeouts, status mapping, response shape) lives in
 * [OpenAiChatClient]; this class owns only the diary prompt and the mapping back to
 * [DiaryModelResponse]. One instance captures one model and one client binding, so later settings
 * edits cannot change a request that is already running.
 *
 * It does not own or close the shared client.
 */
internal class OpenAiDiaryModelSource(
    private val chat: OpenAiChatClient,
    private val modelId: String,
) : DiaryModelSource {

    init {
        require(modelId.isNotBlank()) { "A model ID is required" }
    }

    override suspend fun request(
        text: String,
        onTextDelta: suspend (String) -> Unit,
    ): DiaryModelResponse {
        val outcome = chat.chat(
            ChatSpec(
                modelId = modelId,
                messages = listOf(
                    ChatMessage.system(DiaryExtractionPrompt.instructions),
                    ChatMessage.user(text),
                ),
                jsonOutput = DIARY_JSON_OUTPUT,
            ),
            onTextDelta,
        )
        return when (outcome) {
            is ChatOutcome.Success -> DiaryModelResponse.Json(outcome.content)
            is ChatOutcome.Failure ->
                DiaryModelResponse.Failure(outcome.reason.toDiaryFailure(), outcome.raw)
        }
    }
}
