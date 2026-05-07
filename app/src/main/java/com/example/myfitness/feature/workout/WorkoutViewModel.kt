package com.example.myfitness.feature.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myfitness.domain.model.DailyCheckIn
import com.example.myfitness.domain.model.ExerciseEntry
import com.example.myfitness.domain.model.WorkoutSet
import com.example.myfitness.domain.repository.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * 训练记录页面的 ViewModel。
 */
@HiltViewModel
class WorkoutViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository,
) : ViewModel() {

    private val _todayCheckIn = MutableStateFlow<DailyCheckIn?>(null)
    val todayCheckIn: StateFlow<DailyCheckIn?> = _todayCheckIn

    init {
        loadToday()
    }

    /**
     * 加载今天的训练记录。
     */
    private fun loadToday() {
        viewModelScope.launch {
            _todayCheckIn.value = workoutRepository.getSessionByDate(LocalDate.now())
        }
    }

    /**
     * 为今天的记录添加一组训练。
     *
     * @param exerciseName 动作名称
     * @param weightKg 重量（kg）
     * @param reps 次数
     */
    fun addSet(exerciseName: String, weightKg: Float, reps: Int) {
        viewModelScope.launch {
            val current = _todayCheckIn.value
            val updated = if (current == null) {
                DailyCheckIn(
                    id = 0L,
                    date = LocalDate.now(),
                    exercises = listOf(
                        ExerciseEntry(
                            name = exerciseName,
                            sets = listOf(WorkoutSet(weightKg, reps)),
                        ),
                    ),
                )
            } else {
                val existingExercises = current.exercises.toMutableList()
                val index = existingExercises.indexOfFirst { it.name == exerciseName }
                if (index >= 0) {
                    val oldEntry = existingExercises[index]
                    existingExercises[index] = oldEntry.copy(
                        sets = oldEntry.sets + WorkoutSet(weightKg, reps),
                    )
                } else {
                    existingExercises.add(
                        ExerciseEntry(
                            name = exerciseName,
                            sets = listOf(WorkoutSet(weightKg, reps)),
                        ),
                    )
                }
                current.copy(exercises = existingExercises)
            }

            workoutRepository.saveSession(updated)
            _todayCheckIn.value = updated
        }
    }
}
