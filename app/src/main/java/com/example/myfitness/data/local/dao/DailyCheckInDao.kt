package com.example.myfitness.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Update
import com.example.myfitness.data.local.entity.DailyCheckInEntity

@Dao
interface DailyCheckInDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(dailyCheckInEntity: DailyCheckInEntity)

    @Update
    suspend fun update(dailyCheckInEntity: DailyCheckInEntity)

    @Delete
    suspend fun delete(dailyCheckInEntity: DailyCheckInEntity)

    // TODO Query
}