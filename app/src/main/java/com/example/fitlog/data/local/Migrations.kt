package com.example.fitlog.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * AppDatabase 的 Migration 清单（按版本递增排列）。
 *
 * ## 变更流程（强制）
 *
 * 1. 修改任一 @Entity / @Database 结构后，**必须**递增 [AppDatabase] 的 `version`；
 * 2. 在本文件新增 `Migration(from, to)` 并实现 SQL，加入 [ALL_MIGRATIONS]；
 * 3. 编译生成 `app/schemas/.../<version>.json` 并提交到版本库（schema 历史的事实源）。
 *
 * 项目已移除 `fallbackToDestructiveMigration()`：训练历史是不可再生的用户资产，
 * 任何 schema 变更不允许静默清库。缺 Migration 时 Room 将直接抛
 * `IllegalStateException: A migration from X to Y was required but not found`，
 * 这是有意为之的失败显性化。
 */
object Migrations {

    /**
     * 6 → 7：`workouts.sourceFileName` 建唯一索引，把数据导入的幂等性
     * 从应用层 check-then-insert 下沉到 schema（INSERT OR IGNORE 一并覆盖）。
     *
     * 建索引前先清理旧去重缺陷（TOCTOU）可能产生的同源重复行：
     * 同一 sourceFileName 只保留最早一条（MIN(id)），其动作/组明细一并显式删除——
     * 迁移期间外键级联不生效，遗留孤儿子行会在迁移后的外键校验中使升级失败。
     */
    private val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            val duplicateWorkoutIds =
                "SELECT id FROM workouts WHERE sourceFileName IS NOT NULL " +
                    "AND id NOT IN (" +
                    "SELECT MIN(id) FROM workouts WHERE sourceFileName IS NOT NULL " +
                    "GROUP BY sourceFileName)"
            db.execSQL(
                "DELETE FROM set_logs WHERE exerciseLogId IN " +
                    "(SELECT id FROM exercise_logs WHERE workoutId IN ($duplicateWorkoutIds))",
            )
            db.execSQL(
                "DELETE FROM exercise_logs WHERE workoutId IN ($duplicateWorkoutIds)",
            )
            db.execSQL("DELETE FROM workouts WHERE id IN ($duplicateWorkoutIds)")
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_workouts_sourceFileName` " +
                    "ON `workouts` (`sourceFileName`)",
            )
        }
    }

    /**
     * 全部已注册迁移，按版本升序。
     * `DatabaseModule` 经 `addMigrations(*ALL_MIGRATIONS)` 挂载。
     */
    val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_6_7)
}
