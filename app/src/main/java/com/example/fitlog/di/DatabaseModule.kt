package com.example.fitlog.di

import com.example.fitlog.data.local.dao.AgentStepDao
import com.example.fitlog.data.local.dao.AIProviderConfigDao
import com.example.fitlog.data.local.dao.BodyMetricDao
import com.example.fitlog.data.local.dao.ChatMessageDao
import com.example.fitlog.data.local.dao.ExerciseDao
import com.example.fitlog.data.local.dao.ExerciseLogDao
import com.example.fitlog.data.local.dao.SetLogDao
import com.example.fitlog.data.local.dao.UserProfileDao
import com.example.fitlog.data.local.dao.WorkoutDao
import com.example.fitlog.data.local.dao.WorkoutPlanDao
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.example.fitlog.data.local.AppDatabase
import com.example.fitlog.data.local.Migrations
import com.example.fitlog.data.repository.AppearanceSource
import com.example.fitlog.data.repository.UserPreferencesRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 提供数据库、DAO 及 Repository 绑定的 Hilt Module。
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "fitlog.db",
        )
            .addMigrations(*Migrations.ALL_MIGRATIONS)
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideUserProfileDao(database: AppDatabase): UserProfileDao {
        return database.userProfileDao()
    }

    @Provides
    fun provideWorkoutDao(database: AppDatabase): WorkoutDao {
        return database.workoutDao()
    }

    @Provides
    fun provideExerciseLogDao(database: AppDatabase): ExerciseLogDao {
        return database.exerciseLogDao()
    }

    @Provides
    fun provideSetLogDao(database: AppDatabase): SetLogDao {
        return database.setLogDao()
    }

    @Provides
    fun provideAIProviderConfigDao(database: AppDatabase): AIProviderConfigDao {
        return database.aiProviderConfigDao()
    }

    @Provides
    fun provideWorkoutPlanDao(database: AppDatabase): WorkoutPlanDao {
        return database.workoutPlanDao()
    }

    @Provides
    fun provideExerciseDao(database: AppDatabase): ExerciseDao {
        return database.exerciseDao()
    }

    @Provides
    fun provideBodyMetricDao(database: AppDatabase): BodyMetricDao {
        return database.bodyMetricDao()
    }

    @Provides
    fun provideChatMessageDao(database: AppDatabase): ChatMessageDao {
        return database.chatMessageDao()
    }

    @Provides
    fun provideAgentStepDao(database: AppDatabase): AgentStepDao {
        return database.agentStepDao()
    }

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
        // 损坏容错：默认 handler 会把 CorruptionException 原样抛给每一条
        // dataStore.data 流（themeMode/activePlanId/activeProviderId/种子版本），
        // 任一消费者漏加 catch 就会击穿订阅链崩溃。替换为空偏好降级——
        // 损失的只是可重设的偏好（主题/激活计划/激活服务商），换启动稳定性值得
        return PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        ) {
            context.preferencesDataStoreFile("fitLog_prefs")
        }
    }

    /** 外观偏好只读源：MainViewModel 面向该接口，测试可注入替身。 */
    @Provides
    @Singleton
    fun provideAppearanceSource(
        repository: UserPreferencesRepository,
    ): AppearanceSource = repository
}
