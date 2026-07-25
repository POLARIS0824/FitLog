package com.example.fitlog.data.repository

import com.example.fitlog.data.local.dao.BodyMetricDao
import com.example.fitlog.data.mapper.toEntity
import com.example.fitlog.data.mapper.toModel
import com.example.fitlog.model.BodyMetric
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject

/**
 * 身体指标仓库（当前仅体重）。
 *
 * 支撑 Stats 体重曲线与 Today 快捷记体重；同一天重复记录按天去重（新值覆盖旧值）。
 */
class BodyMetricRepository @Inject constructor(
    private val bodyMetricDao: BodyMetricDao,
) {
    /**
     * 记录/更新某天的身体指标（按天去重）。
     */
    suspend fun upsert(metric: BodyMetric) = bodyMetricDao.upsert(metric.toEntity())

    /**
     * 观察全部体重记录，按日期升序。
     */
    fun getAll(): Flow<List<BodyMetric>> = bodyMetricDao.getAll().map { list ->
        list.map { it.toModel() }
    }

    /**
     * 观察日期区间内的体重记录，按日期升序。
     *
     * @param from 起始日期（含）
     * @param to 结束日期（含）
     */
    fun getByDateRange(from: LocalDate, to: LocalDate): Flow<List<BodyMetric>> =
        bodyMetricDao.getByDateRange(from, to).map { list ->
            list.map { it.toModel() }
        }

    /**
     * 观察最新一条体重记录。
     */
    fun getLatest(): Flow<BodyMetric?> = bodyMetricDao.getLatest().map { it?.toModel() }

    /**
     * 删除某天的体重记录。
     */
    suspend fun deleteByDate(date: LocalDate) = bodyMetricDao.deleteByDate(date)
}
