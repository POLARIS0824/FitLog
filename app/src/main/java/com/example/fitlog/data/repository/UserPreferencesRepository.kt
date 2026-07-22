package com.example.fitlog.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * 主题模式。
 */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * 应用级 UI 偏好仓库。
 *
 * 管理主题模式、动态取色、训练提醒等轻量偏好，
 * 与 [AIProviderConfigRepository] 共用同一个 DataStore（`fitLog_prefs`）。
 */
class UserPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {

    private companion object {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
        val REMINDER_MINUTES = intPreferencesKey("reminder_minutes")
    }

    /** 主题模式，默认跟随系统。 */
    val themeMode: Flow<ThemeMode> = dataStore.data.map { prefs ->
        prefs[THEME_MODE]
            ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
            ?: ThemeMode.SYSTEM
    }

    /** 是否启用动态取色（Material You），默认开启。 */
    val dynamicColor: Flow<Boolean> = dataStore.data.map { it[DYNAMIC_COLOR] ?: true }

    /** 训练提醒开关，默认关闭。 */
    val reminderEnabled: Flow<Boolean> = dataStore.data.map { it[REMINDER_ENABLED] ?: false }

    /** 训练提醒时间（一天中的分钟数），默认 18:00。 */
    val reminderMinutes: Flow<Int> = dataStore.data.map { it[REMINDER_MINUTES] ?: 18 * 60 }

    /** 设置主题模式。 */
    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[THEME_MODE] = mode.name }
    }

    /** 设置动态取色开关。 */
    suspend fun setDynamicColor(enabled: Boolean) {
        dataStore.edit { it[DYNAMIC_COLOR] = enabled }
    }

    /** 设置训练提醒开关。 */
    suspend fun setReminderEnabled(enabled: Boolean) {
        dataStore.edit { it[REMINDER_ENABLED] = enabled }
    }

    /** 设置训练提醒时间（一天中的分钟数）。 */
    suspend fun setReminderMinutes(minutes: Int) {
        dataStore.edit { it[REMINDER_MINUTES] = minutes }
    }
}
