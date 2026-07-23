package com.example.fitlog.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.fitlog.data.local.AppDatabase
import com.example.fitlog.model.ai.AIProviderConfig
import com.example.fitlog.model.ai.ProviderType
import com.example.fitlog.testing.createTestPreferencesDataStore
import com.example.fitlog.util.security.FakeAndroidKeyStoreProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * [AIProviderConfigRepository] 的单元测试。
 *
 * 使用 Robolectric + 内存 Room + 临时文件 DataStore + 模拟密钥库，
 * 验证配置 CRUD（含 apiKey 加解密桥接）与"当前激活配置"的 DataStore 管理逻辑。
 */
@RunWith(RobolectricTestRunner::class)
class AIProviderConfigRepositoryTest {

    /**
     * 每个测试方法使用独立的临时目录存放 DataStore 文件。
     */
    @get:Rule
    val tmpFolder = TemporaryFolder()

    private lateinit var db: AppDatabase
    private lateinit var repository: AIProviderConfigRepository

    /**
     * 初始化内存数据库、临时 DataStore、模拟密钥库与仓库实例。
     */
    @Before
    fun setUp() {
        FakeAndroidKeyStoreProvider.setup()

        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val dataStore = createTestPreferencesDataStore(tmpFolder.newFile("ai_prefs_test.preferences_pb"))
        repository = AIProviderConfigRepository(db.aiProviderConfigDao(), dataStore)
    }

    /**
     * 测试后关闭数据库。
     */
    @After
    fun tearDown() {
        db.close()
    }

    private fun config(
        id: String = "OPENAI",
        apiKey: String = "sk-secret-001",
        cachedModels: List<String> = emptyList(),
    ) = AIProviderConfig(
        id = id,
        name = "OpenAI",
        type = ProviderType.OPENAI,
        baseUrl = "https://api.openai.com",
        apiKey = apiKey,
        model = "gpt-5.6-sol",
        customEndpoint = null,
        apiVersion = null,
        isPreset = true,
        cachedModels = cachedModels,
    )

    // ──────────────────────────────────────
    // CRUD
    // ──────────────────────────────────────

    /**
     * 测试插入配置后可按 ID 读回，且 apiKey 已解密为明文。
     */
    @Test
    fun testInsertAndGetById_apiKeyDecrypted() = runTest {
        repository.insert(config(apiKey = "sk-secret-001"))

        val fetched = repository.getById("OPENAI")
        assertNotNull(fetched)
        assertEquals("sk-secret-001", fetched?.apiKey)
        assertEquals("https://api.openai.com", fetched?.baseUrl)
        assertEquals(ProviderType.OPENAI, fetched?.type)
    }

    /**
     * 测试数据库中实际存储的是密文（不经过 Repository 直接读 DAO 验证）。
     */
    @Test
    fun testInsert_storesCiphertextInDatabase() = runTest {
        repository.insert(config(apiKey = "sk-secret-001"))

        val entity = db.aiProviderConfigDao().getById("OPENAI")
        assertNotNull(entity)
        assertNotEquals("sk-secret-001", entity?.encryptedApiKey)
    }

    /**
     * 测试更新已有配置。
     */
    @Test
    fun testUpdate() = runTest {
        repository.insert(config())
        repository.update(config().copy(model = "gpt-5.5", baseUrl = "https://proxy.example.com"))

        val fetched = repository.getById("OPENAI")
        assertEquals("gpt-5.5", fetched?.model)
        assertEquals("https://proxy.example.com", fetched?.baseUrl)
    }

    /**
     * 测试 getAIProviders 返回全部配置（apiKey 均已解密）。
     */
    @Test
    fun testGetAIProviders_returnsAll() = runTest {
        repository.insert(config(id = "OPENAI"))
        repository.insert(config(id = "DEEPSEEK", apiKey = "sk-deepseek"))

        val all = repository.getAIProviders().first()
        assertEquals(2, all.size)
        assertEquals(setOf("OPENAI", "DEEPSEEK"), all.map { it.id }.toSet())
        assertEquals(setOf("sk-secret-001", "sk-deepseek"), all.map { it.apiKey }.toSet())
    }

    /**
     * 测试更新缓存模型列表：序列化为逗号分隔字符串存入数据库。
     */
    @Test
    fun testUpdateCachedModels() = runTest {
        repository.insert(config())
        repository.updateCachedModels("OPENAI", listOf("gpt-5.6-sol", "gpt-5.5"))

        // 经 DAO 直读验证底层存储格式
        assertEquals("gpt-5.6-sol,gpt-5.5", db.aiProviderConfigDao().getById("OPENAI")?.cachedModels)
        // 经 Repository 读回验证反序列化
        assertEquals(listOf("gpt-5.6-sol", "gpt-5.5"), repository.getById("OPENAI")?.cachedModels)
    }

    /**
     * 测试将缓存模型列表更新为空列表时存为 null。
     */
    @Test
    fun testUpdateCachedModels_emptyList_storedAsNull() = runTest {
        repository.insert(config(cachedModels = listOf("gpt-5.6-sol")))
        repository.updateCachedModels("OPENAI", emptyList())

        assertNull(db.aiProviderConfigDao().getById("OPENAI")?.cachedModels)
        assertEquals(emptyList<String>(), repository.getById("OPENAI")?.cachedModels)
    }

    // ──────────────────────────────────────
    // 激活管理
    // ──────────────────────────────────────

    /**
     * 测试初始状态下激活 ID 与激活配置均为 null。
     */
    @Test
    fun testActiveProvider_initiallyNull() = runTest {
        assertNull(repository.activeProviderId.first())
        assertNull(repository.activeProvider.first())
    }

    /**
     * 测试设置激活 ID 后，activeProvider 自动解析为完整配置（含解密 apiKey）。
     */
    @Test
    fun testSetActiveProviderId_activeProviderResolves() = runTest {
        repository.insert(config(apiKey = "sk-active"))
        repository.setActiveProviderId("OPENAI")

        assertEquals("OPENAI", repository.activeProviderId.first())

        val active = repository.activeProvider.first()
        assertNotNull(active)
        assertEquals("OPENAI", active?.id)
        assertEquals("sk-active", active?.apiKey)
    }

    /**
     * 测试激活 ID 指向不存在的配置时，activeProvider 返回 null（悬空引用容错）。
     */
    @Test
    fun testActiveProvider_danglingId_returnsNull() = runTest {
        repository.setActiveProviderId("GHOST")

        assertEquals("GHOST", repository.activeProviderId.first())
        assertNull(repository.activeProvider.first())
    }

    /**
     * 测试清除激活 ID。
     */
    @Test
    fun testClearActiveProviderId() = runTest {
        repository.setActiveProviderId("OPENAI")
        repository.clearActiveProviderId()

        assertNull(repository.activeProviderId.first())
    }

    /**
     * 测试删除"当前激活"的配置时，激活 ID 被一并清除。
     */
    @Test
    fun testDelete_activeConfig_clearsActiveId() = runTest {
        repository.insert(config())
        repository.setActiveProviderId("OPENAI")

        repository.delete(config())

        assertNull(repository.getById("OPENAI"))
        assertNull(repository.activeProviderId.first())
    }

    /**
     * 测试删除"非激活"的配置时，激活 ID 保持不变。
     */
    @Test
    fun testDelete_inactiveConfig_keepsActiveId() = runTest {
        repository.insert(config(id = "OPENAI"))
        repository.insert(config(id = "DEEPSEEK", apiKey = "sk-deepseek"))
        repository.setActiveProviderId("OPENAI")

        repository.delete(config(id = "DEEPSEEK", apiKey = "sk-deepseek"))

        assertNull(repository.getById("DEEPSEEK"))
        assertEquals("OPENAI", repository.activeProviderId.first())
    }

    /**
     * 测试删除不存在的激活配置后，getAIProviders 不再包含该配置。
     */
    @Test
    fun testDelete_removesFromProvidersFlow() = runTest {
        repository.insert(config())
        assertEquals(1, repository.getAIProviders().first().size)

        repository.delete(config())
        assertTrue(repository.getAIProviders().first().isEmpty())
    }
}
