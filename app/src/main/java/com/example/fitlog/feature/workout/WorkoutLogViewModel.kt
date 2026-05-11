package com.example.fitlog.feature.workout

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fitlog.domain.model.Exercise
import com.example.fitlog.domain.model.ExerciseLog
import com.example.fitlog.domain.model.SetLog
import com.example.fitlog.domain.model.WorkOut
import com.example.fitlog.domain.usecase.workout.GetExercisesUseCase
import com.example.fitlog.domain.usecase.workout.GetWorkoutByDateUseCase
import com.example.fitlog.domain.usecase.workout.SaveWorkoutUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * 训练日志编辑界面的 ViewModel。
 *
 * 管理 [WorkoutLogUiState]，协调 UseCase 完成训练记录的加载、编辑与保存。
 * 支持新建模式（无日期参数）和编辑模式（指定日期加载已有记录）。
 */
@RequiresApi(Build.VERSION_CODES.O)
@HiltViewModel
class WorkoutLogViewModel @Inject constructor(
    private val getExercisesUseCase: GetExercisesUseCase,
    private val getWorkoutByDateUseCase: GetWorkoutByDateUseCase,
    private val saveWorkoutUseCase: SaveWorkoutUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WorkoutLogUiState())

    /**
     * 当前界面的 UI 状态流。
     */
    val uiState: StateFlow<WorkoutLogUiState> = _uiState

    private var existingWorkoutId: Long = 0L
    private var existingUserId: Long = 0L

    /**
     * 初始化 ViewModel，加载动作库缓存，并可选地加载已有训练记录。
     *
     * @param date 编辑模式下的目标日期，null 表示新建模式
     */
    fun init(date: LocalDate?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val exercises = getExercisesUseCase()
                _uiState.update { it.copy(availableExercises = exercises) }

                date?.let {
                    val existing = getWorkoutByDateUseCase(it)
                    existing?.let { workout ->
                        existingWorkoutId = workout.id
                        existingUserId = workout.userId
                        _uiState.update { state ->
                            state.copy(
                                date = workout.date,
                                feelings = workout.feelings ?: "",
                                exercises = workout.exercises.map { entry ->
                                    ExerciseUiModel(
                                        exerciseKey = entry.exerciseKey,
                                        name = entry.name,
                                        sets = entry.sets.mapIndexed { index, set ->
                                            SetUiModel(
                                                setNumber = index + 1,
                                                weightKg = set.weightKg,
                                                reps = set.reps,
                                            )
                                        },
                                    )
                                },
                            )
                        }
                    } ?: _uiState.update { it.copy(date = date) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message ?: "加载失败") }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    /**
     * 更新训练日期。
     *
     * @param date 新日期
     */
    fun updateDate(date: LocalDate) {
        _uiState.update { it.copy(date = date) }
    }

    /**
     * 添加一个动作到当前训练。
     *
     * @param exercise 从动作库选择的动作
     */
    fun addExercise(exercise: Exercise) {
        _uiState.update { state ->
            val newExercise = ExerciseUiModel(
                exerciseKey = exercise.id,
                name = exercise.name,
                sets = listOf(SetUiModel(setNumber = 1)),
            )
            state.copy(exercises = state.exercises + newExercise)
        }
    }

    /**
     * 移除指定位置的动作。
     *
     * @param index 动作在列表中的索引
     */
    fun removeExercise(index: Int) {
        _uiState.update { state ->
            val updated = state.exercises.toMutableList().apply { removeAt(index) }
            state.copy(exercises = updated)
        }
    }

    /**
     * 为指定动作添加一组。
     *
     * @param exerciseIndex 动作在列表中的索引
     */
    fun addSet(exerciseIndex: Int) {
        _uiState.update { state ->
            val exercises = state.exercises.toMutableList()
            val exercise = exercises[exerciseIndex]
            val newSet = SetUiModel(setNumber = exercise.sets.size + 1)
            exercises[exerciseIndex] = exercise.copy(sets = exercise.sets + newSet)
            state.copy(exercises = exercises)
        }
    }

    /**
     * 移除指定动作的指定组。
     *
     * @param exerciseIndex 动作在列表中的索引
     * @param setIndex 组在动作中的索引
     */
    fun removeSet(exerciseIndex: Int, setIndex: Int) {
        _uiState.update { state ->
            val exercises = state.exercises.toMutableList()
            val exercise = exercises[exerciseIndex]
            val updatedSets = exercise.sets.toMutableList().apply { removeAt(setIndex) }
                .mapIndexed { index, set -> set.copy(setNumber = index + 1) }
            exercises[exerciseIndex] = exercise.copy(sets = updatedSets)
            state.copy(exercises = exercises)
        }
    }

    /**
     * 更新指定组的重量和次数。
     *
     * @param exerciseIndex 动作在列表中的索引
     * @param setIndex 组在动作中的索引
     * @param weightKg 新重量（kg），null 表示清空
     * @param reps 新次数，null 表示清空
     */
    fun updateSet(exerciseIndex: Int, setIndex: Int, weightKg: Float?, reps: Int?) {
        _uiState.update { state ->
            val exercises = state.exercises.toMutableList()
            val exercise = exercises[exerciseIndex]
            val sets = exercise.sets.toMutableList()
            sets[setIndex] = sets[setIndex].copy(weightKg = weightKg, reps = reps)
            exercises[exerciseIndex] = exercise.copy(sets = sets)
            state.copy(exercises = exercises)
        }
    }

    /**
     * 更新训练感受/备注。
     *
     * @param feelings 新的感受文本
     */
    fun updateFeelings(feelings: String) {
        _uiState.update { it.copy(feelings = feelings) }
    }

    /**
     * 清除错误信息。
     */
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    /**
     * 保存当前训练记录。
     *
     * 将 UI 状态组装为 [WorkOut] 并调用 [SaveWorkoutUseCase]。
     */
    fun save() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, saveSuccess = false, errorMessage = null) }
            try {
                val state = _uiState.value
                val workOut = WorkOut(
                    id = existingWorkoutId,
                    userId = existingUserId,
                    date = state.date,
                    feelings = state.feelings.ifBlank { null },
                    exercises = state.exercises.map { entry ->
                        ExerciseLog(
                            name = entry.name,
                            exerciseKey = entry.exerciseKey,
                            sets = entry.sets.map { set ->
                                SetLog(
                                    weightKg = set.weightKg ?: 0f,
                                    reps = set.reps ?: 0,
                                )
                            },
                        )
                    },
                    sourceFileName = null,
                )
                saveWorkoutUseCase(workOut)
                _uiState.update { it.copy(isSaving = false, saveSuccess = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, errorMessage = e.message ?: "保存失败") }
            }
        }
    }
}
