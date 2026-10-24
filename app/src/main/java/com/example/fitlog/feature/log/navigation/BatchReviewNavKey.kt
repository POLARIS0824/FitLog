package com.example.fitlog.feature.log.navigation

import androidx.navigation3.runtime.NavKey
import com.example.fitlog.model.source.requireVaultId
import kotlinx.serialization.Serializable

@Serializable
data class BatchReviewNavKey(val vaultId: String, val vaultUri: String) : NavKey { init { requireVaultId(vaultId) } }
