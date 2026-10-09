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

internal data class AppearancePreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
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
        )
    }

    override suspend fun save(settings: AppearancePreferences) {
        dataStore.edit {
            it[THEME_MODE] = settings.themeMode.name
            it[DYNAMIC_COLOR] = settings.dynamicColor
        }
    }

    private companion object {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
    }
}
