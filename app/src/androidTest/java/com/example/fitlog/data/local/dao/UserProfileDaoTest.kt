package com.example.fitlog.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.fitlog.data.local.AppDatabase
import com.example.fitlog.data.local.entity.UserProfileEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [UserProfileDao] 的仪器化测试。
 *
 * 在真实 SQLite 上验证单用户资料的 CRUD 与 getFirst 语义。
 */
@RunWith(AndroidJUnit4::class)
class UserProfileDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: UserProfileDao

    /**
     * 创建内存数据库。
     */
    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        dao = db.userProfileDao()
    }

    /**
     * 关闭数据库。
     */
    @After
    fun closeDb() {
        db.close()
    }

    private fun profile(id: Long = 0, name: String = "张三") = UserProfileEntity(
        id = id,
        name = name,
        age = 25,
        gender = "MALE",
        height = 175f,
        weight = 70f,
        trainingGoal = "STRENGTH",
    )

    /**
     * 测试空表时 getFirst 返回 null。
     */
    @Test
    fun getFirst_emptyTable_returnsNull() = runTest {
        assertNull(dao.getFirst())
    }

    /**
     * 测试插入后主键自增且可按 ID 查询。
     */
    @Test
    fun insert_autoGeneratesId() = runTest {
        dao.insert(profile())

        val fetched = dao.getById(1L)
        assertNotNull(fetched)
        assertEquals("张三", fetched?.name)
        assertEquals(25, fetched?.age)
        assertEquals("MALE", fetched?.gender)
    }

    /**
     * 测试相同 ID 插入时替换（REPLACE）原记录。
     *
     * 注意：DAO 的 KDoc 注释写的是"冲突则忽略"，
     * 但注解实际配置为 [androidx.room.OnConflictStrategy.REPLACE]，以注解行为为准。
     */
    @Test
    fun insertDuplicateId_replaces() = runTest {
        dao.insert(profile(id = 1L, name = "张三"))
        dao.insert(profile(id = 1L, name = "李四"))

        assertEquals("李四", dao.getById(1L)?.name)
    }

    /**
     * 测试 getFirst 返回表中单条记录（单用户 App 语义）。
     */
    @Test
    fun getFirst_returnsSingleRecord() = runTest {
        dao.insert(profile(name = "张三"))

        val first = dao.getFirst()
        assertNotNull(first)
        assertEquals("张三", first?.name)
    }

    /**
     * 测试更新与删除。
     */
    @Test
    fun updateAndDelete() = runTest {
        dao.insert(profile(id = 1L, name = "张三"))

        dao.update(profile(id = 1L, name = "张三丰"))
        assertEquals("张三丰", dao.getById(1L)?.name)

        dao.delete(profile(id = 1L, name = "张三丰"))
        assertNull(dao.getById(1L))
        assertNull(dao.getFirst())
    }
}
