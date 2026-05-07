package com.example.myfitness.ai.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * OpenAI 兼容的 Chat Completions 请求体。
 *
 * @property model 模型名称
 * @property messages 对话消息列表
 * @property responseFormat 强制响应格式（如 json_object）
 */
@Serializable
data class ChatCompletionRequest(
    val model: String,
    val messages: List<AiMessage>,
    @SerialName("response_format")
    val responseFormat: ResponseFormat? = null,
)

/**
 * 单条对话消息。
 *
 * @property role 角色：system / user / assistant
 * @property content 消息内容
 */
@Serializable
data class AiMessage(
    val role: String,
    val content: String,
)

/**
 * 响应格式约束。
 *
 * @property type 格式类型，如 "json_object"
 */
@Serializable
data class ResponseFormat(
    val type: String,
)

/**
 * OpenAI 兼容的 Chat Completions 响应体。
 *
 * @property id 响应 ID
 * @property model 实际使用的模型
 * @property choices 生成的回复列表
 */
@Serializable
data class ChatCompletionResponse(
    val id: String,
    val model: String,
    val choices: List<Choice>,
)

/**
 * 单条生成结果。
 *
 * @property index 结果序号
 * @property message AI 回复消息
 * @property finishReason 生成结束原因
 */
@Serializable
data class Choice(
    val index: Int,
    val message: AiMessage,
    @SerialName("finish_reason")
    val finishReason: String?,
)
