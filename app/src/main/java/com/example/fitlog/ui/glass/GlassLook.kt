package com.example.fitlog.ui.glass

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
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
import com.example.fitlog.ui.theme.FitLogTheme
import com.example.fitlog.ui.theme.GlassColors
import com.example.fitlog.ui.theme.GlassDimens
import com.example.fitlog.ui.glass.backdrop.backdrops.layerBackdrop

internal val LocalUiLook = staticCompositionLocalOf { UiLook.CLASSIC }
internal val LocalReduceMotion = compositionLocalOf<Boolean?> { null }
internal val LocalGlassScene = compositionLocalOf<LayerBackdrop?> { null }
internal val LocalGlassPage = compositionLocalOf<LayerBackdrop?> { null }

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

internal fun DrawScope.drawGlassBackground(dark: Boolean = true, glass: GlassDimens = GlassDimens.Default) {
    drawRect(GlassColors.page(dark))
    val radius = size.maxDimension * glass.backgroundGlowRadius
    drawRect(Brush.radialGradient(listOf(GlassColors.backgroundPrimaryGlow(dark), Color.Transparent),
        center = Offset(size.width * glass.backgroundPrimaryGlowCenterX, size.height * glass.backgroundPrimaryGlowCenterY),
        radius = radius))
    drawRect(Brush.radialGradient(listOf(GlassColors.backgroundSecondaryGlow(dark), Color.Transparent),
        center = Offset(size.width * glass.backgroundSecondaryGlowCenterX, size.height * glass.backgroundSecondaryGlowCenterY),
        radius = radius * glass.backgroundSecondaryGlowScale))
}

@Composable
internal fun GlassBackdropBackground(modifier: Modifier = Modifier) {
    val dark = GlassColors.isDark(MaterialTheme.colorScheme.surface)
    val glass = FitLogTheme.dimens.glass
    Canvas(modifier) { drawGlassBackground(dark, glass) }
}

/** Record each scrolling page, never its floating chrome or a parent containing the glass. */
@Composable
internal fun Modifier.glassPageSource(): Modifier {
    val page = LocalGlassPage.current
    if (page == null) return this
    val fullLook = LocalUiLook.current == UiLook.LIQUID_GLASS
    val pageColor = MaterialTheme.colorScheme.surface
    val dark = GlassColors.isDark(pageColor)
    val glass = FitLogTheme.dimens.glass
    return layerBackdrop(page).drawBehind {
        if (fullLook) drawGlassBackground(dark, glass) else drawRect(pageColor)
    }
}
