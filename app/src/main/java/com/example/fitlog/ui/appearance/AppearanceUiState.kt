package com.example.fitlog.ui.appearance

import com.example.fitlog.data.repository.ThemeMode

/**
 * 外观设置页的 UI 状态。
 */
data class AppearanceUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
)
