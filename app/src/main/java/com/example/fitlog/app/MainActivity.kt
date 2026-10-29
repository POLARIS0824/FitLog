package com.example.fitlog.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.fitlog.data.settings.ThemeMode
import com.example.fitlog.data.settings.UiLook
import com.example.fitlog.ui.glass.LocalUiLook
import com.example.fitlog.ui.glass.LocalReduceMotion
import com.example.fitlog.ui.glass.rememberReduceMotion
import com.example.fitlog.feature.settings.AppearanceViewModel
import com.example.fitlog.ui.theme.FitLogTheme

/**
 * 负责进入 Compose
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as FitLogApplication).container
        setContent {
            val appearance = viewModel<AppearanceViewModel> {
                container.appearance()
            }
            val appearanceState by appearance.uiState.collectAsStateWithLifecycle()
            val liquidLook = appearanceState.preferences.look == UiLook.LIQUID_GLASS
            val dark = liquidLook || when (appearanceState.preferences.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            CompositionLocalProvider(
                LocalUiLook provides appearanceState.preferences.look,
                LocalReduceMotion provides rememberReduceMotion(),
            ) {
            FitLogTheme(darkTheme = dark, dynamicColor = appearanceState.preferences.dynamicColor, liquidLook = liquidLook) {
                val barColor = MaterialTheme.colorScheme.surface.toArgb()
                SideEffect {
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                        navigationBarStyle = SystemBarStyle.auto(barColor, barColor) { dark },
                    )
                }
                FitLogApp(container, appearance, appearanceState)
            }
            }
        }
    }
}
