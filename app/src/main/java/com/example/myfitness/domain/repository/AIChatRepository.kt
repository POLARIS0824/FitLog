package com.example.myfitness.domain.repository

import com.example.myfitness.data.remote.dto.MessageDto

/**
 * AI 聊天的领域层仓库接口。
 */
interface AIChatRepository {

    /**
     * 发送对话消息并获取 AI 回复。
     *
     * @param url 完整的 API 请求地址
     * @param apiKey 当前提供商的 API Key
     * @param model 模型标识
     * @param messages 对话上下文消息列表
     * @return AI 生成的回复文本
     */
    suspend fun sendMessage(
        url: String,
        apiKey: String,
        model: String,
        messages: List<MessageDto>,
    ): String
}
