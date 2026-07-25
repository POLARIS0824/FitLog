package com.example.fitlog.data.seed

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.fitlog.data.local.AppDatabase
import com.example.fitlog.data.local.entity.ExerciseEntity
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
        val dataStore = createTestPreferencesDataStore(tmpFolder.newFile("seeder_prefs.preferences_pb"))
        insertPresetReferencedExercises()
        val seeder = WorkoutPlanSeeder(db.workoutPlanDao(), db.exerciseDao(), dataStore)

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
        assertEquals(1, version)
    }

    /**
     * 测试版本门控：seed 版本已是最新时不写入任何数据。
     */
    @Test
    fun testSeed_skippedWhenVersionUpToDate() = runTest {
        val dataStore = createTestPreferencesDataStore(tmpFolder.newFile("seeder_prefs2.preferences_pb"))
        // 预置版本号为当前 SEED_VERSION（1）
        dataStore.edit { it[intPreferencesKey("plan_seed_version")] = 1 }
        val seeder = WorkoutPlanSeeder(db.workoutPlanDao(), db.exerciseDao(), dataStore)

        seeder.seedIfNeeded()

        assertEquals(0, db.workoutPlanDao().getAllPlans().size)
    }

    /**
     * 测试动作库缺失引用动作时跳过对应计划（不写脏数据）。
     */
    @Test
    fun testSeed_skipsPlanWhenExerciseKeysMissing() = runTest {
        val dataStore = createTestPreferencesDataStore(tmpFolder.newFile("seeder_prefs3.preferences_pb"))
        // 动作库为空 → 两套计划都缺少引用动作，应全部跳过
        val seeder = WorkoutPlanSeeder(db.workoutPlanDao(), db.exerciseDao(), dataStore)

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
}
