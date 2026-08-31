package com.example.fitlog.data.seed

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.fitlog.data.local.AppDatabase
import com.example.fitlog.data.local.entity.ExerciseEntity
import com.example.fitlog.data.mapper.toEntity
import com.example.fitlog.data.repository.WorkoutPlanRepository
import com.example.fitlog.testing.createTestPreferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 预置计划种子导入器 [WorkoutPlanSeeder] 的单元测试。
 *
 * 使用 Robolectric 内存数据库与测试 DataStore 验证：
 * 版本门控（已最新时零写入）、首次导入写入全部计划与训练日、写后版本号更新。
 */
@RunWith(RobolectricTestRunner::class)
class WorkoutPlanSeederTest {

    /**
     * 每个测试方法使用独立的临时目录存放 DataStore 文件。
     */
    @get:Rule
    val tmpFolder = TemporaryFolder()

    private lateinit var db: AppDatabase

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDb() {
        db.close()
    }

    /**
     * 测试首次导入：动作库齐备时写入全部预置计划，并更新 seed 版本号。
     */
    @Test
    fun testSeed_firstRunWritesAllPlansAndUpdatesVersion() = runTest {
        val dataStore = createTestPreferencesDataStore(
            tmpFolder.newFile("seeder_prefs.preferences_pb"),
            backgroundScope,
        )
        insertPresetReferencedExercises()
        val seeder = createSeeder(dataStore)

        seeder.seedIfNeeded()

        val plans = db.workoutPlanDao().getAllPlans()
        assertEquals(PresetPlans.all().size, plans.size)

        val expectedSessionCount = PresetPlans.all().sumOf { it.sessions.size }
        val actualSessionCount = plans.sumOf {
            db.workoutPlanDao().getSessionsByPlanId(it.id).size
        }
        assertEquals(expectedSessionCount, actualSessionCount)

        val version = dataStore.data
            .map { it[intPreferencesKey("plan_seed_version")] ?: 0 }
            .first()
        assertEquals(2, version)
    }

    /**
     * 测试版本门控：seed 版本已最新且计划表非空时不写入任何数据（不重灌）。
     */
    @Test
    fun testSeed_skippedWhenVersionUpToDateAndPlansExist() = runTest {
        val dataStore = createTestPreferencesDataStore(
            tmpFolder.newFile("seeder_prefs2.preferences_pb"),
            backgroundScope,
        )
        // 预置版本号为当前 SEED_VERSION（2），并先写入一套计划模拟已种子过的安装
        dataStore.edit { it[intPreferencesKey("plan_seed_version")] = 2 }
        insertPresetReferencedExercises()
        db.workoutPlanDao().insertPlanIgnore(PresetPlans.all().first().toEntity())
        val seeder = createSeeder(dataStore)

        seeder.seedIfNeeded()

        // 版本最新且计划表非空 → 短路，不重灌：计划数维持 1
        assertEquals(1, db.workoutPlanDao().getAllPlans().size)
    }

    /**
     * 测试清库重灌保护：seed 版本已最新但计划表为空
     * （fallbackToDestructiveMigration 清库后 DataStore 版本号残留）时重新导入预置计划。
     */
    @Test
    fun testSeed_reSeedsWhenVersionUpToDateButPlansMissing() = runTest {
        val dataStore = createTestPreferencesDataStore(
            tmpFolder.newFile("seeder_prefs6.preferences_pb"),
            backgroundScope,
        )
        // 版本已最新，但计划表为空（模拟清库后残留）
        dataStore.edit { it[intPreferencesKey("plan_seed_version")] = 2 }
        insertPresetReferencedExercises()
        val seeder = createSeeder(dataStore)

        seeder.seedIfNeeded()

        // 计划表为空 → 强制重灌全部预置计划
        assertEquals(PresetPlans.all().size, db.workoutPlanDao().getAllPlans().size)
    }

    /**
     * 测试动作库缺失引用动作时跳过对应计划（不写脏数据）。
     */
    @Test
    fun testSeed_skipsPlanWhenExerciseKeysMissing() = runTest {
        val dataStore = createTestPreferencesDataStore(
            tmpFolder.newFile("seeder_prefs3.preferences_pb"),
            backgroundScope,
        )
        // 动作库为空 → 两套计划都缺少引用动作，应全部跳过
        val seeder = createSeeder(dataStore)

        seeder.seedIfNeeded()

        assertEquals(0, db.workoutPlanDao().getAllPlans().size)
    }

    /**
     * 测试整体跳过（一套都没写入）时不标记 seed 版本号，
     * 下次启动（动作库就绪后）可自动重试——修复"未写入也标记版本"缺陷。
     */
    @Test
    fun testSeed_doesNotMarkVersionWhenNothingWritten() = runTest {
        val dataStore = createTestPreferencesDataStore(
            tmpFolder.newFile("seeder_prefs4.preferences_pb"),
            backgroundScope,
        )
        // 动作库为空 → 全部跳过
        val seeder = createSeeder(dataStore)

        seeder.seedIfNeeded()

        val version = dataStore.data
            .map { it[intPreferencesKey("plan_seed_version")] ?: 0 }
            .first()
        assertEquals(0, version)
    }

    /**
     * 测试已被错误置位旧版本号（v1，计划实际为空）的安装会重新执行导入。
     */
    @Test
    fun testSeed_reSeedsWhenV1FlaggedButPlansMissing() = runTest {
        val dataStore = createTestPreferencesDataStore(
            tmpFolder.newFile("seeder_prefs5.preferences_pb"),
            backgroundScope,
        )
        // 模拟被旧缺陷污染的安装：v1 已标记但计划表为空
        dataStore.edit { it[intPreferencesKey("plan_seed_version")] = 1 }
        insertPresetReferencedExercises()
        val seeder = createSeeder(dataStore)

        seeder.seedIfNeeded()

        assertEquals(PresetPlans.all().size, db.workoutPlanDao().getAllPlans().size)
    }

    /**
     * 测试用户主动删光全部计划后，预置计划不再于每次启动时"复活"
     * （repository 的 preset_plans_cleared 标记拦截重灌）。
     */
    @Test
    fun testSeed_skipsWhenUserClearedAllPlans() = runTest {
        val dataStore = createTestPreferencesDataStore(
            tmpFolder.newFile("seeder_prefs7.preferences_pb"),
            backgroundScope,
        )
        // 版本已最新 + 计划表为空 + 用户删光标记（键名与
        // WorkoutPlanRepository.PRESET_PLANS_CLEARED_KEY 对应，私有故按名直写）
        dataStore.edit {
            it[intPreferencesKey("plan_seed_version")] = 2
            it[booleanPreferencesKey("preset_plans_cleared")] = true
        }
        insertPresetReferencedExercises()
        val seeder = createSeeder(dataStore)

        seeder.seedIfNeeded()

        assertEquals(0, db.workoutPlanDao().getAllPlans().size)
    }

    /**
     * 向动作库插入预置计划引用的全部动作（最小实体）。
     */
    private suspend fun insertPresetReferencedExercises() {
        val keys = PresetPlans.all()
            .flatMap { it.sessions }
            .flatMap { it.exercises }
            .map { it.exerciseKey }
            .distinct()
        db.exerciseDao().insertAll(keys.map { ExerciseEntity(id = it, name = it) })
    }

    /** 组装被测对象：seeder 依赖 repository 提供的"用户已删光"标记。 */
    private fun createSeeder(
        dataStore: DataStore<Preferences>,
    ): WorkoutPlanSeeder = WorkoutPlanSeeder(
        workoutPlanDao = db.workoutPlanDao(),
        exerciseDao = db.exerciseDao(),
        workoutPlanRepository = WorkoutPlanRepository(db.workoutPlanDao(), dataStore),
        dataStore = dataStore,
    )
}
