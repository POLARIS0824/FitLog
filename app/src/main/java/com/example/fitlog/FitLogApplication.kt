package com.example.fitlog

import android.app.Application
import com.example.fitlog.data.repository.UserPreferencesRepository
import com.example.fitlog.feature.reminder.ReminderScheduler
import com.example.fitlog.util.log.FitLog
import com.example.fitlog.util.log.FitLogBootstrap
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 应用入口，启用 Hilt 依赖注入。
 */
@HiltAndroidApp
class FitLogApplication : Application() {

    @Inject lateinit var userPreferencesRepository: UserPreferencesRepository

    @Inject lateinit var reminderScheduler: ReminderScheduler

    override fun onCreate() {
        // 日志子系统先于 Hilt 装配（attachBaseContext 已完成，filesDir 可用）：
        // 紧随其后的 DI 初始化若抛异常，也能被 CrashHandler 落盘
        FitLogBootstrap.init(this)
        super.onCreate()
        rescheduleReminderIfNeeded()
    }

    /**
     * 启动期提醒恢复排程：WorkManager 任务队列跨重启/升级持久化，但被用户
     * 强制停止（force-stop）后会被系统整队清除——不补排程的话提醒静默断档，
     * 直到用户再次开关提醒才恢复。
     *
     * 采用 [ReminderScheduler.recoverSchedule]（KEEP 语义）而非 [ReminderScheduler.schedule]（REPLACE 语义）：
     * 保留已经在途或即将到期的有效提醒，仅在无活跃任务时补排，避免在到期临界点启动时
     * 错误取消当天任务并推迟至次日，造成提醒漏发。
     */
    internal fun rescheduleReminderIfNeeded(
        scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    ) {
        scope.launch {
            runCatching {
                val enabled = userPreferencesRepository.reminderEnabled.first()
                val minutes = userPreferencesRepository.reminderMinutes.first()
                if (enabled) {
                    reminderScheduler.recoverSchedule(minutes)
                    FitLog.i("FitLogApplication", "启动恢复训练提醒：$minutes 分钟档")
                }
            }.onFailure {
                FitLog.w("FitLogApplication", "启动恢复提醒失败（下次启动重试）", it)
            }
        }
    }
}
