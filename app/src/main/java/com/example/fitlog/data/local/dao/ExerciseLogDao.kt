package com.example.fitlog.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.fitlog.data.local.entity.workout.ExerciseLogEntity

/**
 * 动作记录（[ExerciseLogEntity]）的数据访问对象。
 */
@Dao
interface ExerciseLogDao {
    /**
     * 插入一条动作记录，若主键冲突则忽略。
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(exerciseLogEntity: ExerciseLogEntity): Long

    /**
     * 批量插入动作记录。
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(exerciseLogEntities: List<ExerciseLogEntity>): List<Long>

    /**
     * 更新已有动作记录。
     */
    @Update
    suspend fun update(exerciseLogEntity: ExerciseLogEntity)

    /**
     * 删除指定动作记录。
     */
    @Delete
    suspend fun delete(exerciseLogEntity: ExerciseLogEntity)

    /**
     * 根据训练日 ID 查询所有动作记录，按排序序号升序排列。
     */
    @Query("SELECT * FROM exercise_logs WHERE workoutId = :workoutId ORDER BY sortOrder ASC")
    suspend fun getByWorkoutId(workoutId: Long): List<ExerciseLogEntity>

    /**
     * 按主键删除动作记录（set_logs 经外键 CASCADE 连带删除）。
     */
    @Query("DELETE FROM exercise_logs WHERE id = :id")
    suspend fun deleteById(id: Long)

    /**
     * 删除指定训练日下的所有动作记录（set_logs 由外键 CASCADE 连带删除）。
     *
     * @param workoutId 训练日 ID
     */
    @Query("DELETE FROM exercise_logs WHERE workoutId = :workoutId")
    suspend fun deleteByWorkoutId(workoutId: Long)
}
