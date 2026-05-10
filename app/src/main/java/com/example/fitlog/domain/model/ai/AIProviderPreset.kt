package com.example.fitlog.domain.model.ai

data class AIProviderPreset(
    val id: String,
    val name: String,
    val baseUrl: String,
    val model: String,
)