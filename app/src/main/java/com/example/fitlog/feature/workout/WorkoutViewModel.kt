package com.example.fitlog.feature.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fitlog.data.repository.WorkoutRepository
import com.example.fitlog.model.Workout
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import javax.inject.Inject

//data class WorkoutUiState(
//    val workoutLists: List<Workout> = emptyList(),
//    val isLoading: Boolean = false,
//    val errorMessage: String? = null,
//)

@HiltViewModel
class WorkoutViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository
): ViewModel() {

//    private val _uiState = MutableStateFlow(WorkoutUiState())
//    val uiState: StateFlow<WorkoutUiState> = _uiState.asStateFlow()
//
//    init {
//        // 页面打开自动加载全部记录
//        // TODO: 优化逻辑，使用 stateIn
//        observeWorkouts()
//    }
//
//    // 手动订阅、手动更新、手动处理生命周期
//    private fun observeWorkouts() {
//        workoutRepository.getWorkouts()
//            .onStart { _uiState.update { it.copy(isLoading = true) } }
//            .catch { e ->
//                _uiState.update {
//                    it.copy(isLoading = false, errorMessage = e.message)
//                }
//            }
//            .onEach { workouts ->
//                _uiState.update {
//                    it.copy(workoutLists = workouts, isLoading = false)
//                }
//            }
//            .launchIn(viewModelScope)
//    }
//
//    fun loadByDate(date: LocalDate) {
//        workoutRepository.getByDate(date)
//            .onStart { _uiState.update { it.copy(isLoading = true) } }
//            .catch { e ->
//                _uiState.update {
//                    it.copy(isLoading = false, errorMessage = e.message)
//                }
//            }
//            .onEach { workouts ->
//                _uiState.update {
//                    it.copy(workoutLists = workouts, isLoading = false)
//                }
//            }
//            .launchIn(viewModelScope)
//    }
//
//    fun clearError() {
//        _uiState.update { it.copy(errorMessage = null) }
//    }

    // 使用 StateIn 配合 sealed interface
    val uiState: StateFlow<WorkoutUiState> = workoutRepository
        // 上游冷 Flow
        .getWorkouts()
        // 转为 UI 状态
        .map<List<Workout>, WorkoutUiState> { workouts ->
            WorkoutUiState.Success(workouts)
        }
        .catch { e ->
            emit(WorkoutUiState.Error(e.message ?: "Unknown"))
        }
        // 转为热 StateFlow
        .stateIn(
            scope = viewModelScope,

            /**
             * 关键参数 WhileSubscribed(5000) 的含义
             *
             *   started = SharingStarted.WhileSubscribed(5000)
             *
             *   - 有人订阅（Screen 在前台收集）：Room 的 Flow 启动，数据库有变化就刷新。
             *   - 没人订阅（用户按 Home、跳转到别的页面、旋转屏幕重建中）：等 5000ms 后，Room Flow 被取消，不再监听数据库。
             *   - 重新订阅（用户回到页面）：自动重新启动 Room Flow。
             *
             *   这能省内存和电量。如果你用 SharingStarted.Eagerly，会一直监听；如果用 Lazily，第一个订阅者来之后永远不停。WhileSubscribed(5000)
             *   是配置变更场景的最佳实践。
             */
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = WorkoutUiState.Loading,
        )
}