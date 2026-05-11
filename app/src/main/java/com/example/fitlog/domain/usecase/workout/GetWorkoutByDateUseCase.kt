package com.example.fitlog.domain.usecase.workout

import com.example.fitlog.domain.model.WorkOut
import com.example.fitlog.domain.repository.WorkoutRepository
import java.time.LocalDate
import javax.inject.Inject

/**
 * 根据日期获取已有训练记录。
 *
 * 用于编辑模式下加载指定日期的训练数据，若该日期无记录则返回 null。
 */
class GetWorkoutByDateUseCase @Inject constructor(
    private val workoutRepository: WorkoutRepository,
) {

    /**
     * 执行用例。
     *
     * @param date 查询日期
     * @return 匹配的训练记录，若不存在则返回 null
     */
    suspend operator fun invoke(date: LocalDate): WorkOut? {
        return workoutRepository.getSessionByDate(date)
    }
}
