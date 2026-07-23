package com.example.fitlog.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * OpenAI 兼容 Chat Completions API 的消息对象。
 *
 * @param role 消息角色，如 "system"、"user"、"assistant"
 * @param content 消息内容
 */
@Serializable
data class MessageDto(
    val role: String,
    val content: String?,
)
