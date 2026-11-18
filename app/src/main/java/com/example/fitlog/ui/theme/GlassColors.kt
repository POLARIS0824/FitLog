package com.example.fitlog.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/** Liquid Glass palette and tints. The glass look replaces the Material scheme with these. */
internal object GlassColors {
    val Page = Color(0xFF0A0B12)
    val Fill = Color(0xFF242633).copy(alpha = 0.90f)
    val Accent = Color(0xFFBBC2FF)
    val DarkScheme = darkColorScheme(
        primary = Accent, onPrimary = Color(0xFF202448),
        primaryContainer = Color(0xFF303450), onPrimaryContainer = Color(0xFFE6E8FF),
        secondary = Color(0xFFB9C5DC), secondaryContainer = Color(0xFF333C51),
        onSecondaryContainer = Color(0xFFE0E8FB), tertiary = Color(0xFFBCDBC9),
        surface = Page, onSurface = Color(0xFFF5F5FC), onSurfaceVariant = Color(0xFFB2B4C4),
        surfaceContainerLowest = Color(0xFF141620), surfaceContainerLow = Color(0xFF191C29),
        surfaceContainer = Color(0xFF212432), surfaceContainerHigh = Color(0xFF2B2F40),
        outline = Color(0xFF85899E), outlineVariant = Color(0xFF3B3F53),
    )

    val LightPage = Color(0xFFF7F8FD)
    val LightFill = Color(0xFFFFFFFF).copy(alpha = 0.88f)
    val LightAccent = Color(0xFF4C59A0)
    val LightScheme = lightColorScheme(
        primary = LightAccent, onPrimary = Color.White,
        primaryContainer = Color(0xFFE0E3FF), onPrimaryContainer = Color(0xFF041450),
        secondary = Color(0xFF5A6071), secondaryContainer = Color(0xFFDFE2F9),
        onSecondaryContainer = Color(0xFF171D2B), tertiary = Color(0xFF476456),
        surface = LightPage, onSurface = Color(0xFF1A1C22), onSurfaceVariant = Color(0xFF45464F),
        surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF1F3FA),
        surfaceContainer = Color(0xFFEBEFF6), surfaceContainerHigh = Color(0xFFE3E8F0),
        outline = Color(0xFF757680), outlineVariant = Color(0xFFC5C6D0),
    )

    /** The glass reads its light or dark variant from the surface it sits on. */
    fun isDark(surface: Color): Boolean = surface.luminance() < 0.5f

    fun page(dark: Boolean): Color = if (dark) Page else LightPage
    fun fill(dark: Boolean): Color = if (dark) Fill else LightFill

    /** The glass's own body over the refracted page: dark, so white text holds on any artwork. */
    private val GlassBody = Color(0xFF1C1C22)
    fun glassTint(dark: Boolean): Color =
        if (dark) GlassBody.copy(alpha = 0.42f) else Color.White.copy(alpha = 0.55f)
    fun navTint(dark: Boolean, fullLook: Boolean): Color = when {
        fullLook -> glassTint(dark)
        dark -> GlassBody.copy(alpha = 0.82f)
        else -> Color.White.copy(alpha = 0.85f)
    }

    /** Alpha the adaptive tint reaches on large glass: more opaque, there is text on it. */
    const val LargeTintAlpha = 0.60f

    /** Specular rim strength and the soft shadow of a Liquid Glass surface. */
    fun rimAlpha(dark: Boolean): Float = if (dark) 0.9f else 0.65f
    fun surfaceShadow(dark: Boolean): Color =
        Color.Black.copy(alpha = if (dark) 0.22f else 0.08f)

    /** Frost fallback: the fill laid over the blurred page, and its hairline border. */
    fun frostTint(dark: Boolean): Color = fill(dark).copy(alpha = if (dark) 0.75f else 0.85f)
    fun frostBorder(dark: Boolean): Color =
        if (dark) Color.White.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.08f)

    /** Tint of the accent glass button. */
    fun accentButtonTint(dark: Boolean, primary: Color): Color =
        primary.copy(alpha = if (dark) 0.18f else 0.12f)

    /** Checked segment fill and its label. */
    fun segmentSelected(dark: Boolean, primaryContainer: Color): Color =
        if (dark) Color.White.copy(alpha = 0.15f) else primaryContainer
    fun onSegmentSelected(dark: Boolean, onPrimaryContainer: Color): Color =
        if (dark) Color.White else onPrimaryContainer

    /** Resting selection pill under the tabs. */
    fun tabIndicator(dark: Boolean, primary: Color): Color =
        if (dark) Color.White.copy(alpha = 0.12f) else primary.copy(alpha = 0.12f)

    /** Plain lifted pill: its raised body and its rim (color and peak alpha). */
    fun liftedPillBody(dark: Boolean): Color = Color.White.copy(alpha = if (dark) 0.20f else 0.70f)
    fun liftedPillRim(dark: Boolean, outlineVariant: Color): Color = if (dark) Color.White else outlineVariant
    fun liftedPillRimAlpha(dark: Boolean): Float = if (dark) 0.38f else 0.45f

    /** Glass selection pill: peak rim and shadow strength, and the shadow color. */
    fun selectionPillHighlightAlpha(dark: Boolean): Float = if (dark) 1f else 0.7f
    fun selectionPillShadowAlpha(dark: Boolean): Float = if (dark) 1f else 0.4f
    val SelectionPillShadow = Color.Black.copy(alpha = 0.3f)

    /** Page background: base and two soft glows. */
    private val LightGlowIndigo = Color(0xFFD6DBF8)
    private val LightGlowViolet = Color(0xFFE8DCFA)
    fun backgroundPrimaryGlow(dark: Boolean): Color =
        (if (dark) Color(0xFF394272) else LightGlowIndigo).copy(alpha = 0.65f)
    fun backgroundSecondaryGlow(dark: Boolean): Color =
        (if (dark) Color(0xFF40345E) else LightGlowViolet).copy(alpha = 0.55f)

    /** Look picker thumbnail: the full-look gradient and its miniature bar and dots. */
    fun lookPreviewGradient(dark: Boolean): List<Color> =
        if (dark) listOf(Color(0xFF242C4D), Color(0xFF60547A)) else listOf(LightGlowIndigo, LightGlowViolet)
    fun lookPreviewBar(dark: Boolean): Color = Color.White.copy(alpha = if (dark) 0.20f else 0.65f)
    fun lookPreviewDot(dark: Boolean, primary: Color): Color =
        if (dark) Color.White.copy(alpha = 0.60f) else primary

    /** Glow behind the Glass Today illustration. */
    fun todayHeroGlow(primary: Color): Color = primary.copy(alpha = 0.26f)
}
