package com.example.fitlog.feature.diary.navigation

import androidx.navigation3.runtime.NavKey
import com.example.fitlog.model.source.requireVaultId
import kotlinx.serialization.Serializable

@Serializable
data class DiaryDetailNavKey(val vaultUri: String, val vaultId: String, val document: String,
    val relPath: String, val directory: String, val fileName: String,
    val openAnalysis: Boolean = false, val openCandidate: Boolean = false) : NavKey { init { requireVaultId(vaultId) } }
