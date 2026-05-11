package com.example.fitlog.domain.usecase.workout

import com.example.fitlog.domain.model.WorkOut
import com.example.fitlog.domain.repository.WorkoutRepository
import javax.inject.Inject

/**
 * 保存或更新单次训练记录。
 *
 * 将 [WorkOut] 及其嵌套的 [ExerciseLog] / [SetLog] 级联写入数据库。
 */
class SaveWorkoutUseCase @Inject constructor(
    private val workoutRepository: WorkoutRepository,
) {

    /**
     * 执行用例。
     *
     * @param workOut 待保存的训练记录
     */
    suspend operator fun invoke(workOut: WorkOut) {
        workoutRepository.saveSession(workOut)
    }
}
