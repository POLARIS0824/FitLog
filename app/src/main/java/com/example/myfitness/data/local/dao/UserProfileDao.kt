package com.example.myfitness.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.myfitness.data.local.entity.UserProfileEntity

@Dao
interface UserProfileDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(userProfileEntity: UserProfileEntity)

    @Update
    suspend fun update(userProfileEntity: UserProfileEntity)

    @Delete
    suspend fun delete(userProfileEntity: UserProfileEntity)

    // TODO Query
}