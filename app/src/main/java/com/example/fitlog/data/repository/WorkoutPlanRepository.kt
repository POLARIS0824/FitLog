package com.example.fitlog.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.fitlog.data.local.dao.WorkoutPlanDao
import com.example.fitlog.data.mapper.toEntity
import com.example.fitlog.data.mapper.toModel
import com.example.fitlog.model.PlannedSession
import com.example.fitlog.model.WorkoutPlan
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * 训练计划仓库。
 *
 * 管理两件事：
 * 1. **计划的增删改查** → 通过 [WorkoutPlanDao] 操作 Room
 *    （计划 + 训练日，动作清单内嵌于训练日的 JSON 列）
 * 2. **当前激活计划的持久化** → 通过 [DataStore] 存储 `active_plan_id`
 *
 * 激活计划是"计划域"的选中态，与 [AIProviderConfigRepository] 的
 * "Room 存数据 + DataStore 存激活 ID"模式同构：Today/Plan 页只需收集
 * [activePlan]，无需先拿 ID 再查计划。
 */
class WorkoutPlanRepository @Inject constructor(
    private val workoutPlanDao: WorkoutPlanDao,
    private val dataStore: DataStore<Preferences>,
) {

    private companion object {
        val ACTIVE_PLAN_KEY = stringPreferencesKey("active_plan_id")

        /**
         * 用户已主动删光全部计划的标记。表空无法区分"清库残留"和"用户主动删光"，
         * 预置计划播种器（WorkoutPlanSeeder）据此避免把用户刻意清空的预置计划
         * 在每次启动时复活。
         */
        val PRESET_PLANS_CLEARED_KEY = booleanPreferencesKey("preset_plans_cleared")
    }

    /**
     * 事务级保存完整计划（计划 + 所有训练日）。
     */
    suspend fun save(workoutPlan: WorkoutPlan) = workoutPlanDao.savePlanWithSessions(
        plan = workoutPlan.toEntity(),
        sessions = workoutPlan.sessions.map { it.toEntity(workoutPlan.id) },
    )

    /**
     * 删除训练计划；若删除的是当前激活计划，联动清除激活 ID。
     *
     * 删光全部计划时记录"用户已主动清空"标记：表空无法区分"清库残留"和
     * "用户主动删光"，标记供预置计划播种器跳过重灌（否则用户永远删不净预置计划）。
     */
    suspend fun delete(id: String) {
        workoutPlanDao.deletePlan(id)
        if (activePlanId.first() == id) {
            clearActivePlanId()
        }
        if (workoutPlanDao.getPlanCount() == 0) {
            dataStore.edit { prefs ->
                prefs[PRESET_PLANS_CLEARED_KEY] = true
            }
        }
    }

    /**
     * 用户是否已主动删光全部计划（供预置计划播种器判断是否跳过重灌）。
     */
    suspend fun isPresetPlansCleared(): Boolean =
        dataStore.data.map { prefs -> prefs[PRESET_PLANS_CLEARED_KEY] ?: false }.first()

    suspend fun getAllPlans(): List<WorkoutPlan> {
        return workoutPlanDao.getAllPlansWithDetails().map { it.toModel() }
    }

    suspend fun getPlanById(id: String): WorkoutPlan? {
        return workoutPlanDao.getPlanByIdWithDetails(id)?.toModel()
    }

    /**
     * 观察所有训练计划（含级联训练日），按创建日期降序。
     */
    fun getAllPlansFlow(): Flow<List<WorkoutPlan>> =
        workoutPlanDao.getAllPlansWithDetailsFlow().map { list ->
            list.map { it.toModel() }
        }

    /**
     * 观察单个训练计划（含级联训练日）。
     */
    fun getPlanByIdFlow(id: String): Flow<WorkoutPlan?> =
        workoutPlanDao.getPlanByIdWithDetailsFlow(id).map { it?.toModel() }

    /**
     * 观察某计划下一个未完成的训练日（Today 待练卡 / Plan 页进度）。
     */
    fun getNextIncompleteSession(planId: String): Flow<PlannedSession?> =
        workoutPlanDao.getNextIncompleteSession(planId).map { it?.toModel() }

    suspend fun markSessionCompleted(sessionId: String, workoutId: Long) =
        workoutPlanDao.markSessionCompleted(sessionId, workoutId)

    suspend fun unmarkSessionCompleted(sessionId: String) =
        workoutPlanDao.unmarkSessionCompleted(sessionId)

    /**
     * 按主键取单个训练日（训练执行流预填动作清单用）。
     */
    suspend fun getSessionById(sessionId: String): PlannedSession? =
        workoutPlanDao.getSessionById(sessionId)?.toModel()

    // ──────────────────────────────────────
    // 激活管理 — "当前正在执行哪套计划？"
    // ──────────────────────────────────────

    /**
     * 观察当前激活计划 ID（未设置时为 null）。
     */
    val activePlanId: Flow<String?> = dataStore.data.map { prefs ->
        prefs[ACTIVE_PLAN_KEY]
    }

    /**
     * 设置当前激活计划。
     */
    suspend fun setActivePlanId(id: String) {
        dataStore.edit { prefs ->
            prefs[ACTIVE_PLAN_KEY] = id
        }
    }

    /**
     * 清除当前激活计划 ID。
     */
    suspend fun clearActivePlanId() {
        dataStore.edit { prefs ->
            prefs.remove(ACTIVE_PLAN_KEY)
        }
    }

    /**
     * 观察当前激活计划的完整对象（DataStore ID → Room 级联查询）。
     *
     * 计划内容或训练日完成标记变化时重新发射；
     * 未设置激活 ID 或计划已被删除时发射 null。
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val activePlan: Flow<WorkoutPlan?> = activePlanId.flatMapLatest { id ->
        if (id == null) {
            flowOf(null)
        } else {
            workoutPlanDao.getPlanByIdWithDetailsFlow(id).map { it?.toModel() }
        }
    }
}
