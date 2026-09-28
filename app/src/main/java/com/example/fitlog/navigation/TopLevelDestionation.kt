package com.example.fitlog.navigation

import androidx.annotation.DrawableRes
import com.example.fitlog.R

// FitLog 有哪些顶级页面，以及这些页面在导航 UI 中怎么显示
data class TopLevelDestination(
    val route: FitLogRoute,
    val label: String,
    @get:DrawableRes val selectedIconRes: Int,
    @get:DrawableRes val unselectedIconRes: Int,
)

// 负责这个 route 在 App 顶级导航中的展示信息
val topLevelDestinations = listOf(
    TopLevelDestination(
        route = FitLogRoute.Today,
        label = "Today",
        selectedIconRes = R.drawable.home_filled_24px,
        unselectedIconRes = R.drawable.home_24px,
    ),
    TopLevelDestination(
        route = FitLogRoute.Log,
        label = "Log",
        selectedIconRes = R.drawable.analytics_filled_24px,
        unselectedIconRes = R.drawable.analytics_24px,
    ),
    TopLevelDestination(
        route = FitLogRoute.Insight,
        label = "Insight",
        selectedIconRes = R.drawable.auto_awesome_filled_24px,
        unselectedIconRes = R.drawable.auto_awesome_24px,
    ),
)