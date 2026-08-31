package com.example.fitlog.di

import com.example.fitlog.feature.reminder.ReminderScheduler
import com.example.fitlog.feature.reminder.WorkManagerReminderScheduler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 训练提醒的 Hilt 绑定：面向 [ReminderScheduler] 接口编程，
 * 生产绑定到 WorkManager 实现（测试注入记录式替身）。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ReminderModule {

    /** 绑定调度器实现为单例。 */
    @Binds
    @Singleton
    abstract fun bindReminderScheduler(impl: WorkManagerReminderScheduler): ReminderScheduler
}
