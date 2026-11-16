package com.example.fitlog.feature.log

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.example.fitlog.data.settings.LogSortOrder

internal data class LogActions(
    val refresh: () -> Unit = {},
    val changeSort: (LogSortOrder) -> Unit = {},
    val toggleMissing: () -> Unit = {},
    val filter: (LogStatusFilter) -> Unit = {},
    val startAnalysis: (LogAnalysisRange, Boolean) -> Unit = { _, _ -> },
    val cancelAnalysis: () -> Unit = {},
    val openAiSettings: () -> Unit = {},
)
