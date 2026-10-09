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
    ) : FitLogRoute {
        init { requireVaultId(vaultId) }
    }

    @Serializable
    data class Editor(
        val vaultUri: String,
        val document: String? = null,
        val date: String = java.time.LocalDate.now().toString(),
        val sessionId: String = java.util.UUID.randomUUID().toString(),
        val directory: String = vaultUri,
        val fileName: String = date + ".md",
        val recoveryId: String? = null,
        val displayPath: String? = null,
        val vaultId: String,
    ) : FitLogRoute {
        init { requireVaultId(vaultId) }
    }

    @Serializable
    data class VaultSetup(
        val createAfterSetup: Boolean = false,
        val requestId: String = java.util.UUID.randomUUID().toString(),
        val todayDate: String = java.time.LocalDate.now().toString(),
    ) : FitLogRoute

    @Serializable
    data class DiarySettings(val vaultUri: String, val vaultId: String) : FitLogRoute {
        init { requireVaultId(vaultId) }
    }

    @Serializable
    data object RecoveryCenter : FitLogRoute

    @Serializable
    data object VaultManagement : FitLogRoute
}
