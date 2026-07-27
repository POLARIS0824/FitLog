package com.example.fitlog.dev

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.fitlog.data.local.AppDatabase
import com.example.fitlog.data.repository.WorkoutRepository
import com.example.fitlog.model.ExerciseLog
import com.example.fitlog.model.SetLog
import com.example.fitlog.model.SetType
import com.example.fitlog.model.Workout
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

/**
 * 开发工具：向真机正式库灌入近一年的模拟训练历史（供 Stats 图表效果演示）。
 *
 * ## 运行方式
 *
 * 不要用 `./gradlew connectedDebugAndroidTest`——该任务跑完会**连应用一起卸载**，
 * 灌入的数据随之删除。正确姿势是手动装双 APK + 直接起插桩（应用与数据保留）：
 *
 * ```
 * ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
 * adb install -r app/build/outputs/apk/debug/app-debug.apk
 * adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
 * adb shell am instrument -w -e class com.example.fitlog.dev.MockStatsHistorySeederTest \
 *   com.example.fitlog.test/androidx.test.runner.AndroidJUnitRunner
 * adb uninstall com.example.fitlog.test   # 可选：移除测试壳，应用与数据保留
 * ```
 *
 * ## 说明
 *
 * - 数据经 [WorkoutRepository] 三层级联写入**正式库** fitlog.db（非内存库），
 *   种子跑完后冷启动 App 即可在 Stats 页看到 W/M/3M/Y 四档效果
 * - **幂等**：每条记录带 `sourceFileName = "mock://<date>"` 标记，重跑跳过已存在日期
 * - 分布：周一/三/五/六为候选训练日（约 78% 出勤），每次 3-5 个动作 ×
 *   （1 热身组 + 3 正式组）；重量带 ±10% 抖动，且越近的记录越重（模拟渐进负荷）
 * - **清除**：清除应用数据或卸载重装即可；不影响 `main` 任何生产代码
 */
@RunWith(AndroidJUnit4::class)
class MockStatsHistorySeederTest {

    private lateinit var db: AppDatabase
    private lateinit var workoutRepository: WorkoutRepository

    /**
     * 打开正式库文件（与 [com.example.fitlog.di.DatabaseModule] 同名 "fitlog.db"）。
     */
    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.databaseBuilder(context, AppDatabase::class.java, "fitlog.db")
            .fallbackToDestructiveMigration()
            .build()
        workoutRepository = WorkoutRepository(
            workoutDao = db.workoutDao(),
            exerciseLogDao = db.exerciseLogDao(),
            setLogDao = db.setLogDao(),
            db = db,
        )
    }

    /**
     * 关闭数据库连接。
     */
    @After
    fun tearDown() {
        db.close()
    }

    /**
     * 灌入 364 天模拟历史；跳过已有 mock 标记的日期（幂等重跑）。
     */
    @Test
    fun seedOneYearOfMockWorkouts() = runBlocking {
        val random = Random(SEED)
        val today = LocalDate.now()
        var inserted = 0
        var skipped = 0

        for (daysAgo in 364 downTo 0) {
            val date = today.minusDays(daysAgo.toLong())
            val isTrainingDay = date.dayOfWeek.value in TRAINING_DAYS &&
                random.nextFloat() < ATTENDANCE
            if (!isTrainingDay) continue

            val marker = "mock://$date"
            if (workoutRepository.existsBySourceFileName(marker)) {
                skipped++
                continue
            }
            workoutRepository.insert(mockWorkout(date, daysAgo, random, marker))
            inserted++
        }
        println("MockStatsHistorySeeder: inserted=$inserted, skipped=$skipped")
    }

    /**
     * 生成一次训练：3-5 个动作，每个动作 1 热身组 + 3 正式组。
     * 重量 = 动作基础重量 × 渐进系数（越早的记录越轻）× ±10% 抖动，按 2.5kg 取整。
     */
    private fun mockWorkout(
        date: LocalDate,
        daysAgo: Int,
        random: Random,
        marker: String,
    ): Workout {
        // 渐进负荷：一年前 82% → 今天 100%
        val progression = 0.82f + 0.18f * (1f - daysAgo / 364f)
        val exerciseCount = 3 + random.nextInt(3)
        val exercises = EXERCISE_POOL
            .shuffled(random)
            .take(exerciseCount)
            .map { (name, baseWeightKg) ->
                val workingWeight = roundTo2_5(
                    baseWeightKg * progression * (0.9f + random.nextFloat() * 0.2f),
                )
                ExerciseLog(
                    name = name,
                    sets = buildList {
                        add(
                            SetLog(
                                weightKg = roundTo2_5(workingWeight * 0.5f),
                                reps = 12,
                                setType = SetType.WARMUP,
                            ),
                        )
                        repeat(3) {
                            add(
                                SetLog(
                                    weightKg = workingWeight,
                                    reps = 6 + random.nextInt(7),
                                    setType = SetType.WORKING,
                                ),
                            )
                        }
                    },
                )
            }

        val startMillis = date.atTime(18, 0)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        return Workout(
            id = 0L,
            userId = 0L,
            date = date,
            exercises = exercises,
            feelings = null,
            startedAt = startMillis,
            endedAt = startMillis + 65L * 60_000L,
            sourceFileName = marker,
            rawContent = "mock",
        )
    }

    /** 按 2.5kg 取整（健身房杠铃片最小粒度）。 */
    private fun roundTo2_5(kg: Float): Float = (kg / 2.5f).toInt() * 2.5f

    private companion object {
        /** 固定随机种子：数据可复现 */
        const val SEED = 42

        /** 候选训练日：周一/三/五/六 */
        val TRAINING_DAYS = setOf(1, 3, 5, 6)

        /** 候选日出勤率 */
        const val ATTENDANCE = 0.78f

        /** 动作池：名称 → 基础重量（kg） */
        val EXERCISE_POOL = listOf(
            "杠铃卧推" to 70f,
            "杠铃深蹲" to 90f,
            "硬拉" to 110f,
            "杠铃推举" to 45f,
            "杠铃划船" to 65f,
            "哑铃推举" to 24f,
            "哑铃弯举" to 14f,
            "腿举" to 140f,
        )
    }
}
