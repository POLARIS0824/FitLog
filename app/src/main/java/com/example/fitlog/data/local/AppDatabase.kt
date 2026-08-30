package com.example.fitlog.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.fitlog.data.local.dao.AIProviderConfigDao
import com.example.fitlog.data.local.dao.BodyMetricDao
import com.example.fitlog.data.local.dao.ExerciseDao
import com.example.fitlog.data.local.dao.ExerciseLogDao
import com.example.fitlog.data.local.dao.SetLogDao
import com.example.fitlog.data.local.dao.UserProfileDao
import com.example.fitlog.data.local.dao.WorkoutDao
import com.example.fitlog.data.local.dao.WorkoutPlanDao
import com.example.fitlog.data.local.entity.AIProviderConfigEntity
import com.example.fitlog.data.local.entity.BodyMetricEntity
import com.example.fitlog.data.local.entity.ExerciseEntity
import com.example.fitlog.data.local.entity.workout.ExerciseLogEntity
import com.example.fitlog.data.local.entity.plan.PlannedSessionEntity
import com.example.fitlog.data.local.entity.workout.SetLogEntity
import com.example.fitlog.data.local.entity.UserProfileEntity
import com.example.fitlog.data.local.entity.workout.WorkoutEntity
import com.example.fitlog.data.local.entity.plan.WorkoutPlanEntity

/**
 * Room 数据库入口，管理以下表：
 * [UserProfileEntity]、[WorkoutEntity]、[ExerciseLogEntity]、[SetLogEntity]、
 * [AIProviderConfigEntity]、[WorkoutPlanEntity]、[PlannedSessionEntity]、
 * [BodyMetricEntity]。
 */
@TypeConverters(ExerciseConverters::class, LocalDateConverters::class, PlanConverters::class)
@Database(
    entities = [
        UserProfileEntity::class,
        WorkoutEntity::class,
        ExerciseLogEntity::class,
        ExerciseEntity::class,
        SetLogEntity::class,
        AIProviderConfigEntity::class,
        WorkoutPlanEntity::class,
        PlannedSessionEntity::class,
        BodyMetricEntity::class,
    ],
    version = 7,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    /**
     * 提供 [UserProfileDao] 实例。
     */
    abstract fun userProfileDao(): UserProfileDao

    /**
     * 提供 [WorkoutDao] 实例。
     */
    abstract fun workoutDao(): WorkoutDao

    /**
     * 提供 [ExerciseLogDao] 实例。
     */
    abstract fun exerciseLogDao(): ExerciseLogDao

    /**
     * 提供 [SetLogDao] 实例。
     */
    abstract fun setLogDao(): SetLogDao

    /**
     * 提供 [AIProviderConfigDao] 实例。
     */
    abstract fun aiProviderConfigDao(): AIProviderConfigDao

    /**
     * 提供 [WorkoutPlanDao] 实例。
     */
    abstract fun workoutPlanDao(): WorkoutPlanDao

    /**
     * 提供 [ExerciseDao] 实例。
     */
    abstract fun exerciseDao(): ExerciseDao

    /**
     * 提供 [BodyMetricDao] 实例。
     */
    abstract fun bodyMetricDao(): BodyMetricDao
}
