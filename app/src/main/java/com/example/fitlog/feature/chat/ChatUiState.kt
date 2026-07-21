package com.example.fitlog.feature.chat

import com.example.fitlog.model.ai.ChatMessage

/**
 * AI 教练界面的 UI 状态。
 */
data class ChatUiState(
    // TODO: 需要将 ChatMessage 更换为 ChatListItem
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val input: String = "",
    val isSending: Boolean = false,
    val errorMessage: String? = null,
)