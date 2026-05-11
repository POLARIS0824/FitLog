package com.example.fitlog.data.repository

import com.example.fitlog.data.local.dao.UserProfileDao
import com.example.fitlog.data.local.entity.UserProfileEntity
import com.example.fitlog.domain.model.user.Gender
import com.example.fitlog.domain.model.user.TrainingGoal
import com.example.fitlog.domain.model.user.TrainingLevel
import com.example.fitlog.domain.model.user.UserProfile
import com.example.fitlog.domain.repository.UserProfileRepository
import javax.inject.Inject

/**
 * [UserProfileRepository] 的 Room 实现。
 */
class UserProfileRepositoryImpl @Inject constructor(
    private val userProfileDao: UserProfileDao,
) : UserProfileRepository {

    /**
     * 根据 ID 查询用户资料。
     *
     * @param id 用户资料主键
     * @return [UserProfile]，若不存在则返回 null
     */
    override suspend fun getProfile(id: Long): UserProfile? {
        val entity = userProfileDao.getById(id) ?: return null
        return entity.toDomain()
    }

    /**
     * 保存新的用户资料。
     *
     * @param profile 待保存的用户资料
     */
    override suspend fun saveProfile(profile: UserProfile) {
        userProfileDao.insert(profile.toEntity())
    }

    /**
     * 更新已有用户资料。
     *
     * @param profile 待更新的用户资料
     */
    override suspend fun updateProfile(profile: UserProfile) {
        userProfileDao.update(profile.toEntity())
    }

    /**
     * 将 [UserProfileEntity] 转换为 domain 模型 [UserProfile]。
     */
    private fun UserProfileEntity.toDomain(): UserProfile {
        return UserProfile(
            id = id,
            name = name,
            age = age,
            gender = gender?.let { Gender.valueOf(it) },
            height = height,
            weight = weight,
            trainingLevel = TrainingLevel(exercises = emptyMap()),
            trainingGoal = trainingGoal?.let { TrainingGoal.valueOf(it) },
        )
    }

    /**
     * 将 domain 模型 [UserProfile] 转换为数据库实体 [UserProfileEntity]。
     */
    private fun UserProfile.toEntity(): UserProfileEntity {
        return UserProfileEntity(
            id = id,
            name = name,
            age = age,
            gender = gender?.name,
            height = height,
            weight = weight,
            trainingGoal = trainingGoal?.name,
        )
    }
}
