package com.example.fitlog.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.fitlog.data.local.AppDatabase
import com.example.fitlog.data.local.entity.ExerciseEntity
import com.example.fitlog.data.local.entity.workout.ExerciseLogEntity
import com.example.fitlog.data.local.entity.workout.SetLogEntity
import com.example.fitlog.data.local.entity.workout.WorkoutEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * [WorkoutDao] 的仪器化测试。
 *
 * 在真机/模拟器上使用真实 SQLite 验证：
 * - 3 层级联（workouts → exercise_logs → set_logs）的外键行为（CASCADE / SET_NULL）
 * - 冲突策略（IGNORE）
 * - 排序与条件查询
 *
 * 与 JVM 端 Robolectric 仓库测试互补：这里验证的是真实 Android SQLite 的
 * SQL 方言与外键强制执行，而非仓库的映射逻辑。
 */
@RunWith(AndroidJUnit4::class)
class WorkoutDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var workoutDao: WorkoutDao
    private lateinit var exerciseLogDao: ExerciseLogDao
    private lateinit var setLogDao: SetLogDao

    /**
     * 创建内存数据库（数据不落地，测试结束即销毁）。
     */
    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        workoutDao = db.workoutDao()
        exerciseLogDao = db.exerciseLogDao()
        setLogDao = db.setLogDao()
    }

    /**
     * 关闭数据库。
     */
    @After
    fun closeDb() {
        db.close()
    }

    private fun workout(id: Long = 0, date: LocalDate, fileName: String? = null) = WorkoutEntity(
        id = id,
        date = date,
        feelings = null,
        sourceFileName = fileName,
        rawContent = null,
    )

    /**
     * 测试插入后可按日期查询到记录。
     */
    @Test
    fun insertThenGetByDate() = runTest {
        workoutDao.insert(workout(date = LocalDate.of(2026, 5, 20)))

        val result = workoutDao.getByDate(LocalDate.of(2026, 5, 20)).first()
        assertEquals(1, result.size)
        assertEquals(LocalDate.of(2026, 5, 20), result[0].date)
    }

    /**
     * 测试主键冲突时插入被忽略（IGNORE 策略返回 -1，原记录不变）。
     */
    @Test
    fun insertWithDuplicateId_isIgnored() = runTest {
        val first = workoutDao.insert(workout(id = 5, date = LocalDate.of(2026, 5, 20), fileName = "a.md"))
        val second = workoutDao.insert(workout(id = 5, date = LocalDate.of(2026, 6, 1), fileName = "b.md"))

        assertTrue(first > 0)
        assertEquals(-1L, second)

        val all = workoutDao.getAll().first()
        assertEquals(1, all.size)
        assertEquals("a.md", all[0].sourceFileName)
    }

    /**
     * 测试 getAll 按日期降序返回。
     */
    @Test
    fun getAll_orderedByDateDesc() = runTest {
        workoutDao.insert(workout(date = LocalDate.of(2026, 5, 18)))
        workoutDao.insert(workout(date = LocalDate.of(2026, 5, 22)))
        workoutDao.insert(workout(date = LocalDate.of(2026, 5, 20)))

        val all = workoutDao.getAll().first()
        assertEquals(
            listOf(
                LocalDate.of(2026, 5, 22),
                LocalDate.of(2026, 5, 20),
                LocalDate.of(2026, 5, 18),
            ),
            all.map { it.date },
        )
    }

    /**
     * 测试按来源文件名查询：存在返回记录，不存在返回 null。
     */
    @Test
    fun getBySourceFileName_foundAndNotFound() = runTest {
        workoutDao.insert(workout(date = LocalDate.of(2026, 5, 20), fileName = "2026-05-20.md"))

        assertNotNull(workoutDao.getBySourceFileName("2026-05-20.md"))
        assertNull(workoutDao.getBySourceFileName("2099-01-01.md"))
    }

    /**
     * 测试更新已有记录。
     */
    @Test
    fun update_modifiesRow() = runTest {
        val id = workoutDao.insert(workout(date = LocalDate.of(2026, 5, 20), fileName = "a.md"))
        val existing = workoutDao.getBySourceFileName("a.md")!!

        workoutDao.update(existing.copy(feelings = "今天状态很好"))
        assertEquals(id, existing.id)

        val updated = workoutDao.getBySourceFileName("a.md")!!
        assertEquals("今天状态很好", updated.feelings)
    }

    /**
     * 测试删除训练日时级联删除其动作记录与组记录（外键 CASCADE）。
     */
    @Test
    fun deleteWorkout_cascadesExerciseLogsAndSetLogs() = runTest {
        val workoutId = workoutDao.insert(workout(date = LocalDate.of(2026, 5, 20)))
        val logId = exerciseLogDao.insert(
            ExerciseLogEntity(workoutId = workoutId, name = "杠铃卧推", sortOrder = 1),
        )
        setLogDao.insertAll(
            listOf(
                SetLogEntity(exerciseLogId = logId, setNumber = 1, weightKg = 80f, reps = 10),
                SetLogEntity(exerciseLogId = logId, setNumber = 2, weightKg = 85f, reps = 8),
            ),
        )

        val workout = workoutDao.getByDate(LocalDate.of(2026, 5, 20)).first()[0]
        workoutDao.delete(workout)

        assertTrue(workoutDao.getAll().first().isEmpty())
        assertTrue(exerciseLogDao.getByWorkoutId(workoutId).isEmpty())
        assertTrue(setLogDao.getByExerciseLogId(logId).isEmpty())
    }

    /**
     * 测试删除动作库条目时，引用它的动作记录的 exerciseKey 被置为 NULL（SET_NULL），
     * 且动作记录本身保留（name 冗余字段用于降级显示）。
     */
    @Test
    fun deleteExercise_setsExerciseKeyNullButKeepsLog() = runTest {
        db.exerciseDao().insert(ExerciseEntity(id = "barbell-bench-press", name = "杠铃卧推"))
        val workoutId = workoutDao.insert(workout(date = LocalDate.of(2026, 5, 20)))
        val logId = exerciseLogDao.insert(
            ExerciseLogEntity(
                workoutId = workoutId,
                exerciseKey = "barbell-bench-press",
                name = "杠铃卧推",
                sortOrder = 1,
            ),
        )

        db.exerciseDao().delete(ExerciseEntity(id = "barbell-bench-press", name = "杠铃卧推"))

        val logs = exerciseLogDao.getByWorkoutId(workoutId)
        assertEquals(1, logs.size)
        assertEquals(logId, logs[0].id)
        assertNull(logs[0].exerciseKey)
        assertEquals("杠铃卧推", logs[0].name)
    }

    /**
     * 测试 @Relation 级联查询：3 层结构组装完整，训练日按日期降序、
     * 每个训练日的动作与组记录正确挂载。
     */
    @Test
    fun getAllWithDetails_assemblesThreeLevelHierarchy() = runTest {
        val oldId = workoutDao.insert(workout(date = LocalDate.of(2026, 5, 19)))
        val newId = workoutDao.insert(workout(date = LocalDate.of(2026, 5, 20)))

        val newLogId = exerciseLogDao.insert(
            ExerciseLogEntity(workoutId = newId, name = "杠铃卧推", sortOrder = 1),
        )
        setLogDao.insert(
            SetLogEntity(exerciseLogId = newLogId, setNumber = 1, weightKg = 80f, reps = 10),
        )
        exerciseLogDao.insert(
            ExerciseLogEntity(workoutId = oldId, name = "哑铃推举", sortOrder = 1),
        )

        val result = workoutDao.getAllWithDetails().first()

        assertEquals(2, result.size)
        // 日期降序：5-20 在前
        assertEquals(LocalDate.of(2026, 5, 20), result[0].workout.date)
        assertEquals(LocalDate.of(2026, 5, 19), result[1].workout.date)

        val firstWorkout = result[0]
        assertEquals(1, firstWorkout.exerciseLogs.size)
        assertEquals("杠铃卧推", firstWorkout.exerciseLogs[0].exerciseLog.name)
        assertEquals(1, firstWorkout.exerciseLogs[0].sets.size)
        assertEquals(80f, firstWorkout.exerciseLogs[0].sets[0].weightKg)

        val secondWorkout = result[1]
        assertEquals(1, secondWorkout.exerciseLogs.size)
        assertTrue(secondWorkout.exerciseLogs[0].sets.isEmpty())
    }
}
