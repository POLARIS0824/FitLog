package com.example.fitlog.ui.settings.reminder

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fitlog.data.repository.UserPreferencesRepository
import com.example.fitlog.feature.reminder.ReminderScheduler
import com.example.fitlog.util.guard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 训练提醒页 ViewModel。
 *
 * 偏好（开关 + 时间）持久化到 DataStore；实际调度委托
 * [ReminderScheduler]（WorkManager 一次性任务自链，跨重启持久化）。
 * 开关关闭取消任务；开启/改时间在开关为开时（重）排任务。
 *
 * 数据流异常按全项目 guard 约定降级：偏好 Flow 捕获后发射默认值，
 * 不让 DataStore IO 异常击穿 `stateIn` 的共享协程崩溃（本页无一次性
 * 错误通道，可重设的偏好静默降级为默认值即可，与 MainViewModel 外观
 * 路径的 `.catch {}` 兜底同策略）。
 */
@HiltViewModel
class ReminderViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val reminderScheduler: ReminderScheduler,
) : ViewModel() {

    val uiState: StateFlow<ReminderUiState> = combine(
        userPreferencesRepository.reminderEnabled.guard(false),
        userPreferencesRepository.reminderMinutes.guard(18 * 60),
        ::ReminderUiState,
    ).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ReminderUiState(),
    )

    /** 提醒开关变化：写偏好并同步调度/取消（通知运行时权限由 Screen 层先行申请）。 */
    fun onEnabledChange(enabled: Boolean) {
        viewModelScope.launch {
            try {
                userPreferencesRepository.setReminderEnabled(enabled)
                if (enabled) {
                    reminderScheduler.schedule(userPreferencesRepository.reminderMinutes.first())
                } else {
                    reminderScheduler.cancel()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "更新提醒开关失败", e)
            }
        }
    }

    /** 提醒时间变化（一天中的分钟数）：开关为开时重排到新时刻。 */
    fun onTimeChange(minutes: Int) {
        viewModelScope.launch {
            try {
                userPreferencesRepository.setReminderMinutes(minutes)
                // 调度决策读 DataStore 事实源而非 uiState.value：后者依赖页面订阅
                // （WhileSubscribed），无人订阅时是陈旧初值，会漏掉重排
                if (userPreferencesRepository.reminderEnabled.first()) {
                    reminderScheduler.schedule(minutes)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "重排提醒时间失败", e)
            }
        }
    }

    private companion object {
        private const val TAG = "ReminderViewModel"
    }
}
