package com.example.fitlog.model.ai

/**
 * AI 对话中的单条消息。
 *
 * @param role 消息角色，如 "system"、"user"、"assistant"
 * @param content 消息文本内容
 * @param id 本地展示用唯一标识（作为 LazyColumn 的稳定 key，支撑删除/流式更新）；
 *     由调用方在创建/落屏时分配，网络往返（[com.example.fitlog.data.mapper.ChatMessageMapper]）保留默认值 0
 */
data class ChatMessage(
    val role: String,
    val content: String,
    val id: Long = 0L,
)