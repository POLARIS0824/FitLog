package com.example.fitlog.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.fitlog.data.local.AppDatabase
import com.example.fitlog.data.local.entity.AIProviderConfigEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [AIProviderConfigDao] 的仪器化测试。
 *
 * 在真实 SQLite 上验证 AI 配置的 CRUD、REPLACE 冲突策略与缓存模型列表更新。
 * 注意：这里直接操作 Entity（密文由调用方/Mapper 负责），DAO 层不感知加解密。
 */
@RunWith(AndroidJUnit4::class)
class AIProviderConfigDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: AIProviderConfigDao

    /**
     * 创建内存数据库。
     */
    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        dao = db.aiProviderConfigDao()
    }

    /**
     * 关闭数据库。
     */
    @After
    fun closeDb() {
        db.close()
    }

    private fun entity(
        id: String,
        name: String = "配置-$id",
        cachedModels: String? = null,
    ) = AIProviderConfigEntity(
        id = id,
        name = name,
        type = "CUSTOM",
        baseUrl = "https://example.com",
        encryptedApiKey = "ciphertext-$id",
        model = "model-1",
        customEndpoint = null,
        apiVersion = null,
        isPreset = false,
        cachedModels = cachedModels,
    )

    /**
     * 测试插入与按 ID 查询。
     */
    @Test
    fun insertAndGetById() = runTest {
        dao.insert(entity("OPENAI"))

        val fetched = dao.getById("OPENAI")
        assertNotNull(fetched)
        assertEquals("配置-OPENAI", fetched?.name)
        assertEquals("ciphertext-OPENAI", fetched?.encryptedApiKey)
    }

    /**
     * 测试相同 ID 重复插入时替换（REPLACE）原记录。
     */
    @Test
    fun insertDuplicateId_replaces() = runTest {
        dao.insert(entity("OPENAI", name = "旧配置"))
        dao.insert(entity("OPENAI", name = "新配置"))

        assertEquals("新配置", dao.getById("OPENAI")?.name)
        assertEquals(1, dao.getAll().first().size)
    }

    /**
     * 测试更新与删除。
     */
    @Test
    fun updateAndDelete() = runTest {
        dao.insert(entity("OPENAI"))

        dao.update(entity("OPENAI", name = "更新后"))
        assertEquals("更新后", dao.getById("OPENAI")?.name)

        dao.delete(entity("OPENAI", name = "更新后"))
        assertNull(dao.getById("OPENAI"))
    }

    /**
     * 测试 updateCachedModels 只更新缓存字段，其余字段保持不变。
     */
    @Test
    fun updateCachedModels_updatesOnlyCacheField() = runTest {
        dao.insert(entity("OPENAI"))

        dao.updateCachedModels("OPENAI", "gpt-5.6-sol,gpt-5.5")
        var fetched = dao.getById("OPENAI")
        assertEquals("gpt-5.6-sol,gpt-5.5", fetched?.cachedModels)
        assertEquals("配置-OPENAI", fetched?.name)
        assertEquals("ciphertext-OPENAI", fetched?.encryptedApiKey)

        dao.updateCachedModels("OPENAI", null)
        fetched = dao.getById("OPENAI")
        assertNull(fetched?.cachedModels)
    }

    /**
     * 测试 getAll 以 Flow 形式返回全部配置。
     */
    @Test
    fun getAll_returnsAllViaFlow() = runTest {
        dao.insert(entity("OPENAI"))
        dao.insert(entity("DEEPSEEK"))

        val all = dao.getAll().first()
        assertEquals(2, all.size)
        assertEquals(setOf("OPENAI", "DEEPSEEK"), all.map { it.id }.toSet())
    }
}
