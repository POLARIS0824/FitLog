package com.example.fitlog.feature.workout

import androidx.lifecycle.ViewModel
import com.example.fitlog.data.repository.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class WorkoutViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository
): ViewModel() {

    private val _uiState = MutableStateFlow(WorkoutUiState())
    val uiState: StateFlow<WorkoutUiState> = _uiState.asStateFlow()

    init {
        // 页面打开自动加载全部记录
        // TODO: 优化逻辑
        observeWorkouts()
    }

    private fun observeWorkouts() {
        workoutRepository.getWorkouts()
    }
}