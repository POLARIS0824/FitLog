package com.example.myfitness.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.myfitness.data.local.dao.DailyCheckInDao
import com.example.myfitness.data.local.dao.UserProfileDao
import com.example.myfitness.data.local.entity.DailyCheckInEntity
import com.example.myfitness.data.local.entity.UserProfileEntity

/**
 * Room 数据库入口，管理 [UserProfileEntity] 与 [DailyCheckInEntity] 两张表。
 */
@Database(
    entities = [UserProfileEntity::class, DailyCheckInEntity::class],
    version = 1,
)
abstract class AppDatabase : RoomDatabase() {
    /**
     * 提供 [UserProfileDao] 实例。
     */
    abstract fun userProfileDao(): UserProfileDao

    /**
     * 提供 [DailyCheckInDao] 实例。
     */
    abstract fun dailyCheckInDao(): DailyCheckInDao
}
