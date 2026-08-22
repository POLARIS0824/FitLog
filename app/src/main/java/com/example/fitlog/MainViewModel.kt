package com.example.fitlog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fitlog.data.repository.ThemeMode
import com.example.fitlog.data.repository.UserPreferencesRepository
import com.example.fitlog.data.seed.SeedOrchestrator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 主 Activity 的 ViewModel。
 *
 * 暴露主题偏好（主题模式 + 动态取色开关），驱动 [com.example.fitlog.ui.theme.FitLogTheme]
 * 在运行时响应"外观"设置页的修改。
 *
 * 启动时经 [SeedOrchestrator] 触发种子数据导入（先动作库、后预置计划）。
 *
 * ## 首帧放行（[isReady]）
 *
 * Splash **只等外观偏好加载**（DataStore 一读即放，消除主题翻转，耗时极短）；
 * 种子耗时不再阻塞 Splash——首装/升级时由 Today 首屏加载条承接
 * （TodayViewModel 等 [SeedOrchestrator.completed] 才首发 uiState）。
 *
 * [isReady] 与 [appearance] 必须使用 [SharingStarted.Eagerly]：
 * `setKeepOnScreenCondition` 只读 [StateFlow.value]，不构成订阅——
 * 若为 WhileSubscribed 且无人收集，上游永不启动，应用永久卡死在 Splash。
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    userPreferencesRepository: UserPreferencesRepository,
    private val seedOrchestrator: SeedOrchestrator,
) : ViewModel() {

    /** 外观真实值首发标记：仅 DataStore 发射后置位（stateIn initialValue 不经过上游）。 */
    private val appearanceLoaded = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            seedOrchestrator.seedIfNeeded()
        }
    }

    /** （主题模式, 动态取色开关） */
    val appearance: StateFlow<Pair<ThemeMode, Boolean>> = combine(
        userPreferencesRepository.themeMode,
        userPreferencesRepository.dynamicColor,
        ::Pair,
    )
        // DataStore 读取异常兜底：吞掉异常并放行默认外观。
        // 不加 catch 时异常会击穿 stateIn(Eagerly) 的共享协程成为未捕获异常，启动即崩溃。
        .catch { }
        .onEach { appearanceLoaded.value = true }
        // 双保险：即使上游异常完成，也放行 Splash（onCompletion 对异常完成同样回调）
        .onCompletion { appearanceLoaded.value = true }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = ThemeMode.SYSTEM to true,
        )

    /** 首帧放行条件：外观偏好已加载（种子不阻塞 Splash，见 [SeedOrchestrator]）。 */
    val isReady: StateFlow<Boolean> = appearanceLoaded
}
