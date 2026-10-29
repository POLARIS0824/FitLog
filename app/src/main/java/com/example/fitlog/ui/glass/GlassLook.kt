package com.example.fitlog.ui.glass

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalContext
import com.example.fitlog.data.settings.UiLook
import com.example.fitlog.ui.glass.backdrop.backdrops.LayerBackdrop
import com.example.fitlog.ui.glass.backdrop.backdrops.layerBackdrop

internal val LocalUiLook = staticCompositionLocalOf { UiLook.CLASSIC }
internal val LocalReduceMotion = compositionLocalOf<Boolean?> { null }
internal val LocalGlassScene = compositionLocalOf<LayerBackdrop?> { null }
internal val LocalGlassPage = compositionLocalOf<LayerBackdrop?> { null }

/** Immersive is a separate dark look, just as in Myspoti. Classic owns its existing theme. */
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

private fun DrawScope.drawGlassBackground() {
    drawRect(GlassTokens.Page)
    val radius = size.maxDimension * 0.70f
    drawRect(Brush.radialGradient(listOf(Color(0xFF394272).copy(alpha = 0.65f), Color.Transparent),
        center = Offset(size.width * 1.05f, size.height * 0.25f), radius = radius))
    drawRect(Brush.radialGradient(listOf(Color(0xFF40345E).copy(alpha = 0.55f), Color.Transparent),
        center = Offset(-size.width * 0.15f, size.height * 0.85f), radius = radius * 0.8f))
}

@Composable
internal fun GlassBackdropBackground(modifier: Modifier = Modifier) {
    Canvas(modifier) { drawGlassBackground() }
}

/** Record each scrolling page, never its floating chrome or a parent containing the glass. */
@Composable
internal fun Modifier.glassPageSource(): Modifier {
    val page = LocalGlassPage.current
    return if (LocalUiLook.current == UiLook.LIQUID_GLASS && page != null)
        layerBackdrop(page).drawBehind { drawGlassBackground() } else this
}
