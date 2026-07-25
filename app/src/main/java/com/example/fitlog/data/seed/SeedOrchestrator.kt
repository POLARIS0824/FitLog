package com.example.fitlog.data.seed

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 应用级种子导入编排器（进程单例）。
 *
 * 集中持有"种子是否完成"的共享状态 [completed]，分离两类启动等待：
 *
 * - **Splash 只等外观偏好**（DataStore 一读即放，几十毫秒）；
 * - **首装/升级的种子耗时由 Today 首屏加载条承接**——
 *   [com.example.fitlog.feature.today.TodayViewModel] 等待 [completed]
 *   才允许 uiState 首发，避免种子在 UI 收集期间写库导致内容中途翻转。
 *
 * 触发方：MainViewModel 启动时调用 [seedIfNeeded]（幂等，重复调用短路）。
 */
@Singleton
class SeedOrchestrator @Inject constructor(
    private val exerciseSeeder: ExerciseSeeder,
    private val workoutPlanSeeder: WorkoutPlanSeeder,
) {

    private val _completed = MutableStateFlow(false)

    /** 种子导入是否已完成（失败同样置位，fail-open 防卡死首帧）。 */
    val completed: StateFlow<Boolean> = _completed

    private val mutex = Mutex()
    private var ran = false

    /**
     * 触发种子导入：先动作库、后预置计划（计划动作引用动作库 key）。
     * 进程内幂等；异常经 finally 放行——宁可内容中途变，不能永久卡加载。
     */
    suspend fun seedIfNeeded() {
        if (ran) return
        mutex.withLock {
            if (ran) return
            try {
                exerciseSeeder.seedIfNeeded()
                workoutPlanSeeder.seedIfNeeded()
            } finally {
                ran = true
                _completed.value = true
            }
        }
    }
}
