package com.example.fitlog.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.example.fitlog.R

/**
 * 把 route、文字资源、选中图标、未选中图标绑在一起，topLevelDestinations 就是底部主导航的三个入口
 */
data class TopLevelDestination(
    val route: FitLogRoute,
    @get:StringRes val labelRes: Int,
    @get:DrawableRes val selectedIconRes: Int,
    @get:DrawableRes val unselectedIconRes: Int,
)

// 负责这个 route 在 App 顶级导航中的展示信息
val topLevelDestinations = listOf(
    TopLevelDestination(
        route = FitLogRoute.Today,
        labelRes = R.string.nav_today,
        selectedIconRes = R.drawable.home_filled_24px,
        unselectedIconRes = R.drawable.home_24px,
    ),
    TopLevelDestination(
        route = FitLogRoute.Log,
        labelRes = R.string.nav_log,
        selectedIconRes = R.drawable.analytics_filled_24px,
        unselectedIconRes = R.drawable.analytics_24px,
    ),
    TopLevelDestination(
        route = FitLogRoute.Insight,
        labelRes = R.string.nav_insight,
        selectedIconRes = R.drawable.auto_awesome_filled_24px,
        unselectedIconRes = R.drawable.auto_awesome_24px,
    ),
)