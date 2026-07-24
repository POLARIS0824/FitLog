package com.example.fitlog.testing

import com.example.fitlog.data.remote.AIApi
import com.example.fitlog.data.remote.dto.ChatCompletionRequestDto
import com.example.fitlog.data.remote.dto.ChatCompletionResponseDto
import com.example.fitlog.data.remote.dto.ChoiceDto
import com.example.fitlog.data.remote.dto.MessageDto
import com.example.fitlog.data.remote.dto.ModelItemDto
import com.example.fitlog.data.remote.dto.ModelsResponseDto

/**
 * [AIApi] 的测试替身（Fake）。
 *
 * 不依赖 Mock 框架：记录每次调用的入参供断言，
 * 响应行为通过可替换的 handler 变量编程（成功、失败、阻塞等）。
 */
class FakeAIApi : AIApi {

    /**
     * 一次 chatCompletions 调用的完整入参记录。
     */
    data class ChatCall(
        val url: String,
        val headers: Map<String, String>,
        val request: ChatCompletionRequestDto,
    )

    /**
     * 一次 models 调用的完整入参记录。
     */
    data class ModelsCall(
        val url: String,
        val headers: Map<String, String>,
    )

    /** 已发生的 chat 调用列表（按调用顺序）。 */
    val chatCalls = mutableListOf<ChatCall>()

    /** 已发生的 models 调用列表（按调用顺序）。 */
    val modelsCalls = mutableListOf<ModelsCall>()

    /** chat 响应行为，默认返回一条 assistant 回复。 */
    var chatHandler: suspend (ChatCall) -> ChatCompletionResponseDto = {
        ChatCompletionResponseDto(
            choices = listOf(ChoiceDto(message = MessageDto(role = "assistant", content = "默认回复"))),
        )
    }

    /** models 响应行为，默认返回两个模型。 */
    var modelsHandler: suspend (ModelsCall) -> ModelsResponseDto = {
        ModelsResponseDto(data = listOf(ModelItemDto("model-a"), ModelItemDto("model-b")))
    }

    override suspend fun chatCompletions(
        url: String,
        headers: Map<String, String>,
        request: ChatCompletionRequestDto,
    ): ChatCompletionResponseDto {
        val call = ChatCall(url, headers, request)
        chatCalls += call
        return chatHandler(call)
    }

    override suspend fun models(
        url: String,
        headers: Map<String, String>,
    ): ModelsResponseDto {
        val call = ModelsCall(url, headers)
        modelsCalls += call
        return modelsHandler(call)
    }
}
