package com.example.fitlog.data.local

import androidx.room.migration.Migration

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
     * 全部已注册迁移，按版本升序；当前 version=6 为基线，暂无迁移。
     * `DatabaseModule` 经 `addMigrations(*ALL_MIGRATIONS)` 挂载。
     */
    val ALL_MIGRATIONS: Array<Migration> = arrayOf()
}
