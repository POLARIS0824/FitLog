package com.example.myfitness.domain.repository

import com.example.myfitness.domain.model.UserProfile

interface UserProfileRepository {
    suspend fun getProfile(id: Long): UserProfile?

    suspend fun saveProfile(profile: UserProfile)

    suspend fun updateProfile(profile: UserProfile)
}
