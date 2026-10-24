package com.example.fitlog.feature.insight.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data class ExerciseHistoryNavKey(val nameKey: String, val range: String = "NINETY") : NavKey { init { require(nameKey.isNotBlank()) } }
