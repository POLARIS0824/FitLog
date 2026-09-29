package com.example.fitlog.navigation

import androidx.navigation3.runtime.NavKey
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
    data class Editor(
        val vault: String,
        val document: String? = null,
        val date: String = java.time.LocalDate.now().toString(),
        val sessionId: String = java.util.UUID.randomUUID().toString(),
        val directory: String = vault,
        val fileName: String = date + ".md",
    ) : FitLogRoute

    @Serializable
    data class VaultSetup(
        val createAfterSetup: Boolean = false,
        val requestId: String = java.util.UUID.randomUUID().toString(),
        val todayDate: String = java.time.LocalDate.now().toString(),
    ) : FitLogRoute

    @Serializable
    data class DiarySettings(val vault: String) : FitLogRoute
}
