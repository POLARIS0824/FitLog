package com.example.fitlog.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.fitlog.data.local.AppDatabase
import com.example.fitlog.data.local.entity.workout.ExerciseLogEntity
import com.example.fitlog.data.local.entity.workout.SetLogEntity
import com.example.fitlog.data.local.entity.workout.WorkoutEntity
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
 * 训练日 DAO [WorkoutDao] 新增查询的单元测试。
 *
 * 使用 Robolectric 在 JVM 环境下验证最近一次训练、最近 N 条截断排序、
 * 日期区间过滤（含级联组记录的组类型透传）。
 */
@RunWith(RobolectricTestRunner::class)
class WorkoutDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: WorkoutDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.workoutDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    /**
     * 测试 getLatest 返回日期最新的一条；空表返回 null。
     */
    @Test
    fun testGetLatest() = runTest {
        assertNull(dao.getLatest().first())

        insertWorkout(date = LocalDate.of(2026, 7, 18))
        insertWorkout(date = LocalDate.of(2026, 7, 22))
        insertWorkout(date = LocalDate.of(2026, 7, 20))

        assertEquals(LocalDate.of(2026, 7, 22), dao.getLatest().first()?.date)
    }

    /**
     * 测试 getRecentWithDetails 按日期降序截断到 limit。
     */
    @Test
    fun testGetRecentWithDetails_limitAndOrder() = runTest {
        insertWorkout(date = LocalDate.of(2026, 7, 18))
        insertWorkout(date = LocalDate.of(2026, 7, 22))
        insertWorkout(date = LocalDate.of(2026, 7, 20))

        val result = dao.getRecentWithDetails(2).first()

        assertEquals(2, result.size)
        assertEquals(LocalDate.of(2026, 7, 22), result[0].workout.date)
        assertEquals(LocalDate.of(2026, 7, 20), result[1].workout.date)
    }

    /**
     * 测试 getByDateRangeWithDetails 区间过滤含端点，级联组记录带组类型。
     */
    @Test
    fun testGetByDateRangeWithDetails_rangeAndCascadeSetType() = runTest {
        val inRangeId = insertWorkout(date = LocalDate.of(2026, 7, 20))
        insertWorkout(date = LocalDate.of(2026, 7, 18))
        val boundaryId = insertWorkout(date = LocalDate.of(2026, 7, 25))
        insertWorkout(date = LocalDate.of(2026, 7, 26))

        val exerciseLogId = db.exerciseLogDao().insert(
            ExerciseLogEntity(workoutId = inRangeId, name = "杠铃卧推", sortOrder = 0)
        )
        db.setLogDao().insertAll(
            listOf(
                SetLogEntity(exerciseLogId = exerciseLogId, setNumber = 1, weightKg = 40f, reps = 12, setType = "WARMUP"),
                SetLogEntity(exerciseLogId = exerciseLogId, setNumber = 2, weightKg = 80f, reps = 10),
            ),
        )

        val result = dao.getByDateRangeWithDetails(
            from = LocalDate.of(2026, 7, 20),
            to = LocalDate.of(2026, 7, 25),
        ).first()

        assertEquals(2, result.size)
        // 按日期降序：7-25 在前，7-20 在后
        assertEquals(boundaryId, result[0].workout.id)
        assertEquals(inRangeId, result[1].workout.id)

        val sets = result[1].exerciseLogs[0].sets
        assertEquals(2, sets.size)
        assertEquals("WARMUP", sets[0].setType)
        assertEquals("WORKING", sets[1].setType)
    }

    /**
     * 插入一条训练日并返回自增主键。
     */
    private suspend fun insertWorkout(date: LocalDate): Long {
        return dao.insert(
            WorkoutEntity(date = date, sourceFileName = null, rawContent = null),
        )
    }
}
