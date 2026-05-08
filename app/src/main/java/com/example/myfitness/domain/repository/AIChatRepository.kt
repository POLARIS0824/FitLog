package com.example.myfitness.domain.repository

import com.example.myfitness.domain.model.ChatMessage

/**
 * AI 聊天的领域层仓库接口。
 */
interface AIChatRepository {

    /**
     * 发送对话消息并获取 AI 回复。
     *
     * 具体使用哪个 AI 提供商、API Key 与模型，由实现层根据当前激活配置自行决定。
     *
     * @param messages 对话上下文消息列表
     * @return AI 生成的回复文本
     * @throws IllegalStateException 当没有激活配置或请求失败时抛出
     */
    suspend fun sendChat(
        messages: List<ChatMessage>,
    ): String
}
