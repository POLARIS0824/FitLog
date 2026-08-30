package com.example.fitlog.feature.workout

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fitlog.data.repository.WorkoutRepository
import com.example.fitlog.model.Workout
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 训练记录列表页的 ViewModel。
 *
 * 列表经 stateIn 直接订阅 Room Flow（库变更自动刷新）；
 * 增删属一次性事件，失败记录日志（页面无错误通道，列表以 Room 为唯一事实源）。
 */
@HiltViewModel
class WorkoutViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository,
) : ViewModel() {

    /** 页面 UI 状态流：Room 变更驱动，加载/错误/数据三态。 */
    val uiState: StateFlow<WorkoutUiState> = workoutRepository
        .getWorkouts()
        .map<List<Workout>, WorkoutUiState> { workouts ->
            WorkoutUiState.Success(workouts)
        }
        .catch { e ->
            emit(WorkoutUiState.Error(e.message ?: "Unknown"))
        }
        .stateIn(
            scope = viewModelScope,
            // 前台订阅期间 Room 变更即刷新；无人订阅 5s 后停止监听（配置变更最佳实践）
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = WorkoutUiState.Loading,
        )

    /** 删除训练记录（失败仅记录日志，列表由 Room Flow 驱动，无需手动刷新）。 */
    fun deleteWorkout(workout: Workout) {
        viewModelScope.launch {
            try {
                workoutRepository.delete(workout)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "删除训练记录失败：${workout.date}", e)
            }
        }
    }

    private companion object {
        const val TAG = "WorkoutViewModel"
    }
}
