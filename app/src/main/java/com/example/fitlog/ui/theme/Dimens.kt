package com.example.fitlog.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Component sizes that are not spacing: page widths, chart heights, stroke widths, icon boxes.
 * Each area owns one class named after its screens; values are named by the element they size.
 * Read through `FitLogTheme.dimens.<area>.<name>`.
 */
@Immutable
class FitLogDimens {
    val page = PageDimens()
    val navigation = NavigationToolbarDimens()
    val components = ComponentDimens()
    val diary = DiaryDimens()
    val insight = InsightDimens()
    val log = LogDimens()
    val record = RecordDimens()
    val settings = SettingsDimens()
    val vault = VaultDimens()
    val today = TodayDimens()
    val glass = GlassDimens()
}

/** Page frame: gutters, content width caps and space reserved for floating navigation. */
@Immutable
class PageDimens {
    /** Windows narrower than this use [compactPadding]; Material's compact breakpoint. */
    val compactBreakpoint: Dp = 600.dp
    val compactPadding: Dp = 16.dp
    val expandedPadding: Dp = 24.dp
    val contentWidth: Dp = 640.dp
    val wideContentWidth: Dp = 840.dp
    val navigationBottomPadding: Dp = 100.dp
    val todayBottomPadding: Dp = 120.dp
    val sheetPadding: Dp = 24.dp
}

/**
 * Values of the bottom navigation toolbar and its FAB menu, moved here unchanged.
 * Changing any of them changes the protected navigation; see AGENTS.md.
 */
@Immutable
class NavigationToolbarDimens {
    val itemGap: Dp = 8.dp
    val shadowElevation: Dp = 6.dp
    val tabHeight: Dp = 48.dp
    val tabIconSize: Dp = 24.dp
    val fabIconCheckedRotation: Float = 45f
}

internal val LocalFitLogDimens = staticCompositionLocalOf { FitLogDimens() }

/** Horizontal page gutter: 16dp on compact windows, 24dp from the medium breakpoint up. */
@Composable
fun fitLogPagePadding(): Dp {
    val page = FitLogTheme.dimens.page
    val windowWidth = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }
    return if (windowWidth < page.compactBreakpoint) page.compactPadding else page.expandedPadding
}
