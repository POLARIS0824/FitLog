package com.example.fitlog.data.repository

import com.example.fitlog.data.local.dao.UserProfileDao
import com.example.fitlog.data.mapper.toEntity
import com.example.fitlog.data.mapper.toModel
import com.example.fitlog.model.user.UserProfile
import javax.inject.Inject

/**
 * 用户资料仓库
 * 使用 UserProfileDao，实现对用户身高、体重、年龄、训练目标等基本个人资料的增删改查
 */
class UserProfileRepository @Inject constructor(
    private val userProfileDao: UserProfileDao
) {
    /**
     * 插入用户资料。
     *
     * @return 新行的主键，调用方应回填以避免后续保存误走 insert 产生重复记录
     */
    suspend fun insert(userProfile: UserProfile): Long = userProfileDao.insert(userProfile.toEntity())

    suspend fun update(userProfile: UserProfile) = userProfileDao.update(userProfile.toEntity())

    suspend fun getById(id: Long) = userProfileDao.getById(id)?.toModel()

    /**
     * 查询首条用户资料（单用户 App，至多一条）。
     */
    suspend fun getFirst() = userProfileDao.getFirst()?.toModel()
}