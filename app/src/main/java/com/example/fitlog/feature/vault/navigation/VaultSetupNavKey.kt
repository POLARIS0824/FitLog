package com.example.fitlog.feature.vault.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data class VaultSetupNavKey(val requestId: String = java.util.UUID.randomUUID().toString()) : NavKey
