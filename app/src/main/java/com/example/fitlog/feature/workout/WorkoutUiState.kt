package com.example.fitlog.feature.workout

import com.example.fitlog.model.Workout

/**
 * 训练日志界面的 UI 状态。
 * 使用 sealed interface 确保状态互斥与强类型安全。
 */
sealed interface WorkoutUiState {
    data object Loading : WorkoutUiState

    data class Success(val workouts: List<Workout>) : WorkoutUiState

    data class Error(val message: String) : WorkoutUiState
}