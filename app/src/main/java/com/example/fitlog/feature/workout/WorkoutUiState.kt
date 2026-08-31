package com.example.fitlog.feature.workout

import com.example.fitlog.model.Workout

/**
 * 训练日志历史列表的 UI 状态（进行中会话行由 ViewModel 过滤，由会话视图呈现）。
 *
 * 刻意只有 Loading/Success 两态：数据流异常按全项目 guard 约定降级为
 * 空列表 + 一次性提示（[WorkoutViewModel.message] 通道），不在流末端
 * 发射错误态终结链路（同 TodayViewModel/StatsViewModel 的注释声明）。
 */
sealed interface WorkoutUiState {
    data object Loading : WorkoutUiState

    data class Success(val workouts: List<Workout>) : WorkoutUiState
}
