package com.example.fitlog.settings

import com.example.fitlog.data.settings.AppearancePreferences

internal data class AppearanceUiState(
    val preferences: AppearancePreferences = AppearancePreferences(),
    val initialized: Boolean = false,
    val loading: Boolean = true,
    val saving: Boolean = false,
    val message: Int? = null,
    val messageId: Long = 0,
) {
    val canEdit get() = initialized && !loading && !saving
}
