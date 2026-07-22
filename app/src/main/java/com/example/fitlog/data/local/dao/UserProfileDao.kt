package com.example.fitlog.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.fitlog.data.local.entity.UserProfileEntity

/**
 * 用户资料（[UserProfileEntity]）的数据访问对象。
 */
@Dao
interface UserProfileDao {
    /**
     * 插入一条用户资料，若主键冲突则忽略。
     *
     * @param userProfileEntity 待插入的实体
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(userProfileEntity: UserProfileEntity)

    /**
     * 更新已有用户资料。
     *
     * @param userProfileEntity 待更新的实体
     */
    @Update
    suspend fun update(userProfileEntity: UserProfileEntity)

    /**
     * 删除指定用户资料。
     *
     * @param userProfileEntity 待删除的实体
     */
    @Delete
    suspend fun delete(userProfileEntity: UserProfileEntity)

    /**
     * 根据 ID 查询用户资料。
     *
     * @param id 用户资料主键
     * @return 匹配的记录，若不存在则返回 null
     */
    @Query("SELECT * FROM user_profiles WHERE id = :id")
    suspend fun getById(id: Long): UserProfileEntity?

    /**
     * 查询首条用户资料。
     *
     * 本应用为单用户个人 App，表里至多一条记录。
     *
     * @return 首条记录，若不存在则返回 null
     */
    @Query("SELECT * FROM user_profiles LIMIT 1")
    suspend fun getFirst(): UserProfileEntity?
}
