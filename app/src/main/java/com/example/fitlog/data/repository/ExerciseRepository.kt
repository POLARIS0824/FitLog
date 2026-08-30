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
    /**
     * 写入单条动作（已存在则更新内容）。
     *
     * 必须走保行 upsert：SQLite REPLACE 是 DELETE+INSERT，对已存在 id 执行会
     * 先 DELETE 父行，触发 exercise_logs.exerciseKey 外键 SET_NULL，把历史
     * 训练日志与动作库的关联静默断开。
     */
    suspend fun insert(exercise: Exercise) =
        exerciseDao.upsertAllPreservingRows(listOf(exercise.toEntity()))

    /**
     * 批量写入动作（已存在则更新内容），同样保行——见 [insert] 的 REPLACE 警示。
     */
    suspend fun insertAll(exercises: List<Exercise>) =
        exerciseDao.upsertAllPreservingRows(exercises.map { it.toEntity() })

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
