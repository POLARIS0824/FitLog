package com.example.fitlog.navigation

import androidx.navigation3.runtime.NavKey
import com.example.fitlog.data.vault.requireVaultId
import kotlinx.serialization.Serializable

// 负责导航身份 包含顶级页面与二级页面...
@Serializable
sealed interface FitLogRoute : NavKey {

    @Serializable
    data object Today : FitLogRoute

    @Serializable
    data object Log : FitLogRoute

    @Serializable
    data object Insight : FitLogRoute

    @Serializable
    data class ExerciseHistory(val nameKey: String, val range: String = "NINETY") : FitLogRoute {
        init { require(nameKey.isNotBlank()) }
    }

    @Serializable
    data object Settings : FitLogRoute

    @Serializable
    data object Appearance : FitLogRoute

    @Serializable
    data object AiSettings : FitLogRoute

    @Serializable
    data class BatchReview(val vaultId: String, val vaultUri: String) : FitLogRoute {
        init { requireVaultId(vaultId) }
    }

    @Serializable
    data class DiaryDetail(
        val vaultUri: String,
        val vaultId: String,
        val document: String,
        val relPath: String,
        val directory: String,
        val fileName: String,
        val openAnalysis: Boolean = false,
        val openCandidate: Boolean = false,
    ) : FitLogRoute {
        init { requireVaultId(vaultId) }
    }

    @Serializable
    data class NativeRecord(val recordId: String? = null,
        val sessionId: String = java.util.UUID.randomUUID().toString()) : FitLogRoute

    @Serializable
    data class VaultSetup(
        val requestId: String = java.util.UUID.randomUUID().toString(),
    ) : FitLogRoute

    @Serializable
    data object VaultManagement : FitLogRoute
}
