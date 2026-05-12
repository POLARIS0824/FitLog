package com.example.fitlog.feature.workout

import com.example.fitlog.model.Workout

sealed interface WorkoutUiState {
    data object Loading : WorkoutUiState
    data class Success(val workouts: List<Workout>) : WorkoutUiState
    data class Error(val message: String) : WorkoutUiState
}