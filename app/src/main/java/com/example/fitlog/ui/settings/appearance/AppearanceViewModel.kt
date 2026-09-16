package com.example.fitlog.ui.settings.appearance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fitlog.data.repository.ThemeMode
import com.example.fitlog.data.repository.UserPreferencesRepository
import com.example.fitlog.util.guard
import com.example.fitlog.util.log.FitLog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
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
 *
 * 数据流异常按全项目 guard 约定降级：偏好 Flow 捕获后发射默认值，
 * 不让 DataStore IO 异常击穿 `stateIn` 的共享协程崩溃（可重设的偏好
 * 静默降级为默认值，与 MainViewModel 外观路径的 `.catch {}` 兜底同策略）。
 */
@HiltViewModel
class AppearanceViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    val uiState: StateFlow<AppearanceUiState> = combine(
        userPreferencesRepository.themeMode.guard(ThemeMode.SYSTEM, context = "主题模式偏好"),
        userPreferencesRepository.dynamicColor.guard(true, context = "动态取色偏好"),
        ::AppearanceUiState,
    ).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AppearanceUiState(),
    )

    /** 主题模式变化（跟随系统 / 浅色 / 深色）。 */
    fun onThemeModeChange(mode: ThemeMode) {
        viewModelScope.launch {
            try {
                userPreferencesRepository.setThemeMode(mode)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                FitLog.w(TAG, "写入主题模式失败", e)
            }
        }
    }

    /** 动态取色开关变化。 */
    fun onDynamicColorChange(enabled: Boolean) {
        viewModelScope.launch {
            try {
                userPreferencesRepository.setDynamicColor(enabled)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                FitLog.w(TAG, "写入动态取色开关失败", e)
            }
        }
    }

    private companion object {
        private const val TAG = "AppearanceViewModel"
    }
}
