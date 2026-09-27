package com.example.fitlog.navigation

import androidx.annotation.DrawableRes
import com.example.fitlog.R

// FitLog 有哪些顶级页面，以及这些页面在导航 UI 中怎么显示
data class TopLevelDestination(
    val route: FitLogRoute,
    val label: String,
    @DrawableRes val iconRes: Int,
)

// 负责这个 route 在 App 顶级导航中的展示信息
val topLevelDestinations = listOf(
    TopLevelDestination(
        route = FitLogRoute.Today,
        label = "Today",
        iconRes = R.drawable.home_24px
    ),
    TopLevelDestination(
        route = FitLogRoute.Log,
        label = "Log",
        iconRes = R.drawable.analytics_24px
    ),
    TopLevelDestination(
        route = FitLogRoute.Insight,
        label = "Insight",
        iconRes = R.drawable.auto_awesome_24px
    ),
)