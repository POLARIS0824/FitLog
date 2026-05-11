package com.example.fitlog.data.repository

import com.example.fitlog.data.local.dao.ExerciseDao
import com.example.fitlog.data.local.entity.ExerciseEntity
import com.example.fitlog.domain.model.Exercise
import com.example.fitlog.domain.repository.ExerciseRepository
import javax.inject.Inject

/**
 * [ExerciseRepository] 的 Room 实现。
 *
 * 通过 [ExerciseDao] 查询动作库，并将 [ExerciseEntity] 转换为 domain/model [Exercise]。
 */
class ExerciseRepositoryImpl @Inject constructor(
    private val exerciseDao: ExerciseDao,
) : ExerciseRepository {

    /**
     * 获取所有动作记录，按名称升序排列。
     *
     * @return [Exercise] 列表
     */
    override suspend fun getAll(): List<Exercise> {
        return exerciseDao.getAll().map { it.toDomain() }
    }

    /**
     * 将 [ExerciseEntity] 转换为 domain/model [Exercise]。
     */
    private fun ExerciseEntity.toDomain(): Exercise {
        return Exercise(
            id = id,
            name = name,
            primaryMuscle = primaryMuscle,
            secondaryMuscles = secondaryMuscles,
            movementPattern = movementPattern,
            force = force,
            difficulty = difficulty,
            isCompound = isCompound,
            isCustom = isCustom,
            equipment = equipment,
            category = category,
            description = description,
            instructions = instructions,
        )
    }
}
