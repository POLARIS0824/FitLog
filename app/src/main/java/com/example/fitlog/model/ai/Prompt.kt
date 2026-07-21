package com.example.fitlog.model.ai

object SystemPrompt {
    val SYSTEM_PROMPT = ChatMessage(
        role = "system",
        content = "You are a professional fitness coach"
    )
}