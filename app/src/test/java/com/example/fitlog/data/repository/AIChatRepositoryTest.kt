package com.example.fitlog.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.fitlog.data.local.AppDatabase
import com.example.fitlog.data.remote.dto.ChatCompletionResponseDto
import com.example.fitlog.data.remote.dto.ChoiceDto
import com.example.fitlog.data.remote.dto.MessageDto
import com.example.fitlog.model.ai.AIProviderConfig
import com.example.fitlog.model.ai.ChatMessage
import com.example.fitlog.model.ai.ProviderType
import com.example.fitlog.testing.FakeAIApi
import com.example.fitlog.testing.createTestPreferencesDataStore
import com.example.fitlog.util.security.FakeAndroidKeyStoreProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

/**
 * [AIChatRepository] 的单元测试。
 *
 * 网络层用 [FakeAIApi] 替身，配置层用真实的内存 Room + 临时 DataStore，
 * 验证请求装配（URL/Headers/Body）、响应映射和错误处理策略
 * （Result 包装、CancellationException 透传）。
 */
@RunWith(RobolectricTestRunner::class)
class AIChatRepositoryTest {

    /**
     * 每个测试方法使用独立的临时目录存放 DataStore 文件。
     */
    @get:Rule
    val tmpFolder = TemporaryFolder()

    private lateinit var db: AppDatabase
    private lateinit var fakeApi: FakeAIApi
    private lateinit var providerConfigRepo: AIProviderConfigRepository
    private lateinit var repository: AIChatRepository

    /**
     * 测试调度器：与 DataStore scope 及 `runTest` 共享同一实例。
     */
    private val testScheduler = TestCoroutineScheduler()

    /**
     * DataStore 内部协程的作用域，测试结束时在 [tearDown] 中取消。
     */
    private lateinit var dataStoreScope: TestScope

    /**
     * 初始化内存数据库、临时 DataStore、模拟密钥库、Fake API 与仓库实例。
     */
    @Before
    fun setUp() {
        FakeAndroidKeyStoreProvider.setup()

        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        dataStoreScope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val dataStore = createTestPreferencesDataStore(
            tmpFolder.newFile("chat_prefs_test.preferences_pb"),
            dataStoreScope,
        )
        providerConfigRepo = AIProviderConfigRepository(db.aiProviderConfigDao(), dataStore)
        fakeApi = FakeAIApi()
        repository = AIChatRepository(fakeApi, providerConfigRepo)
    }

    /**
     * 取消 DataStore 作用域并关闭数据库。
     */
    @After
    fun tearDown() {
        dataStoreScope.cancel()
        db.close()
    }

    private fun config(
        type: ProviderType = ProviderType.OPENAI,
        baseUrl: String = "https://api.openai.com",
        apiKey: String = "sk-test",
        model: String = "gpt-5.6-sol",
        customEndpoint: String? = null,
        apiVersion: String? = null,
    ) = AIProviderConfig(
        id = type.name,
        name = type.name,
        type = type,
        baseUrl = baseUrl,
        apiKey = apiKey,
        model = model,
        customEndpoint = customEndpoint,
        apiVersion = apiVersion,
        isPreset = true,
    )

    private val messages = listOf(ChatMessage(role = "user", content = "你好"))

    // ──────────────────────────────────────
    // chat(messages) — 依赖激活配置
    // ──────────────────────────────────────

    /**
     * 测试未设置激活服务商时，返回包含引导文案的 failure，且不发网络请求。
     */
    @Test
    fun testChat_noActiveProvider_returnsGuidanceFailure() = runTest(testScheduler) {
        val result = repository.chat(messages)

        assertTrue(result.isFailure)
        assertEquals("未设置 AI 服务商，请先在设置中配置 API Key", result.exceptionOrNull()?.message)
        assertTrue(fakeApi.chatCalls.isEmpty())
    }

    /**
     * 测试已设置激活服务商时，chat(messages) 使用该配置发请求。
     */
    @Test
    fun testChat_withActiveProvider_usesActiveConfig() = runTest(testScheduler) {
        providerConfigRepo.insert(config(apiKey = "sk-active"))
        providerConfigRepo.setActiveProviderId("OPENAI")

        val result = repository.chat(messages)

        assertTrue(result.isSuccess)
        assertEquals(1, fakeApi.chatCalls.size)
        val call = fakeApi.chatCalls[0]
        assertEquals("https://api.openai.com/v1/chat/completions", call.url)
        assertEquals("Bearer sk-active", call.headers["Authorization"])
    }

    // ──────────────────────────────────────
    // chat(config, messages) — 请求装配
    // ──────────────────────────────────────

    /**
     * 测试成功响应：映射为领域模型 ChatMessage 并包装为 success。
     */
    @Test
    fun testChat_success_mapsReplyToModel() = runTest(testScheduler) {
        fakeApi.chatHandler = {
            ChatCompletionResponseDto(
                choices = listOf(ChoiceDto(message = MessageDto(role = "assistant", content = "加油，坚持训练！"))),
            )
        }

        val result = repository.chat(config(), messages)

        assertTrue(result.isSuccess)
        assertEquals(ChatMessage(role = "assistant", content = "加油，坚持训练！"), result.getOrNull())
    }

    /**
     * 测试请求体装配：model 与 messages 正确序列化进请求 DTO。
     */
    @Test
    fun testChat_assemblesRequestBody() = runTest(testScheduler) {
        repository.chat(config(model = "deepseek-v4-pro"), messages)

        val call = fakeApi.chatCalls[0]
        assertEquals("deepseek-v4-pro", call.request.model)
        assertEquals(listOf(MessageDto(role = "user", content = "你好")), call.request.messages)
    }

    /**
     * 测试 Azure 配置：URL 含 deployments 与 api-version，Header 用 api-key。
     */
    @Test
    fun testChat_azureConfig_buildsAzureStyleRequest() = runTest(testScheduler) {
        val azureConfig = config(
            type = ProviderType.AZURE,
            baseUrl = "https://my-resource.openai.azure.com",
            apiKey = "azure-secret",
            model = "gpt-4o",
            apiVersion = "2024-02-01",
        )

        val result = repository.chat(azureConfig, messages)

        assertTrue(result.isSuccess)
        val call = fakeApi.chatCalls[0]
        assertEquals(
            "https://my-resource.openai.azure.com/openai/deployments/gpt-4o/chat/completions?api-version=2024-02-01",
            call.url,
        )
        assertEquals("azure-secret", call.headers["api-key"])
        assertTrue(!call.headers.containsKey("Authorization"))
    }

    // ──────────────────────────────────────
    // 错误处理策略
    // ──────────────────────────────────────

    /**
     * 测试 AI 返回空 choices 时，包装为"AI 未返回任何回复"的 failure。
     */
    @Test
    fun testChat_emptyChoices_returnsFailure() = runTest(testScheduler) {
        fakeApi.chatHandler = { ChatCompletionResponseDto(choices = emptyList()) }

        val result = repository.chat(config(), messages)

        assertTrue(result.isFailure)
        assertEquals("AI 未返回任何回复", result.exceptionOrNull()?.message)
    }

    /**
     * 测试网络异常被包装为 Result.failure（而非抛出），且错误信息经用户可读映射。
     */
    @Test
    fun testChat_networkException_wrappedAsFailure() = runTest(testScheduler) {
        fakeApi.chatHandler = { throw IOException("连接超时") }

        val result = repository.chat(config(), messages)

        assertTrue(result.isFailure)
        // 原始异常作为 cause 保留（H14：不吞链路信息）
        assertTrue(result.exceptionOrNull()?.cause is IOException)
        assertEquals("连接超时", result.exceptionOrNull()?.message)
    }

    /**
     * 测试 CancellationException 不被 Result 包装，直接向上传播（结构化并发要求）。
     */
    @Test
    fun testChat_cancellation_propagates() = runTest(testScheduler) {
        fakeApi.chatHandler = { throw CancellationException("协程被取消") }

        try {
            repository.chat(config(), messages)
            fail("CancellationException 应该向上传播，而不是被包装为 Result.failure")
        } catch (e: CancellationException) {
            // 预期路径：取消异常原样抛出
        }
    }

    // ──────────────────────────────────────
    // testConnection / fetchModels
    // ──────────────────────────────────────

    /**
     * 测试连通性测试：发送最小消息并返回成功。
     */
    @Test
    fun testTestConnection_sendsMinimalMessage() = runTest(testScheduler) {
        val result = repository.testConnection(config())

        assertTrue(result.isSuccess)
        val call = fakeApi.chatCalls[0]
        assertEquals(listOf(MessageDto(role = "user", content = "Hi")), call.request.messages)
    }

    /**
     * 测试拉取模型列表：响应 data 映射为模型 id 列表。
     */
    @Test
    fun testFetchModels_success_mapsIds() = runTest(testScheduler) {
        val result = repository.fetchModels(config())

        assertTrue(result.isSuccess)
        assertEquals(listOf("model-a", "model-b"), result.getOrNull())
        assertEquals("https://api.openai.com/v1/models", fakeApi.modelsCalls[0].url)
        assertEquals("Bearer sk-test", fakeApi.modelsCalls[0].headers["Authorization"])
    }

    /**
     * 测试拉取模型列表失败时包装为 failure。
     */
    @Test
    fun testFetchModels_networkException_wrappedAsFailure() = runTest(testScheduler) {
        fakeApi.modelsHandler = { throw IOException("无网络") }

        val result = repository.fetchModels(config())

        assertTrue(result.isFailure)
        assertNotNull(result.exceptionOrNull())
    }

    /**
     * 测试 fetchModels 中 CancellationException 同样向上传播。
     */
    @Test
    fun testFetchModels_cancellation_propagates() = runTest(testScheduler) {
        fakeApi.modelsHandler = { throw CancellationException() }

        try {
            repository.fetchModels(config())
            fail("CancellationException 应该向上传播")
        } catch (e: CancellationException) {
            // 预期路径
        }
    }
}
