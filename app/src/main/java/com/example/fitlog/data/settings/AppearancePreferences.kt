package com.example.fitlog.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** A separate look, independent of the existing Material theme preferences. */
internal enum class UiLook { CLASSIC, LIQUID_GLASS, GLASS_NAVIGATION }

internal data class AppearancePreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val look: UiLook = UiLook.CLASSIC,
    val liquidGlass: Boolean = true,
)

internal interface AppearanceSettingsStore {
    val settings: Flow<AppearancePreferences>
    suspend fun save(settings: AppearancePreferences)
}

internal val Context.appearanceDataStore by preferencesDataStore(name = "appearance_preferences")

/** App-wide appearance is independent of vault identity, diary settings and AI credentials. */
internal class AppearancePreferencesStore(private val dataStore: DataStore<Preferences>) : AppearanceSettingsStore {
    override val settings = dataStore.data.map { values ->
        AppearancePreferences(
            themeMode = values[THEME_MODE]?.let(ThemeMode::valueOf) ?: ThemeMode.SYSTEM,
            dynamicColor = values[DYNAMIC_COLOR] ?: true,
            look = UiLook.entries.firstOrNull { it.name == values[UI_LOOK] } ?: UiLook.CLASSIC,
            liquidGlass = values[LIQUID_GLASS] ?: true,
        )
    }

    override suspend fun save(settings: AppearancePreferences) {
        dataStore.edit {
            it[THEME_MODE] = settings.themeMode.name
            it[DYNAMIC_COLOR] = settings.dynamicColor
            it[UI_LOOK] = settings.look.name
            it[LIQUID_GLASS] = settings.liquidGlass
        }
    }

    private companion object {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val UI_LOOK = stringPreferencesKey("ui_look")
        val LIQUID_GLASS = booleanPreferencesKey("liquid_glass")
    }
}
