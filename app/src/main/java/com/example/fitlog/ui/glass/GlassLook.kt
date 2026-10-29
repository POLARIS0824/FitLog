package com.example.fitlog.ui.glass

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import com.example.fitlog.data.settings.UiLook
import com.example.fitlog.ui.glass.backdrop.backdrops.LayerBackdrop
import com.example.fitlog.ui.glass.backdrop.backdrops.layerBackdrop

internal val LocalUiLook = staticCompositionLocalOf { UiLook.CLASSIC }
internal val LocalReduceMotion = compositionLocalOf<Boolean?> { null }
internal val LocalGlassScene = compositionLocalOf<LayerBackdrop?> { null }
internal val LocalGlassPage = compositionLocalOf<LayerBackdrop?> { null }

/** Tokens for Liquid Glass, adapting cleanly between light and dark themes. */
internal object GlassTokens {
    val Page = Color(0xFF0A0B12)
    val Fill = Color(0xFF242633).copy(alpha = 0.90f)
    val Accent = Color(0xFFBBC2FF)
    val Colors = darkColorScheme(
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
    val LightColors = lightColorScheme(
        primary = LightAccent, onPrimary = Color.White,
        primaryContainer = Color(0xFFE0E3FF), onPrimaryContainer = Color(0xFF041450),
        secondary = Color(0xFF5A6071), secondaryContainer = Color(0xFFDFE2F9),
        onSecondaryContainer = Color(0xFF171D2B), tertiary = Color(0xFF476456),
        surface = LightPage, onSurface = Color(0xFF1A1C22), onSurfaceVariant = Color(0xFF45464F),
        surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF1F3FA),
        surfaceContainer = Color(0xFFEBEFF6), surfaceContainerHigh = Color(0xFFE3E8F0),
        outline = Color(0xFF757680), outlineVariant = Color(0xFFC5C6D0),
    )

    fun page(dark: Boolean): Color = if (dark) Page else LightPage
    fun fill(dark: Boolean): Color = if (dark) Fill else LightFill
    fun glassTint(dark: Boolean): Color =
        if (dark) Color(0xFF1C1C22).copy(alpha = 0.42f) else Color.White.copy(alpha = 0.55f)
    fun navTint(dark: Boolean, fullLook: Boolean): Color = when {
        fullLook -> glassTint(dark)
        dark -> Color(0xFF1C1C22).copy(alpha = 0.82f)
        else -> Color.White.copy(alpha = 0.85f)
    }
}

/** Observe accessibility changes while the app is open; no glass/press motion when disabled. */
@Composable
internal fun rememberReduceMotion(): Boolean {
    LocalReduceMotion.current?.let { return it }
    val resolver = LocalContext.current.contentResolver
    fun read() = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    var reduced by remember(resolver) { mutableStateOf(read()) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { reduced = read() }
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return reduced
}

private fun DrawScope.drawGlassBackground(dark: Boolean = true) {
    val base = if (dark) GlassTokens.Page else GlassTokens.LightPage
    drawRect(base)
    val radius = size.maxDimension * 0.70f
    val c1 = if (dark) Color(0xFF394272).copy(alpha = 0.65f) else Color(0xFFD6DBF8).copy(alpha = 0.65f)
    val c2 = if (dark) Color(0xFF40345E).copy(alpha = 0.55f) else Color(0xFFE8DCFA).copy(alpha = 0.55f)
    drawRect(Brush.radialGradient(listOf(c1, Color.Transparent),
        center = Offset(size.width * 1.05f, size.height * 0.25f), radius = radius))
    drawRect(Brush.radialGradient(listOf(c2, Color.Transparent),
        center = Offset(-size.width * 0.15f, size.height * 0.85f), radius = radius * 0.8f))
}

@Composable
internal fun GlassBackdropBackground(modifier: Modifier = Modifier) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    Canvas(modifier) { drawGlassBackground(dark) }
}

/** Record each scrolling page, never its floating chrome or a parent containing the glass. */
@Composable
internal fun Modifier.glassPageSource(): Modifier {
    val page = LocalGlassPage.current
    if (page == null) return this
    val fullLook = LocalUiLook.current == UiLook.LIQUID_GLASS
    val pageColor = MaterialTheme.colorScheme.surface
    val dark = pageColor.luminance() < 0.5f
    return layerBackdrop(page).drawBehind {
        if (fullLook) drawGlassBackground(dark) else drawRect(pageColor)
    }
}
