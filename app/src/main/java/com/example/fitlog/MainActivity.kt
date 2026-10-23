package com.example.fitlog

import android.os.Bundle
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.SideEffect
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.fitlog.data.settings.AppearancePreferencesStore
import com.example.fitlog.data.settings.ThemeMode
import com.example.fitlog.data.settings.appearanceDataStore
import com.example.fitlog.settings.AppearanceViewModel
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
            val dark = when (appearanceState.preferences.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            FitLogTheme(darkTheme = dark, dynamicColor = appearanceState.preferences.dynamicColor) {
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
