package com.example.myfitness.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.myfitness.data.local.entity.SetLogEntity

/**
 * 组记录（[SetLogEntity]）的数据访问对象。
 */
@Dao
interface SetLogDao {
    /**
     * 插入一条组记录，若主键冲突则忽略。
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(setLogEntity: SetLogEntity): Long

    /**
     * 批量插入组记录。
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(setLogEntities: List<SetLogEntity>): List<Long>

    /**
     * 更新已有组记录。
     */
    @Update
    suspend fun update(setLogEntity: SetLogEntity)

    /**
     * 删除指定组记录。
     */
    @Delete
    suspend fun delete(setLogEntity: SetLogEntity)

    /**
     * 根据动作记录 ID 查询所有组记录，按组号升序排列。
     */
    @Query("SELECT * FROM set_logs WHERE exerciseLogId = :exerciseLogId ORDER BY setNumber ASC")
    suspend fun getByExerciseLogId(exerciseLogId: Long): List<SetLogEntity>
}
