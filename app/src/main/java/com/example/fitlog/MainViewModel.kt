package com.example.fitlog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fitlog.data.repository.ThemeMode
import com.example.fitlog.data.repository.UserPreferencesRepository
import com.example.fitlog.data.seed.ExerciseSeeder
import com.example.fitlog.data.seed.WorkoutPlanSeeder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
 * 首次启动时触发种子数据导入：先动作库、后预置计划
 * （计划动作引用动作库 key，WorkoutPlanSeeder 写入前需校验存在性）。
 *
 * ## 首帧放行（[isReady]）
 *
 * Splash 保持到"外观偏好已加载 + 种子已完成"才放行，消除两类启动跳变：
 * 外观 initialValue 与存储值不同导致的全屏换主题；种子在 UI 收集期间写 Room
 * 导致的内容中途翻转。
 *
 * [isReady] 与 [appearance] 必须使用 [SharingStarted.Eagerly]：
 * `setKeepOnScreenCondition` 只读 [StateFlow.value]，不构成订阅——
 * 若为 WhileSubscribed 且无人收集，上游永不启动，应用永久卡死在 Splash。
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    userPreferencesRepository: UserPreferencesRepository,
    private val exerciseSeeder: ExerciseSeeder,
    private val workoutPlanSeeder: WorkoutPlanSeeder,
) : ViewModel() {

    /** 种子导入完成标记（成功/失败都放行，fail-open 防卡死启动）。 */
    private val seedCompleted = MutableStateFlow(false)

    /** 外观真实值首发标记：仅 DataStore 发射后置位（stateIn initialValue 不经过上游）。 */
    private val appearanceLoaded = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            try {
                exerciseSeeder.seedIfNeeded()
                workoutPlanSeeder.seedIfNeeded()
            } finally {
                // 种子异常也必须放行：宁可内容中途变，不能永久卡 Splash
                seedCompleted.value = true
            }
        }
    }

    /** （主题模式, 动态取色开关） */
    val appearance: StateFlow<Pair<ThemeMode, Boolean>> = combine(
        userPreferencesRepository.themeMode,
        userPreferencesRepository.dynamicColor,
        ::Pair,
    )
        .onEach { appearanceLoaded.value = true }
        // DataStore 读取异常兜底：放行默认外观，不卡启动
        .onCompletion { appearanceLoaded.value = true }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = ThemeMode.SYSTEM to true,
        )

    /** 首帧放行条件：外观已加载 AND 种子已完成。 */
    val isReady: StateFlow<Boolean> = combine(
        appearanceLoaded, seedCompleted,
    ) { loaded, seeded -> loaded && seeded }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = false,
        )
}
