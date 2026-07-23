package com.example.fitlog.data.repository

import com.example.fitlog.data.local.dao.ExerciseDao
import com.example.fitlog.data.mapper.toEntity
import com.example.fitlog.data.mapper.toModel
import com.example.fitlog.model.Exercise
import javax.inject.Inject

/**
 * 动作库仓库。
 *
 * 管理内置动作与用户自定义动作的标准动作列表，
 * 提供增删改查、按身体部位/肌群筛选和名称搜索。
 */
class ExerciseRepository @Inject constructor(
    private val exerciseDao: ExerciseDao
) {
    suspend fun insert(exercise: Exercise) = exerciseDao.insert(exercise.toEntity())

    suspend fun insertAll(exercises: List<Exercise>) =
        exerciseDao.insertAll(exercises.map { it.toEntity() })

    suspend fun update(exercise: Exercise) = exerciseDao.update(exercise.toEntity())

    suspend fun delete(exercise: Exercise) = exerciseDao.delete(exercise.toEntity())

    suspend fun getById(id: String) = exerciseDao.getById(id)?.toModel()

    suspend fun getAll() = exerciseDao.getAll().map { it.toModel() }

    suspend fun getByBodyPart(bodyPart: String) =
        exerciseDao.getByBodyPart(bodyPart).map { it.toModel() }

    suspend fun getByMuscle(muscle: String) =
        exerciseDao.getByMuscle(muscle).map { it.toModel() }

    suspend fun getCustomExercises() = exerciseDao.getCustomExercises().map { it.toModel() }

    suspend fun getByName(name: String) = exerciseDao.getByName(name)?.toModel()

    suspend fun searchByName(name: String) = exerciseDao.searchByName(name).map { it.toModel() }

    suspend fun getCount() = exerciseDao.getCount()
}
