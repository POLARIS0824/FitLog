package com.example.myfitness.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.myfitness.data.local.dao.AIProviderConfigDao
import com.example.myfitness.data.local.dao.ExerciseLogDao
import com.example.myfitness.data.local.dao.SetLogDao
import com.example.myfitness.data.local.dao.UserProfileDao
import com.example.myfitness.data.local.dao.WorkoutDao
import com.example.myfitness.data.local.entity.AIProviderConfigEntity
import com.example.myfitness.data.local.entity.ExerciseLogEntity
import com.example.myfitness.data.local.entity.SetLogEntity
import com.example.myfitness.data.local.entity.UserProfileEntity
import com.example.myfitness.data.local.entity.WorkoutEntity

/**
 * Room 数据库入口，管理 [UserProfileEntity]、[WorkoutEntity]、[ExerciseLogEntity]、[SetLogEntity]
 * 与 [AIProviderConfigEntity] 五张表。
 */
@Database(
    entities = [
        UserProfileEntity::class,
        WorkoutEntity::class,
        ExerciseLogEntity::class,
        SetLogEntity::class,
        AIProviderConfigEntity::class,
    ],
    version = 4,
    exportSchema = false,
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

    companion object {
        /**
         * 从版本 1 迁移到版本 2：
         * 删除旧的 `daily_check_ins` 表，创建新的关系型表结构。
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS daily_check_ins")

                db.execSQL(
                    """
                    CREATE TABLE workouts (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        date TEXT NOT NULL,
                        sourceFileName TEXT,
                        rawContent TEXT NOT NULL
                    )
                    """.trimIndent(),
                )

                db.execSQL(
                    """
                    CREATE TABLE exercise_logs (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        workoutId INTEGER NOT NULL,
                        name TEXT NOT NULL,
                        sortOrder INTEGER NOT NULL,
                        FOREIGN KEY(workoutId) REFERENCES workouts(id) ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )

                db.execSQL(
                    """
                    CREATE TABLE set_logs (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        exerciseLogId INTEGER NOT NULL,
                        setNumber INTEGER NOT NULL,
                        weightKg REAL NOT NULL,
                        reps INTEGER NOT NULL,
                        FOREIGN KEY(exerciseLogId) REFERENCES exercise_logs(id) ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )

                db.execSQL("CREATE INDEX index_exercise_logs_workoutId ON exercise_logs(workoutId)")
                db.execSQL("CREATE INDEX index_set_logs_exerciseLogId ON set_logs(exerciseLogId)")
            }
        }

        /**
         * 从版本 2 迁移到版本 3：
         * 创建 ai_provider_configs 表，用于存储加密的 AI 提供商配置。
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE ai_provider_configs (
                        id TEXT PRIMARY KEY NOT NULL,
                        name TEXT NOT NULL,
                        baseUrl TEXT NOT NULL,
                        encryptedApiKey TEXT NOT NULL,
                        model TEXT NOT NULL,
                        isPreset INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }

        /**
         * 从版本 3 迁移到版本 4：
         * 为 ai_provider_configs 表添加 type、custom_endpoint 和 api_version 列。
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE ai_provider_configs ADD COLUMN type TEXT NOT NULL DEFAULT 'CUSTOM'")
                db.execSQL("ALTER TABLE ai_provider_configs ADD COLUMN custom_endpoint TEXT")
                db.execSQL("ALTER TABLE ai_provider_configs ADD COLUMN api_version TEXT")
            }
        }
    }
}
