package com.example.fitlog.data.repository

import androidx.room.withTransaction
import com.example.fitlog.data.local.AppDatabase
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
 * 训练日志仓库。
 *
 * 协调 [WorkoutDao]、[ExerciseLogDao] 和 [SetLogDao]，
 * 通过 [androidx.room.withTransaction] 完成 3 层训练日志（Workout → ExerciseLog → SetLog）
 * 的事务级联存储、删除以及联表查询聚合。
 */
class WorkoutRepository @Inject constructor(
    private val workoutDao: WorkoutDao,
    private val exerciseLogDao: ExerciseLogDao,
    private val setLogDao: SetLogDao,
    private val db: AppDatabase,
) {
    /**
     * 事务级联插入完整训练日志。
     *
     * 依次写入 workouts → exercise_logs → set_logs：
     * 父行插入返回的自增主键作为子行的外键。
     * 任一环节失败则整体回滚。
     *
     * @param workout 完整训练日志（含动作与组）
     * @return 新插入训练日的数据库主键（冲突被 IGNORE 时为 -1）
     */
    suspend fun insert(workout: Workout): Long = db.withTransaction {
        val workoutId = workoutDao.insert(workout.toEntity())
        insertChildren(workoutId, workout)
        workoutId
    }

    /**
     * 事务级联更新：更新父行后删除旧子行并重新插入。
     *
     * set_logs 由 exercise_logs 的外键 CASCADE 连带删除，无需显式清理。
     *
     * @param workout 完整训练日志（id 必须已存在）
     */
    suspend fun update(workout: Workout) = db.withTransaction {
        workoutDao.update(workout.toEntity())
        exerciseLogDao.deleteByWorkoutId(workout.id)
        insertChildren(workout.id, workout)
    }

    /**
     * 级联插入动作与组（须在事务内调用）。
     */
    private suspend fun insertChildren(workoutId: Long, workout: Workout) {
        workout.exercises.forEachIndexed { index, exerciseLog ->
            val exerciseLogId = exerciseLogDao.insert(
                exerciseLog.toEntity(workoutId = workoutId, sortOrder = index),
            )
            val setEntities = exerciseLog.sets.mapIndexed { setIndex, setLog ->
                setLog.toEntity(exerciseLogId = exerciseLogId, setNumber = setIndex + 1)
            }
            setLogDao.insertAll(setEntities)
        }
    }

    /**
     * 判断指定来源文件名的训练记录是否已存在（导入去重用）。
     */
    suspend fun existsBySourceFileName(fileName: String) =
        workoutDao.getBySourceFileName(fileName) != null

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
     * 观察最近一次训练（Today「身体状态」卡片）。
     */
    fun getLatest(): Flow<Workout?> = workoutDao.getLatest().map { it?.toModel() }

    /**
     * 观察最近 N 条完整训练日志（Today「最近训练」列表）。
     *
     * @param limit 返回条数上限
     */
    fun getRecentWithDetails(limit: Int): Flow<List<Workout>> =
        workoutDao.getRecentWithDetails(limit).map { list ->
            list.map { it.toModel() }
        }

    /**
     * 观察日期区间内的完整训练日志（Today「本周概览」与 Stats 区间聚合）。
     *
     * @param from 起始日期（含）
     * @param to 结束日期（含）
     */
    fun getByDateRange(from: LocalDate, to: LocalDate): Flow<List<Workout>> =
        workoutDao.getByDateRangeWithDetails(from, to).map { list ->
            list.map { it.toModel() }
        }
}