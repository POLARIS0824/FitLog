package com.example.fitlog.util

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.HttpException

/**
 * AI 网络错误的用户可读信息映射（AIChatRepository 与 OpenAiCompatibleModel 共享）。
 *
 * HTTP 错误（[HttpException]）优先提取服务商响应体里的 `error.message`
 * （如 "Invalid value for 'tools[0].type'"），比笼统的 "HTTP 400 Bad Request"
 * 更能定位问题；解析失败则退回原始 body 文本。非 HTTP 异常返回原始 message。
 */
object AiErrorMessages {

    /** 宽松 JSON：只做错误体字段提取，容忍任何畸形输入。 */
    private val json = Json { ignoreUnknownKeys = true }

    /** 把异常转成用户可读的错误信息。 */
    fun toUserFacingMessage(throwable: Throwable): String = with(throwable) {
        if (this is HttpException) {
            val body = response()?.errorBody()?.string()
            if (!body.isNullOrBlank()) {
                val providerMessage = runCatching {
                    json.parseToJsonElement(body)
                        .jsonObject["error"]?.jsonObject?.get("message")
                        ?.jsonPrimitive?.contentOrNull
                }.getOrNull()
                if (!providerMessage.isNullOrBlank()) {
                    return "${message ?: "HTTP ${code()}"}：$providerMessage"
                }
                return "${message ?: "HTTP ${code()}"}（$body）"
            }
        }
        return message ?: "AI 请求失败"
    }
}
