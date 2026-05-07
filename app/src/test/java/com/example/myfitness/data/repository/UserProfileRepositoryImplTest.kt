package com.example.myfitness.data.repository

import com.example.myfitness.data.local.dao.UserProfileDao
import com.example.myfitness.data.local.entity.UserProfileEntity
import com.example.myfitness.domain.model.Gender
import com.example.myfitness.domain.model.TrainingLevel
import com.example.myfitness.domain.model.UserProfile
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class UserProfileRepositoryImplTest {

    private lateinit var fakeDao: FakeUserProfileDao
    private lateinit var repository: UserProfileRepositoryImpl

    @Before
    fun setUp() {
        fakeDao = FakeUserProfileDao()
        repository = UserProfileRepositoryImpl(fakeDao)
    }

    @Test
    fun `getProfile returns mapped domain model`() = runTest {
        val entity = UserProfileEntity(
            id = 1L,
            name = "Test User",
            age = 25,
            gender = "MALE",
            height = 175.0f,
            weight = 70.0f,
        )
        fakeDao.insert(entity)

        val result = repository.getProfile(1L)

        assertEquals(
            UserProfile(
                id = 1L,
                name = "Test User",
                age = 25,
                gender = Gender.MALE,
                height = 175.0f,
                weight = 70.0f,
                trainingLevel = TrainingLevel(exercises = emptyMap()),
            ),
            result,
        )
    }

    @Test
    fun `getProfile returns null when not found`() = runTest {
        assertNull(repository.getProfile(999L))
    }

    @Test
    fun `getProfile maps null gender to null`() = runTest {
        val entity = UserProfileEntity(
            id = 2L,
            name = "Anonymous",
            age = null,
            gender = null,
            height = null,
            weight = null,
        )
        fakeDao.insert(entity)

        val result = repository.getProfile(2L)

        assertEquals(null, result?.gender)
        assertEquals(null, result?.age)
        assertEquals(null, result?.height)
        assertEquals(null, result?.weight)
    }

    @Test
    fun `getProfile maps all genders correctly`() = runTest {
        Gender.entries.forEach { gender ->
            val id = gender.ordinal.toLong() + 10L
            val entity = UserProfileEntity(
                id = id,
                name = gender.name,
                age = null,
                gender = gender.name,
                height = null,
                weight = null,
            )
            fakeDao.insert(entity)

            val result = repository.getProfile(id)
            assertEquals(gender, result?.gender)
        }
    }

    @Test
    fun `saveProfile inserts entity`() = runTest {
        val profile = UserProfile(
            id = 0L,
            name = "New User",
            age = 30,
            gender = Gender.FEMALE,
            height = 160.0f,
            weight = 55.0f,
            trainingLevel = TrainingLevel(exercises = emptyMap()),
        )

        repository.saveProfile(profile)

        val stored = fakeDao.getById(0L)
        assertEquals("New User", stored?.name)
        assertEquals(30, stored?.age)
        assertEquals("FEMALE", stored?.gender)
        assertEquals(160.0f, stored?.height)
        assertEquals(55.0f, stored?.weight)
    }

    @Test
    fun `updateProfile updates entity`() = runTest {
        val original = UserProfileEntity(
            id = 5L,
            name = "Old Name",
            age = 20,
            gender = "MALE",
            height = 170.0f,
            weight = 65.0f,
        )
        fakeDao.insert(original)

        val updated = UserProfile(
            id = 5L,
            name = "New Name",
            age = 21,
            gender = Gender.OTHER,
            height = 171.0f,
            weight = 66.0f,
            trainingLevel = TrainingLevel(exercises = emptyMap()),
        )
        repository.updateProfile(updated)

        val stored = fakeDao.getById(5L)
        assertEquals("New Name", stored?.name)
        assertEquals(21, stored?.age)
        assertEquals("OTHER", stored?.gender)
        assertEquals(171.0f, stored?.height)
        assertEquals(66.0f, stored?.weight)
    }

    @Test
    fun `saveProfile strips trainingLevel from entity`() = runTest {
        val profile = UserProfile(
            id = 0L,
            name = "User",
            age = null,
            gender = null,
            height = null,
            weight = null,
            trainingLevel = TrainingLevel(
                exercises = mapOf(
                    "squat" to com.example.myfitness.domain.model.ExerciseTrainingLevel(
                        estimatedOneRMKg = 100.0,
                        relativeStrength = 1.5,
                        bestVolumeLoadKg = 5000.0,
                    ),
                ),
            ),
        )

        repository.saveProfile(profile)

        val stored = fakeDao.getById(0L)
        assertEquals("User", stored?.name)
        // TrainingLevel is not persisted in MVP stage
    }

    private class FakeUserProfileDao : UserProfileDao {
        private val storage = mutableListOf<UserProfileEntity>()

        override suspend fun insert(userProfileEntity: UserProfileEntity) {
            storage.add(userProfileEntity)
        }

        override suspend fun update(userProfileEntity: UserProfileEntity) {
            val index = storage.indexOfFirst { it.id == userProfileEntity.id }
            if (index != -1) {
                storage[index] = userProfileEntity
            }
        }

        override suspend fun delete(userProfileEntity: UserProfileEntity) {
            storage.removeAll { it.id == userProfileEntity.id }
        }

        override suspend fun getById(id: Long): UserProfileEntity? {
            return storage.find { it.id == id }
        }
    }
}
