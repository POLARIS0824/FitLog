package com.example.fitlog.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * OpenAI 兼容 GET /models 的响应体。
 *
 * @property data 模型列表
 */
@Serializable
data class ModelsResponseDto(
    val data: List<ModelItemDto>,
)

/**
 * 单个模型项。仅取 [id]，其余字段由 Json 的 ignoreUnknownKeys 忽略。
 *
 * @property id 模型标识，如 "deepseek-chat"
 */
@Serializable
data class ModelItemDto(
    val id: String,
)
