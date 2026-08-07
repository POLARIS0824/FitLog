package com.example.fitlog.data.mapper

import com.example.fitlog.data.local.entity.AIProviderConfigEntity
import com.example.fitlog.model.ai.AIProviderConfig
import com.example.fitlog.model.ai.ProviderType
import com.example.fitlog.util.security.FakeAndroidKeyStoreProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * [AIProviderConfigMapper] 的单元测试。
 *
 * 关键点：apiKey 在 Entity（密文）与 Model（明文）转换时经 [com.example.fitlog.util.security.KeystoreManager]
 * 加解密，因此使用 Robolectric + FakeAndroidKeyStoreProvider 在 JVM 上模拟硬件密钥库。
 */
@RunWith(RobolectricTestRunner::class)
class AIProviderConfigMapperTest {

    /**
     * 注册模拟的 AndroidKeyStore 提供者。
     */
    @Before
    fun setUp() {
        FakeAndroidKeyStoreProvider.setup()
    }

    private fun model(
        apiKey: String = "sk-plain-text",
        cachedModels: List<String> = emptyList(),
    ) = AIProviderConfig(
        id = "OPENAI",
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

    /**
     * 测试转 Entity 时 apiKey 被加密：密文不等于明文。
     */
    @Test
    fun testToEntity_apiKeyIsEncrypted() {
        val entity = model(apiKey = "sk-plain-text").toEntity()

        assertNotEquals("sk-plain-text", entity.encryptedApiKey)
        assertTrue(entity.encryptedApiKey.isNotBlank())
    }

    /**
     * 测试加解密往返：Model → Entity（加密）→ Model（解密）后 apiKey 还原。
     */
    @Test
    fun testRoundTrip_apiKeyRestored() {
        val original = model(apiKey = "sk-super-secret-12345")

        val restored = original.toEntity().toModel()

        assertEquals("sk-super-secret-12345", restored.apiKey)
    }

    /**
     * 测试 type 枚举与字符串的相互转换。
     */
    @Test
    fun testTypeConversion() {
        val entity = model().toEntity()
        assertEquals("OPENAI", entity.type)
        assertEquals(ProviderType.OPENAI, entity.toModel().type)
    }

    /**
     * 测试 type 为未知字符串时降级为 [ProviderType.CUSTOM]（不崩溃）。
     */
    @Test
    fun testToModel_unknownType_fallsBackToCustom() {
        val entity = AIProviderConfigEntity(
            id = "LEGACY",
            name = "Legacy",
            type = "SOME_OLD_PLATFORM",
            baseUrl = "https://example.com",
            encryptedApiKey = model().toEntity().encryptedApiKey,
            model = "m",
            customEndpoint = null,
            apiVersion = null,
            isPreset = false,
        )

        val restored = entity.toModel()

        assertEquals(ProviderType.CUSTOM, restored.type)
        assertEquals("Legacy", restored.name)
    }

    /**
     * 测试 Keystore 密钥丢失（如备份恢复到新设备）时读取配置不崩溃，
     * apiKey 降级为空字符串，其余字段（名称/类型/地址/模型）仍可展示。
     */
    @Test
    fun testToModel_keystoreKeyLost_degradesToEmptyApiKey() {
        // 先用当前密钥加密，得到一条“旧设备”上的密文记录
        val entity = model(apiKey = "sk-secret").toEntity()

        // 模拟换机恢复：清空 Fake KeyStore 中的密钥条目
        FakeAndroidKeyStoreProvider.entries.clear()

        // 读取不再崩溃，apiKey 降级为空，配置仍可展示
        val restored = entity.toModel()
        assertEquals("", restored.apiKey)
        assertEquals(ProviderType.OPENAI, restored.type)
        assertEquals("OpenAI", restored.name)
        assertEquals("https://api.openai.com", restored.baseUrl)
        assertEquals("gpt-5.6-sol", restored.model)
    }

    /**
     * 测试 cachedModels 列表序列化为逗号分隔字符串。
     */
    @Test
    fun testToEntity_cachedModelsJoinedWithComma() {
        val entity = model(cachedModels = listOf("gpt-5.6-sol", "gpt-5.5")).toEntity()
        assertEquals("gpt-5.6-sol,gpt-5.5", entity.cachedModels)
    }

    /**
     * 测试空 cachedModels 列表序列化为 null（而非空字符串）。
     */
    @Test
    fun testToEntity_emptyCachedModels_serializedAsNull() {
        val entity = model(cachedModels = emptyList()).toEntity()
        assertNull(entity.cachedModels)
    }

    /**
     * 测试 cachedModels 反序列化：null 与空白项的容错处理。
     */
    @Test
    fun testToModel_cachedModelsParsing() {
        fun entityWith(cached: String?) = AIProviderConfigEntity(
            id = "CUSTOM",
            name = "Custom",
            type = "CUSTOM",
            baseUrl = "https://example.com",
            encryptedApiKey = model().toEntity().encryptedApiKey,
            model = "m",
            customEndpoint = null,
            apiVersion = null,
            isPreset = false,
            cachedModels = cached,
        )

        assertEquals(emptyList<String>(), entityWith(null).toModel().cachedModels)
        assertEquals(listOf("a", "b"), entityWith("a,b").toModel().cachedModels)
        // 空白项（如尾部多余逗号产生的空串）被过滤
        assertEquals(listOf("a", "b"), entityWith("a,,b,").toModel().cachedModels)
    }

    /**
     * 测试可选字段 customEndpoint / apiVersion / isPreset 的透传。
     */
    @Test
    fun testOptionalFields_passThrough() {
        val original = AIProviderConfig(
            id = "AZURE",
            name = "Azure OpenAI",
            type = ProviderType.AZURE,
            baseUrl = "https://res.openai.azure.com",
            apiKey = "azure-key",
            model = "gpt-4o",
            customEndpoint = null,
            apiVersion = "2024-02-01",
            isPreset = false,
        )

        val restored = original.toEntity().toModel()

        assertEquals("AZURE", restored.id)
        assertEquals("Azure OpenAI", restored.name)
        assertEquals("https://res.openai.azure.com", restored.baseUrl)
        assertEquals("2024-02-01", restored.apiVersion)
        assertNull(restored.customEndpoint)
        assertEquals(false, restored.isPreset)
        assertEquals("gpt-4o", restored.model)
    }
}
