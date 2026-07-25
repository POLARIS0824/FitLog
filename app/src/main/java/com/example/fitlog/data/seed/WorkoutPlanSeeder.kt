package com.example.fitlog.data.seed

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.example.fitlog.data.local.dao.ExerciseDao
import com.example.fitlog.data.local.dao.WorkoutPlanDao
import com.example.fitlog.data.mapper.toEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 预置训练计划种子导入器。
 *
 * 将 [PresetPlans] 写入 Room（REPLACE 语义，仅作用于预置 id，幂等），
 * 通过 DataStore 记录 seed 版本号，确保仅在首次启动或计划内容更新时执行。
 *
 * 必须在 [ExerciseSeeder] 之后执行：计划动作引用动作库 key，
 * 写入前逐一校验，缺失则跳过该计划（不写脏数据）。
 */
@Singleton
class WorkoutPlanSeeder @Inject constructor(
    private val workoutPlanDao: WorkoutPlanDao,
    private val exerciseDao: ExerciseDao,
    private val dataStore: DataStore<Preferences>,
) {

    /**
     * 检查并执行预置计划导入。
     *
     * 如果当前 seed 版本已是最新，则跳过；
     * 预置计划被用户删除后不会复活（版本门控），内容更新时递增版本号重灌。
     *
     * 注意：**仅在真正写入过计划后才标记版本**——动作库缺失导致整体跳过时
     * 不标记，下次启动（动作库就绪后）可自动重试，避免版本号被错误置位后卡死。
     */
    suspend fun seedIfNeeded() = withContext(Dispatchers.IO) {
        val currentSeedVersion = dataStore.data
            .map { it[SEED_VERSION_KEY] ?: 0 }
            .first()

        if (currentSeedVersion >= SEED_VERSION) return@withContext

        var seeded = false
        PresetPlans.all().forEach { plan ->
            val missingKeys = plan.sessions
                .flatMap { it.exercises }
                .map { it.exerciseKey }
                .distinct()
                .filter { exerciseDao.getById(it) == null }

            if (missingKeys.isNotEmpty()) {
                Log.w(TAG, "预置计划 ${plan.id} 引用的动作不存在：$missingKeys，跳过该计划")
                return@forEach
            }

            workoutPlanDao.savePlanWithSessions(
                plan = plan.toEntity(),
                sessions = plan.sessions.map { it.toEntity(plan.id) },
            )
            seeded = true
        }

        if (seeded) {
            dataStore.edit { it[SEED_VERSION_KEY] = SEED_VERSION }
        }
    }

    companion object {
        /**
         * 当前预置计划版本号，更新计划内容时递增。
         *
         * v2：修复"未写入也标记版本"的缺陷；已被错误置位 v1（计划实际为空）的
         * 安装会因此重新执行导入。
         */
        private const val SEED_VERSION = 2
        private const val TAG = "WorkoutPlanSeeder"
        private val SEED_VERSION_KEY = intPreferencesKey("plan_seed_version")
    }
}
