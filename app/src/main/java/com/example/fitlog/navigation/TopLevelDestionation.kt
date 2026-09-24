package com.example.fitlog.navigation

// FitLog 有哪些顶级页面，以及这些页面在导航 UI 中怎么显示
data class TopLevelDestination(
    val route: FitLogRoute,
    val label: String
)

// 负责这个 route 在 App 顶级导航中的展示信息
val topLevelDestinations = listOf(
    TopLevelDestination(
        route = FitLogRoute.Today,
        label = "Today",
    ),
    TopLevelDestination(
        route = FitLogRoute.Log,
        label = "Log",
    ),
    TopLevelDestination(
        route = FitLogRoute.Insight,
        label = "Insight",
    ),
)