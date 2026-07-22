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

    /**
     * 判断指定来源文件名的训练记录是否已存在（导入去重用）。
     */
    suspend fun existsBySourceFileName(fileName: String) =
        workoutDao.getBySourceFileName(fileName) != null

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

    /**
     * 查询最近 [limit] 次训练（含动作与组级联），按日期降序。
     * suspend 一次性读取，供 agent 工具使用。
     */
    suspend fun getRecentWorkouts(limit: Int): List<Workout> {
        return workoutDao.getRecentWithDetails(limit).map { it.toModel() }
    }

    /**
     * 按主键查询单条训练（含动作与组级联）。
     */
    suspend fun getWorkoutById(id: Long): Workout? {
        return workoutDao.getByIdWithDetails(id)?.toModel()
    }

    /**
     * 查询包含指定动作（exerciseKey 精确匹配或名称模糊匹配）的最近 [limit] 次训练。
     * 返回的是完整训练日记录，调用方需自行过滤出目标动作。
     */
    suspend fun getWorkoutsByExercise(query: String, limit: Int): List<Workout> {
        return workoutDao.getByExerciseWithDetails(query, limit).map { it.toModel() }
    }
}