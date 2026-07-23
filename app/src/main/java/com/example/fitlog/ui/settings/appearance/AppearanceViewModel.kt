package com.example.fitlog.ui.settings.appearance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fitlog.data.repository.ThemeMode
import com.example.fitlog.data.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 外观设置页 ViewModel。
 *
 * 状态全部来自 [UserPreferencesRepository]（DataStore），
 * 修改即写盘，主题由 MainViewModel 收集后全局生效，无需手动刷新。
 */
@HiltViewModel
class AppearanceViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    val uiState: StateFlow<AppearanceUiState> = combine(
        userPreferencesRepository.themeMode,
        userPreferencesRepository.dynamicColor,
        ::AppearanceUiState,
    ).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AppearanceUiState(),
    )

    /** 主题模式变化（跟随系统 / 浅色 / 深色）。 */
    fun onThemeModeChange(mode: ThemeMode) {
        viewModelScope.launch { userPreferencesRepository.setThemeMode(mode) }
    }

    /** 动态取色开关变化。 */
    fun onDynamicColorChange(enabled: Boolean) {
        viewModelScope.launch { userPreferencesRepository.setDynamicColor(enabled) }
    }
}
