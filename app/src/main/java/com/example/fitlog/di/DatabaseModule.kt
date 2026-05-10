package com.example.fitlog.di

import com.example.fitlog.data.local.dao.AIProviderConfigDao
import com.example.fitlog.data.local.dao.ExerciseDao
import com.example.fitlog.data.local.dao.ExerciseLogDao
import com.example.fitlog.data.local.dao.SetLogDao
import com.example.fitlog.data.local.dao.UserProfileDao
import com.example.fitlog.data.local.dao.WorkoutDao
import com.example.fitlog.data.local.dao.WorkoutPlanDao
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.example.fitlog.data.local.AppDatabase
import com.example.fitlog.data.repository.AIChatRepositoryImpl
import com.example.fitlog.data.repository.AIProviderConfigRepositoryImpl
import com.example.fitlog.data.repository.UserProfileRepositoryImpl
import com.example.fitlog.data.repository.WorkoutPlanRepositoryImpl
import com.example.fitlog.data.repository.WorkoutRepositoryImpl
import com.example.fitlog.domain.repository.AIChatRepository
import com.example.fitlog.domain.repository.AIProviderConfigRepository
import com.example.fitlog.domain.repository.UserProfileRepository
import com.example.fitlog.domain.repository.WorkoutPlanRepository
import com.example.fitlog.domain.repository.WorkoutRepository
import dagger.Binds
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
        ).fallbackToDestructiveMigration()
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
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
        return PreferenceDataStoreFactory.create {
            context.preferencesDataStoreFile("myfitness_prefs")
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindUserProfileRepository(
        impl: UserProfileRepositoryImpl,
    ): UserProfileRepository

    @Binds
    abstract fun bindWorkoutRepository(
        impl: WorkoutRepositoryImpl,
    ): WorkoutRepository

    @Binds
    abstract fun bindAIProviderConfigRepository(
        impl: AIProviderConfigRepositoryImpl,
    ): AIProviderConfigRepository

    @Binds
    abstract fun bindAIChatRepository(
        impl: AIChatRepositoryImpl,
    ): AIChatRepository

    @Binds
    abstract fun bindWorkoutPlanRepository(
        impl: WorkoutPlanRepositoryImpl,
    ): WorkoutPlanRepository
}
