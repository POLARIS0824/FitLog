package com.example.fitlog.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Liquid Glass look (ui/glass, excluding the backdrop library). The glass keeps its own non-Material
 * geometry, so these values sit off the spacing and corner scales on purpose. Values typed
 * `Float` are dp (or a ratio) fed to the backdrop renderer or a spring, not layout.
 */
@Immutable
class GlassDimens {
    // Surfaces.

    /** Corner of a glass card and the default glass surface. */
    val surfaceCorner: Dp = 28.dp

    /** Gutter of glass pages, the glass top bar and the glass navigation. */
    val pageGutter: Dp = 20.dp

    /** Frost fallback: blur radius in dp and the hairline border around it. */
    val frostBlurRadius: Float = 24f
    val frostBorderWidth: Dp = 0.75.dp

    // Liquid Glass rendering.

    /** Resolution the glass records and processes its backdrop at (a third, as BitChord). */
    val resolutionScale: Float = 0.33f

    /** Surfaces up to this size (dp, smaller side) are "thin" glass: bars, pills, buttons. */
    val thinSurfaceSize: Float = 56f

    /** From this size (dp, smaller side) a surface is "large" glass: a sheet or a card. */
    val largeSurfaceSize: Float = 200f

    /** Vibrancy of the refracted page. */
    val darkBrightness: Float = -0.03f
    val darkSaturation: Float = 1.6f
    val lightBrightness: Float = 0.02f
    val lightSaturation: Float = 1.35f

    /** Blur radius (dp) of thin glass and of large glass; sizes in between interpolate. */
    val thinBlurRadius: Float = 3.5f
    val largeBlurRadius: Float = 14f

    /** Lens depth of thin glass: this fraction of the smaller side, capped at [thinLensMaxHeight] dp. */
    val thinLensHeightFraction: Float = 0.42f
    val thinLensMaxHeight: Float = 22f

    /** Lens depth (dp) of large glass. */
    val largeLensHeight: Float = 30f

    /** Refraction amount as a multiple of the lens depth, thin and large. */
    val thinLensAmountRatio: Float = 1.7f
    val largeLensAmountRatio: Float = 1.2f

    /** Specular rim width and the soft shadow under a glass surface. */
    val rimWidth: Dp = 0.75.dp
    val darkShadowRadius: Dp = 18.dp
    val lightShadowRadius: Dp = 16.dp

    // Press.

    /** Scale a glass surface loses at full press. */
    val pressScaleDepth: Float = 0.04f

    /** How much the lens bulges at full press (fraction of its refraction). */
    val pressBulge: Float = 0.35f

    /** Press spring going down, and the release spring with a touch of overshoot. */
    val pressSpringStiffness: Float = 900f
    val releaseSpringDampingRatio: Float = 0.62f
    val releaseSpringStiffness: Float = 1500f

    // Buttons and fields.

    val buttonMinHeight: Dp = 52.dp
    val buttonHorizontalPadding: Dp = 20.dp
    val buttonVerticalPadding: Dp = 14.dp
    val buttonIconGap: Dp = 10.dp

    /** Glyph inside glass buttons and icon buttons. */
    val iconSize: Dp = 22.dp
    val iconButtonSize: Dp = 48.dp
    val searchFieldMinHeight: Dp = 56.dp

    // Segmented control.

    val segmentedItemMinWidth: Dp = 88.dp
    val segmentedItemMinHeight: Dp = 44.dp
    val segmentedItemGap: Dp = 2.dp
    val segmentedItemHorizontalPadding: Dp = 14.dp

    // Navigation and tab bar.

    val navigationMaxWidth: Dp = 440.dp

    /** Between the action buttons above the bar, and between the tab pill and the standalone tab. */
    val navigationActionGap: Dp = 10.dp
    val tabBarClusterGap: Dp = 10.dp
    val tabBarPadding: Dp = 6.dp
    val tabVerticalPadding: Dp = 10.dp

    /** [com.example.fitlog.ui.glass.FloatingTabBarDefaults] tab padding, as vendored. */
    val defaultTabVerticalPadding: Dp = 6.dp
    val defaultTabHorizontalPadding: Dp = 20.dp

    /** Selected tab glyph: scale, on its own spring. */
    val selectedTabIconScale: Float = 1.08f
    val tabIconSpringDampingRatio: Float = 0.72f
    val tabIconSpringStiffness: Float = 320f

    // Selection pill.

    /** How much taller than the bar the pill stands while it is lifted. */
    val selectionPillLiftHeight: Dp = 9.dp

    /** Plain lifted pill (no Liquid Glass): contact shadow and rim. */
    val liftedPillShadowElevation: Dp = 10.dp
    val liftedPillRimWidth: Dp = 1.dp

    /** Lens of the glass pill and its contact shadow. */
    val selectionLensHeight: Dp = 12.dp
    val selectionLensAmount: Dp = 14.dp
    val selectionPillShadowRadius: Dp = 12.dp
    val selectionPillShadowOffsetY: Dp = 2.dp

    // Look picker preview.

    val lookPreviewSize: Dp = 64.dp
    val lookPreviewBarHeight: Dp = 16.dp
    val lookPreviewBarPadding: Dp = 3.dp
    val lookPreviewDotSize: Dp = 10.dp

    // Glass Today.

    val todayBottomPadding: Dp = 140.dp
    val todaySectionGap: Dp = 28.dp
    val todayHeaderGap: Dp = 6.dp
    val todayHeroGap: Dp = 18.dp
    val todayLibraryGap: Dp = 14.dp
    val todayLibraryRowPadding: Dp = 18.dp

    /** Dumbbell illustration on the hero card, and the glow behind it (fractions of the canvas). */
    val todayIllustrationHeight: Dp = 112.dp
    val todayGlowCenterX: Float = 0.78f
    val todayGlowCenterY: Float = 0.52f

    // Page background glows (fractions of the page).

    val backgroundGlowRadius: Float = 0.70f
    val backgroundPrimaryGlowCenterX: Float = 1.05f
    val backgroundPrimaryGlowCenterY: Float = 0.25f
    val backgroundSecondaryGlowCenterX: Float = -0.15f
    val backgroundSecondaryGlowCenterY: Float = 0.85f

    /** Second glow's radius relative to [backgroundGlowRadius]. */
    val backgroundSecondaryGlowScale: Float = 0.8f

    internal companion object {
        /** For draw helpers called outside composition; same values as `FitLogTheme.dimens.glass`. */
        val Default = GlassDimens()
    }
}
