package com.example.fitlog.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * FitLog 的全局表面语义角色：页面背景 / 卡片 / 底部导航栏 / 顶栏折叠背景。
 *
 * 四者的档位关系由 [Companion.from] 一处定义（唯一改色入口），页面与通用组件
 * 一律经 [MaterialTheme.fitLogColors] 取用，不直读 colorScheme 的
 * surfaceContainer* 槽位——调整整体风格（如切"经典 M3"：surface /
 * surfaceContainerLow / surfaceContainer）时任何页面都无需改动。
 *
 * 注意：badge、图表强调色、渐变等局部用色不属于这些角色，仍直读 colorScheme。
 *
 * @param pageBackground 页面（Scaffold）与顶栏未滚动时的背景
 * @param card 卡片容器背景（如 [com.example.fitlog.ui.components.FitLogCard]）
 * @param navigationBar 底部导航栏背景
 * @param topBarScrolled 顶栏滚动折叠后的背景（CollapsingTitleScaffold 折叠过渡端、
 *   各页 scrolledContainerColor）
 */
@Immutable
data class FitLogColors(
    val pageBackground: Color,
    val card: Color,
    val navigationBar: Color,
    val topBarScrolled: Color,
) {
    companion object {
        /**
         * 从解析后的 [ColorScheme] 推导各角色：动态取色、深浅色模式自动跟随，
         * 本函数只决定"用哪一档"。
         */
//        fun from(scheme: ColorScheme) = FitLogColors(
//            pageBackground = scheme.surfaceContainerLow,
//            card = scheme.surfaceContainerLowest,
//            navigationBar = scheme.surface,
//            topBarScrolled = scheme.surfaceContainer,
//        )

        fun from(scheme: ColorScheme) = FitLogColors(
            pageBackground = scheme.surface,
            card = scheme.surfaceContainerLow,
            navigationBar = scheme.surfaceContainer,
            topBarScrolled = scheme.surfaceContainer,
        )
    }
}

/**
 * [FitLogColors] 的注入载体；默认值为基线浅色，兜底 provider 外误用的极端场景。
 */
val LocalFitLogColors = staticCompositionLocalOf { FitLogColors.from(lightColorScheme()) }

/**
 * 语义角色访问入口：`MaterialTheme.fitLogColors.pageBackground` 等。
 */
val MaterialTheme.fitLogColors: FitLogColors
    @Composable
    @ReadOnlyComposable
    get() = LocalFitLogColors.current
