package com.example.fitlog.model

import java.time.LocalDate

/**
 * 某天的身体指标记录（当前仅体重）。
 *
 * 以日期为业务标识：同一天重复记录时新值覆盖旧值。
 */
data class BodyMetric(
    val date: LocalDate,
    val weightKg: Float,
)
