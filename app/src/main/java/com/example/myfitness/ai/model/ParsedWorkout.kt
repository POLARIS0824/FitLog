package com.example.myfitness.ai.model

import kotlinx.serialization.Serializable

/**
 * AI 解析后返回的 JSON 结构体，对应单次训练的完整记录。
 *
 * @property date 训练日期，格式 "YYYY-MM-DD"
 * @property exercises 动作列表
 */
@Serializable
data class ParsedWorkout(
    val date: String,
    val exercises: List<ParsedExercise>,
)

/**
 * AI 解析出的单个动作。
 *
 * @property name 动作名称
 * @property sets 组列表
 */
@Serializable
data class ParsedExercise(
    val name: String,
    val sets: List<ParsedSet>,
)

/**
 * AI 解析出的单组记录。
 *
 * @property weightKg 重量（kg）
 * @property reps 次数
 */
@Serializable
data class ParsedSet(
    val weightKg: Float,
    val reps: Int,
)
