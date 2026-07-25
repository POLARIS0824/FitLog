package com.example.fitlog.data.mapper

import com.example.fitlog.data.local.entity.BodyMetricEntity
import com.example.fitlog.model.BodyMetric

fun BodyMetricEntity.toModel(): BodyMetric {
    return BodyMetric(
        date = date,
        weightKg = weightKg,
    )
}

fun BodyMetric.toEntity(): BodyMetricEntity {
    return BodyMetricEntity(
        date = date,
        weightKg = weightKg,
    )
}
