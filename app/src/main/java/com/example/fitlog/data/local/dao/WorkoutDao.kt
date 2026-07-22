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
     * 查询最近 [limit] 次训练日记录及其关联的练习日志，按日期降序。
     * suspend 一次性读取，供 agent 工具（list_recent_workouts）使用。
     */
    @Transaction
    @Query("SELECT * FROM workouts ORDER BY date DESC LIMIT :limit")
    suspend fun getRecentWithDetails(limit: Int): List<WorkoutWithExerciseLogs>

    /**
     * 按主键查询单条训练日记录及其关联的练习日志。
     */
    @Transaction
    @Query("SELECT * FROM workouts WHERE id = :id")
    suspend fun getByIdWithDetails(id: Long): WorkoutWithExerciseLogs?

    /**
     * 查询包含指定动作的训练日记录，按日期降序。
     *
     * 优先匹配 exerciseKey（动作库 kebab-case ID），名称 LIKE 模糊匹配兜底——
     * 大量历史记录的 exerciseKey 为空，仅有冗余的 name 字段。
     */
    @Transaction
    @Query(
        """
        SELECT * FROM workouts WHERE id IN (
            SELECT workoutId FROM exercise_logs
            WHERE exerciseKey = :query OR name LIKE '%' || :query || '%'
        ) ORDER BY date DESC LIMIT :limit
        """
    )
    suspend fun getByExerciseWithDetails(query: String, limit: Int): List<WorkoutWithExerciseLogs>
}
