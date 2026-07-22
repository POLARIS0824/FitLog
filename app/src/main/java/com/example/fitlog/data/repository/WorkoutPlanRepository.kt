package com.example.fitlog.data.repository

import com.example.fitlog.data.local.dao.WorkoutPlanDao
import com.example.fitlog.data.mapper.toEntity
import com.example.fitlog.data.mapper.toModel
import com.example.fitlog.model.WorkoutPlan
import javax.inject.Inject

class WorkoutPlanRepository @Inject constructor(
    private val workoutPlanDao: WorkoutPlanDao
) {
    suspend fun insert(workoutPlan: WorkoutPlan) = workoutPlanDao.insertPlan(workoutPlan.toEntity())

    // TODO: Other CRUD operations (update, delete) can be added here as needed

    suspend fun getAllPlans(): List<WorkoutPlan> {
        return workoutPlanDao.getAllPlansWithDetails().map { it.toModel() }
    }

    suspend fun getPlanById(id: String): WorkoutPlan? {
        return workoutPlanDao.getPlanByIdWithDetails(id)?.toModel()
    }
}