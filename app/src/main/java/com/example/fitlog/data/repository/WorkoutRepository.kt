package com.example.fitlog.data.repository

import com.example.fitlog.data.local.dao.WorkoutDao
import com.example.fitlog.data.mapper.toEntity
import com.example.fitlog.data.mapper.toModel
import com.example.fitlog.model.Workout
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject

class WorkoutRepository @Inject constructor(
    private val workoutDao: WorkoutDao
) {
    suspend fun insert(workout: Workout) = workoutDao.insert(workout.toEntity())

    suspend fun update(workout: Workout) = workoutDao.update(workout.toEntity())

    suspend fun delete(workout: Workout) = workoutDao.delete(workout.toEntity())

    fun getByDate(date: LocalDate) = workoutDao.getByDate(date).map { list ->
        list.map { it.toModel() }
    }

    fun getWorkouts(): Flow<List<Workout>> {
        return workoutDao.getAll().map { list ->
            list.map { it.toModel() }
        }
    }
}