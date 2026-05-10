package com.example.fitlog.domain.repository

import com.example.fitlog.domain.model.UserProfile

interface UserProfileRepository {
    suspend fun getProfile(id: Long): UserProfile?

    suspend fun saveProfile(profile: UserProfile)

    suspend fun updateProfile(profile: UserProfile)
}
