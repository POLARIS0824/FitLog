package com.example.myfitness.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.example.myfitness.data.local.AppDatabase
import com.example.myfitness.data.local.dao.AIProviderConfigDao
import com.example.myfitness.data.local.dao.ExerciseLogDao
import com.example.myfitness.data.local.dao.SetLogDao
import com.example.myfitness.data.local.dao.UserProfileDao
import com.example.myfitness.data.local.dao.WorkoutDao
import com.example.myfitness.data.repository.AIProviderConfigRepositoryImpl
import com.example.myfitness.data.repository.UserProfileRepositoryImpl
import com.example.myfitness.data.repository.WorkoutRepositoryImpl
import com.example.myfitness.domain.repository.AIProviderConfigRepository
import com.example.myfitness.domain.repository.UserProfileRepository
import com.example.myfitness.domain.repository.WorkoutRepository
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
            "myfitness.db",
        ).addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
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
}
