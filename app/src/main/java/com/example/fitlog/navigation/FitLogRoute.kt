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
    data object Editor : FitLogRoute
}