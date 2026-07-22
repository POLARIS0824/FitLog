package com.example.fitlog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fitlog.data.repository.ThemeMode
import com.example.fitlog.data.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * 主 Activity 的 ViewModel。
 *
 * 暴露主题偏好（主题模式 + 动态取色开关），驱动 [com.example.fitlog.ui.theme.FitLogTheme]
 * 在运行时响应"外观"设置页的修改。
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    userPreferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    /** （主题模式, 动态取色开关） */
    val appearance: StateFlow<Pair<ThemeMode, Boolean>> = combine(
        userPreferencesRepository.themeMode,
        userPreferencesRepository.dynamicColor,
        ::Pair,
    ).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ThemeMode.SYSTEM to true,
    )
}
