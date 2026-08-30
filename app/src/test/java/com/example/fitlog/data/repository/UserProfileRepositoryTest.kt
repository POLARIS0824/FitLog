package com.example.fitlog.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.fitlog.data.local.AppDatabase
import com.example.fitlog.model.user.Gender
import com.example.fitlog.model.user.TrainingGoal
import com.example.fitlog.model.user.UserProfile
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 用户资料仓库 [UserProfileRepository] 的单元测试。
 * 使用 Robolectric 在 JVM 环境下进行用户身高、体重、年龄、目标等基本个人资料的 CRUD 验证。
 */
@RunWith(RobolectricTestRunner::class)
class UserProfileRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: UserProfileRepository

    /**
     * 初始化内存 Room 数据库和 UserProfileRepository 实例。
     */
    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = UserProfileRepository(db.userProfileDao())
    }

    /**
     * 测试后关闭数据库。
     */
    @After
    fun closeDb() {
        db.close()
    }

    /**
     * 测试用户资料的插入与获取。
     */
    @Test
    fun testInsertAndGetById() = runTest {
        val profile = UserProfile(
            id = 1L,
            name = "张三",
            age = 25,
            gender = Gender.MALE,
            height = 175f,
            weight = 70f,
            trainingGoal = TrainingGoal.STRENGTH
        )

        repository.insert(profile)

        val fetched = repository.getById(1L)
        assertNotNull(fetched)
        assertEquals("张三", fetched?.name)
        assertEquals(25, fetched?.age)
        assertEquals(Gender.MALE, fetched?.gender)
        assertEquals(175f, fetched?.height)
        assertEquals(70f, fetched?.weight)
        assertEquals(TrainingGoal.STRENGTH, fetched?.trainingGoal)
    }

    /**
     * 测试用户资料的更新。
     */
    @Test
    fun testUpdate() = runTest {
        val profile = UserProfile(
            id = 1L,
            name = "张三",
            age = 25,
            gender = Gender.MALE,
            height = 175f,
            weight = 70f,
            trainingGoal = TrainingGoal.STRENGTH
        )
        repository.insert(profile)

        val updated = profile.copy(name = "李四", weight = 72f, trainingGoal = TrainingGoal.HYPERTROPHY)
        repository.update(updated)

        val fetched = repository.getById(1L)
        assertNotNull(fetched)
        assertEquals("李四", fetched?.name)
        assertEquals(72f, fetched?.weight)
        assertEquals(TrainingGoal.HYPERTROPHY, fetched?.trainingGoal)
    }
}
