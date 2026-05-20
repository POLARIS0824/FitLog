package com.example.fitlog.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.fitlog.data.local.AppDatabase
import com.example.fitlog.data.local.entity.workout.WorkoutEntity
import com.example.fitlog.data.local.entity.workout.ExerciseLogEntity
import com.example.fitlog.data.local.entity.workout.SetLogEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class) // 关键：使用 Robolectric，无需真机即可在 JVM 运行 Android 上下文
class WorkoutRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: WorkoutRepository

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // 关键：创建内存数据库，数据仅保存在内存中，测试运行完自动销毁，速度极快
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries() // 单元测试允许在主线程进行简单 DB 操作
            .build()

        repository = WorkoutRepository(db.workoutDao())
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun testGetWorkouts_whenDataInserted_shouldReturnCompleteNestedHierarchy() = runTest {
        // 1. 准备测试数据 (插入一条 Workout，关联一个 ExerciseLog，关联两组 SetLog)
        val workoutId = db.workoutDao().insert(
            WorkoutEntity(date = LocalDate.of(2026, 5, 20), sourceFileName = "2026-05-20.md", rawContent = null)
        )

        val exerciseLogId = db.exerciseLogDao().insert(
            ExerciseLogEntity(workoutId = workoutId, name = "杠铃卧推", sortOrder = 1)
        )

        db.setLogDao().insertAll(
            listOf(
                SetLogEntity(exerciseLogId = exerciseLogId, setNumber = 1, weightKg = 80f, reps = 10),
                SetLogEntity(exerciseLogId = exerciseLogId, setNumber = 2, weightKg = 85f, reps = 8)
            )
        )

        // 2. 调用 Repository 进行响应式 Flow 读取
        // 使用 .first() 取得 Flow 发射的第一组数据
        val result = repository.getWorkouts().first()

        // 3. 验证数据拼装是否完全正确！
        assertEquals(1, result.size)

        val workout = result[0]
        assertEquals(LocalDate.of(2026, 5, 20), workout.date)
        assertEquals(1, workout.exercises.size)

        val exercise = workout.exercises[0]
        assertEquals("杠铃卧推", exercise.name)
        assertEquals(2, exercise.sets.size)

        assertEquals(80f, exercise.sets[0].weightKg)
        assertEquals(10, exercise.sets[0].reps)

        assertEquals(85f, exercise.sets[1].weightKg)
        assertEquals(8, exercise.sets[1].reps)
    }
}