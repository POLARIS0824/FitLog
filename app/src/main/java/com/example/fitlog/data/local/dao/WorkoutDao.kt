package com.example.fitlog.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.fitlog.data.local.entity.workout.WorkoutEntity
import com.example.fitlog.data.local.relation.WorkoutWithExerciseLogs
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * 训练日（[WorkoutEntity]）的数据访问对象。
 */
@Dao
interface WorkoutDao {
    /**
     * 插入一条训练日记录，若主键冲突则忽略。
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(workoutEntity: WorkoutEntity): Long

    /**
     * 更新已有训练日记录。
     */
    @Update
    suspend fun update(workoutEntity: WorkoutEntity)

    /**
     * 删除指定训练日记录。
     */
    @Delete
    suspend fun delete(workoutEntity: WorkoutEntity)

    /**
     * 根据日期查询训练日记录。
     *
     * @param date LocalDate
     */
    @Query("SELECT * FROM workouts WHERE date = :date")
    fun getByDate(date: LocalDate): Flow<List<WorkoutEntity>>

    /**
     * 查询所有训练日记录，按日期降序排列。
     */
    @Query("SELECT * FROM workouts ORDER BY date DESC")
    fun getAll(): Flow<List<WorkoutEntity>>

    /**
     * 根据来源文件名查询训练日记录。
     */
    @Query("SELECT * FROM workouts WHERE sourceFileName = :fileName")
    suspend fun getBySourceFileName(fileName: String): WorkoutEntity?

    /**
     * 查询所有训练日记录及其关联的练习日志，按日期降序排列。
     * 使用 @Transaction 注解确保查询和关联数据的原子性
     */
    @Transaction
    @Query("SELECT * FROM workouts ORDER BY date DESC")
    fun getAllWithDetails(): Flow<List<WorkoutWithExerciseLogs>>

    /**
     * 根据日期查询训练日记录及其关联的练习日志。
     * 使用 @Transaction 注解确保查询和关联数据的原子性
     */
    @Transaction
    @Query("SELECT * FROM workouts WHERE date = :date")
    fun getByDateWithDetails(date: LocalDate): Flow<List<WorkoutWithExerciseLogs>>

    /**
     * 查询最近一次训练（Today「最近训练」与「身体状态」卡片）。
     */
    @Query("SELECT * FROM workouts ORDER BY date DESC, id DESC LIMIT 1")
    fun getLatest(): Flow<WorkoutEntity?>

    /**
     * 查询最近 N 条训练（含动作与组），按日期降序排列。
     * 使用 @Transaction 注解确保查询和关联数据的原子性。
     *
     * @param limit 返回条数上限
     */
    @Transaction
    @Query("SELECT * FROM workouts ORDER BY date DESC, id DESC LIMIT :limit")
    fun getRecentWithDetails(limit: Int): Flow<List<WorkoutWithExerciseLogs>>

    /**
     * 查询日期区间内的训练（含动作与组），按日期降序排列
     * （Today「本周概览」与 Stats 区间聚合取数；聚合在 Kotlin 端完成）。
     * 使用 @Transaction 注解确保查询和关联数据的原子性。
     *
     * @param from 起始日期（含）
     * @param to 结束日期（含）
     */
    @Transaction
    @Query("SELECT * FROM workouts WHERE date BETWEEN :from AND :to ORDER BY date DESC")
    fun getByDateRangeWithDetails(from: LocalDate, to: LocalDate): Flow<List<WorkoutWithExerciseLogs>>
}
