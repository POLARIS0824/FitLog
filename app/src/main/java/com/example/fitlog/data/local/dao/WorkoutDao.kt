package com.example.fitlog.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.fitlog.data.local.entity.workout.WorkoutEntity

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
     * @param date ISO-8601 格式的日期字符串
     */
    @Query("SELECT * FROM workouts WHERE date = :date")
    suspend fun getByDate(date: String): List<WorkoutEntity>

    /**
     * 查询所有训练日记录，按日期降序排列。
     */
    @Query("SELECT * FROM workouts ORDER BY date DESC")
    suspend fun getAll(): List<WorkoutEntity>

    /**
     * 根据来源文件名查询训练日记录。
     */
    @Query("SELECT * FROM workouts WHERE sourceFileName = :fileName")
    suspend fun getBySourceFileName(fileName: String): WorkoutEntity?
}
