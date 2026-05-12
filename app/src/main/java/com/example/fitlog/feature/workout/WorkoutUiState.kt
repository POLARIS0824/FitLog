package com.example.fitlog.feature.workout

import com.example.fitlog.model.Workout

data class WorkoutUiState(
    val workoutLists: List<Workout> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)