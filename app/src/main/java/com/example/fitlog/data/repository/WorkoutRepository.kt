package com.example.fitlog.data.repository

import com.example.fitlog.data.local.dao.ExerciseLogDao
import com.example.fitlog.data.local.dao.SetLogDao
import com.example.fitlog.data.local.dao.WorkoutDao
import com.example.fitlog.data.mapper.toEntity
import com.example.fitlog.data.mapper.toModel
import com.example.fitlog.model.Workout
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject

/**
 * 训练日志仓库
 * 协调 WorkoutDao、ExerciseLogDao 和 SetLogDao，完成 3 层训练日志级联体系的存储、删除以及联表/级联查询聚合
 */
class WorkoutRepository @Inject constructor(
    private val workoutDao: WorkoutDao,
    private val exerciseLogDao: ExerciseLogDao,
    private val setLogDao: SetLogDao,
) {
    suspend fun insert(workout: Workout) = workoutDao.insert(workout.toEntity())

    suspend fun update(workout: Workout) = workoutDao.update(workout.toEntity())

    suspend fun delete(workout: Workout) = workoutDao.delete(workout.toEntity())

    fun getByDate(date: LocalDate) = workoutDao.getByDateWithDetails(date).map { list ->
        list.map { it.toModel() }
    }

    fun getWorkouts(): Flow<List<Workout>> {
        return workoutDao.getAllWithDetails().map { list ->
            list.map { it.toModel() }
        }
    }
}