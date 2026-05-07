package com.example.myfitness.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.myfitness.data.local.entity.DailyCheckInEntity

/**
 * 训练记录（[DailyCheckInEntity]）的数据访问对象。
 */
@Dao
interface DailyCheckInDao {
    /**
     * 插入一条训练记录，若主键冲突则忽略。
     *
     * @param dailyCheckInEntity 待插入的实体
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(dailyCheckInEntity: DailyCheckInEntity)

    /**
     * 更新已有训练记录。
     *
     * @param dailyCheckInEntity 待更新的实体
     */
    @Update
    suspend fun update(dailyCheckInEntity: DailyCheckInEntity)

    /**
     * 删除指定训练记录。
     *
     * @param dailyCheckInEntity 待删除的实体
     */
    @Delete
    suspend fun delete(dailyCheckInEntity: DailyCheckInEntity)

    /**
     * 根据日期查询训练记录。
     *
     * @param date ISO-8601 格式的日期字符串，如 "2026-05-07"
     * @return 匹配的记录，若不存在则返回 null
     */
    @Query("SELECT * FROM daily_check_ins WHERE date = :date")
    suspend fun getByDate(date: String): DailyCheckInEntity?

    /**
     * 查询所有训练记录，按日期降序排列。
     *
     * @return 训练记录列表
     */
    @Query("SELECT * FROM daily_check_ins ORDER BY date DESC")
    suspend fun getAll(): List<DailyCheckInEntity>
}
