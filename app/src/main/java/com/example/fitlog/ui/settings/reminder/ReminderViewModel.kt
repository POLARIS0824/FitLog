package com.example.fitlog.ui.settings.reminder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fitlog.data.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 训练提醒页 ViewModel。
 *
 * 当前仅持久化偏好（开关 + 时间）到 DataStore；
 * 实际调度逻辑待 WorkManager 接入（TODO）。
 */
@HiltViewModel
class ReminderViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    val uiState: StateFlow<ReminderUiState> = combine(
        userPreferencesRepository.reminderEnabled,
        userPreferencesRepository.reminderMinutes,
        ::ReminderUiState,
    ).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ReminderUiState(),
    )

    /** 提醒开关变化。 */
    fun onEnabledChange(enabled: Boolean) {
        viewModelScope.launch { userPreferencesRepository.setReminderEnabled(enabled) }
    }

    /** 提醒时间变化（一天中的分钟数）。 */
    fun onTimeChange(minutes: Int) {
        viewModelScope.launch { userPreferencesRepository.setReminderMinutes(minutes) }
    }
}
