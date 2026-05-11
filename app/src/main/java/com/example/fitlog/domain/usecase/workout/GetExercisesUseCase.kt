package com.example.fitlog.domain.usecase.workout

import com.example.fitlog.domain.model.Exercise
import com.example.fitlog.domain.repository.ExerciseRepository
import javax.inject.Inject

/**
 * 获取动作库全部动作。
 *
 * 用于训练日志编辑界面的动作选择器，提供搜索/浏览的数据源。
 */
class GetExercisesUseCase @Inject constructor(
    private val exerciseRepository: ExerciseRepository,
) {

    /**
     * 执行用例，返回全部动作列表。
     *
     * @return [Exercise] 列表，按名称升序
     */
    suspend operator fun invoke(): List<Exercise> {
        return exerciseRepository.getAll()
    }
}
