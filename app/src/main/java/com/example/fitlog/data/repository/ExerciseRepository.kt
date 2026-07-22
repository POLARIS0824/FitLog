package com.example.fitlog.data.repository

import com.example.fitlog.data.local.dao.ExerciseDao
import com.example.fitlog.data.mapper.toEntity
import com.example.fitlog.data.mapper.toModel
import com.example.fitlog.model.Exercise
import javax.inject.Inject

/**
 * 动作库仓库
 * 使用 ExerciseDao，管理内置与用户自定义的标准动作列表
 */
class ExerciseRepository @Inject constructor(
    private val exerciseDao: ExerciseDao
) {
    suspend fun insert(exercise: Exercise) = exerciseDao.insert(exercise.toEntity())

    suspend fun update(exercise: Exercise) = exerciseDao.update(exercise.toEntity())

    suspend fun delete(exercise: Exercise) = exerciseDao.delete(exercise.toEntity())

    suspend fun getById(id: String) = exerciseDao.getById(id)?.toModel()

    suspend fun getAll() = exerciseDao.getAll().map { it.toModel() }

    suspend fun getByCategory(category: String) = exerciseDao.getByCategory(category).map { it.toModel() }

    suspend fun getByPrimaryMuscle(muscle: String) = exerciseDao.getByPrimaryMuscle(muscle).map { it.toModel() }

    suspend fun getCustomExercises() = exerciseDao.getCustomExercises().map { it.toModel() }

    suspend fun getByName(name: String) = exerciseDao.getByName(name)?.toModel()

    suspend fun searchByName(name: String) = exerciseDao.searchByName(name).map { it.toModel() }
}