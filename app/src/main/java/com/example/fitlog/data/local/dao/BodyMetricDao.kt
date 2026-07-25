package com.example.fitlog.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.fitlog.data.local.entity.BodyMetricEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * 身体指标（[BodyMetricEntity]）的数据访问对象。
 */
@Dao
interface BodyMetricDao {

    /**
     * 写入某天的身体指标；同一天重复记录时替换（date 为主键 + REPLACE）。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: BodyMetricEntity)

    /**
     * 观察全部体重记录，按日期升序（Stats 体重曲线）。
     */
    @Query("SELECT * FROM body_metrics ORDER BY date ASC")
    fun getAll(): Flow<List<BodyMetricEntity>>

    /**
     * 观察日期区间内的体重记录，按日期升序。
     *
     * @param from 起始日期（含）
     * @param to 结束日期（含）
     */
    @Query("SELECT * FROM body_metrics WHERE date BETWEEN :from AND :to ORDER BY date ASC")
    fun getByDateRange(from: LocalDate, to: LocalDate): Flow<List<BodyMetricEntity>>

    /**
     * 观察最新一条体重记录。
     */
    @Query("SELECT * FROM body_metrics ORDER BY date DESC LIMIT 1")
    fun getLatest(): Flow<BodyMetricEntity?>

    /**
     * 删除某天的体重记录。
     */
    @Query("DELETE FROM body_metrics WHERE date = :date")
    suspend fun deleteByDate(date: LocalDate)
}
