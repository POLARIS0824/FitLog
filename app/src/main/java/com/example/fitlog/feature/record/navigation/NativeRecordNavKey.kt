package com.example.fitlog.feature.record.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data class NativeRecordNavKey(val recordId: String? = null,
    val sessionId: String = java.util.UUID.randomUUID().toString()) : NavKey
