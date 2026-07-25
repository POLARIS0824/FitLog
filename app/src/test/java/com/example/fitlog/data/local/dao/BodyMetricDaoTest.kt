package com.example.fitlog.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.fitlog.data.local.AppDatabase
import com.example.fitlog.data.local.entity.BodyMetricEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

/**
 * 身体指标 DAO [BodyMetricDao] 的单元测试。
 *
 * 使用 Robolectric 在 JVM 环境下验证按天去重 upsert、
 * 日期区间查询（含端点）与按天删除。
 */
@RunWith(RobolectricTestRunner::class)
class BodyMetricDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: BodyMetricDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.bodyMetricDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    /**
     * 测试同一天重复记录时新值覆盖旧值（按天去重）。
     */
    @Test
    fun testUpsert_sameDateReplacesOldValue() = runTest {
        val date = LocalDate.of(2026, 7, 20)

        dao.upsert(BodyMetricEntity(date = date, weightKg = 70.0f))
        dao.upsert(BodyMetricEntity(date = date, weightKg = 70.5f))

        val all = dao.getAll().first()
        assertEquals(1, all.size)
        assertEquals(70.5f, all[0].weightKg)
    }

    /**
     * 测试日期区间查询含起止端点，且按日期升序返回。
     */
    @Test
    fun testGetByDateRange_boundariesInclusiveOrderedAscending() = runTest {
        dao.upsert(BodyMetricEntity(date = LocalDate.of(2026, 7, 18), weightKg = 71f))
        dao.upsert(BodyMetricEntity(date = LocalDate.of(2026, 7, 20), weightKg = 70f))
        dao.upsert(BodyMetricEntity(date = LocalDate.of(2026, 7, 22), weightKg = 69f))
        dao.upsert(BodyMetricEntity(date = LocalDate.of(2026, 7, 25), weightKg = 68f))

        val result = dao.getByDateRange(
            from = LocalDate.of(2026, 7, 20),
            to = LocalDate.of(2026, 7, 25),
        ).first()

        assertEquals(3, result.size)
        assertEquals(LocalDate.of(2026, 7, 20), result[0].date)
        assertEquals(LocalDate.of(2026, 7, 22), result[1].date)
        assertEquals(LocalDate.of(2026, 7, 25), result[2].date)
    }

    /**
     * 测试 getLatest 返回日期最新的一条；空表返回 null。
     */
    @Test
    fun testGetLatest() = runTest {
        assertNull(dao.getLatest().first())

        dao.upsert(BodyMetricEntity(date = LocalDate.of(2026, 7, 20), weightKg = 70f))
        dao.upsert(BodyMetricEntity(date = LocalDate.of(2026, 7, 18), weightKg = 71f))

        val latest = dao.getLatest().first()
        assertEquals(LocalDate.of(2026, 7, 20), latest?.date)
        assertEquals(70f, latest?.weightKg)
    }

    /**
     * 测试按天删除。
     */
    @Test
    fun testDeleteByDate() = runTest {
        dao.upsert(BodyMetricEntity(date = LocalDate.of(2026, 7, 20), weightKg = 70f))
        dao.upsert(BodyMetricEntity(date = LocalDate.of(2026, 7, 21), weightKg = 69f))

        dao.deleteByDate(LocalDate.of(2026, 7, 20))

        val all = dao.getAll().first()
        assertEquals(1, all.size)
        assertEquals(LocalDate.of(2026, 7, 21), all[0].date)
    }
}
