package com.example.fitlog.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalContext

/**
 * FitLog's UI theme. Material owns color, typography, shapes and motion
 * (`MaterialTheme.colorScheme` / `.typography` / `.shapes` / `.motionScheme`);
 * FitLog adds spacing and component dimensions through [FitLogTheme.spacing] and
 * [FitLogTheme.dimens], and number voices through [FitLogType].
 */
@Composable
fun FitLogTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    liquidLook: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        liquidLook -> if (darkTheme) GlassColors.DarkScheme else GlassColors.LightScheme
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> FitLogDarkColors
        else -> FitLogLightColors
    }

    CompositionLocalProvider(
        LocalFitLogSpacing provides DefaultSpacing,
        LocalFitLogDimens provides DefaultDimens,
    ) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            typography = FitLogType.typography,
            shapes = FitLogShapes,
            motionScheme = MotionScheme.expressive(),
            content = content
        )
    }
}

private val DefaultSpacing = FitLogSpacing()
private val DefaultDimens = FitLogDimens()

/** Read FitLog's tokens inside a composable: `FitLogTheme.spacing.l`, `FitLogTheme.dimens.page.contentWidth`. */
object FitLogTheme {
    val spacing: FitLogSpacing
        @Composable @ReadOnlyComposable get() = LocalFitLogSpacing.current

    val dimens: FitLogDimens
        @Composable @ReadOnlyComposable get() = LocalFitLogDimens.current
}
