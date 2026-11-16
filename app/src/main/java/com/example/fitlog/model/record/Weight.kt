package com.example.fitlog.model.record

import kotlinx.serialization.Serializable

@Serializable
enum class WeightUnit { UNKNOWN, KG, LB }

@Serializable
enum class WeightBasis { UNKNOWN, PER_SIDE, TOTAL, BODYWEIGHT, ADDED, ASSISTED }
