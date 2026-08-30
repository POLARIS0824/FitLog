package com.example.fitlog.feature.aisettings

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.fitlog.data.local.AppDatabase
import com.example.fitlog.data.remote.dto.ChatCompletionResponseDto
import com.example.fitlog.data.remote.dto.ChoiceDto
import com.example.fitlog.data.remote.dto.MessageDto
import com.example.fitlog.data.remote.dto.ModelItemDto
import com.example.fitlog.data.remote.dto.ModelsResponseDto
import com.example.fitlog.data.repository.AIChatRepository
import com.example.fitlog.data.repository.AIProviderConfigRepository
import com.example.fitlog.model.ai.AIProviderConfig
import com.example.fitlog.model.ai.ProviderType
import com.example.fitlog.testing.FakeAIApi
import com.example.fitlog.testing.createTestPreferencesDataStore
import com.example.fitlog.util.security.FakeAndroidKeyStoreProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

/**
 * [AISettingsViewModel] 的单元测试。
 *
 * 使用真实仓库链（内存 Room + 临时 DataStore）+ Fake 网络层，
 * 验证服务商选择回填、表单事件、保存即激活、
 * 拉取模型列表（成功/失败/守卫）与连通性测试的完整行为。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AISettingsViewModelTest {

    /**
     * 每个测试方法使用独立的临时目录存放 DataStore 文件。
     */
    @get:Rule
    val tmpFolder = TemporaryFolder()

    private lateinit var db: AppDatabase
    private lateinit var fakeApi: FakeAIApi
    private lateinit var providerConfigRepo: AIProviderConfigRepository
    private lateinit var viewModel: AISettingsViewModel

    /**
     * 测试调度器：与 DataStore scope、Main dispatcher 及 `runTest` 共享同一实例。
     */
    private val testScheduler = TestCoroutineScheduler()

    /**
     * DataStore 内部协程的作用域，测试结束时在 [tearDown] 中取消。
     */
    private lateinit var dataStoreScope: TestScope

    /**
     * 设置主调度器，初始化数据库、仓库链与 ViewModel。
     */
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        FakeAndroidKeyStoreProvider.setup()

        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        dataStoreScope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val dataStore = createTestPreferencesDataStore(
            tmpFolder.newFile("ai_settings_prefs.preferences_pb"),
            dataStoreScope,
        )
        providerConfigRepo = AIProviderConfigRepository(db.aiProviderConfigDao(), dataStore)
        fakeApi = FakeAIApi()
        viewModel = AISettingsViewModel(providerConfigRepo, AIChatRepository(fakeApi, providerConfigRepo))
    }

    /**
     * 取消 DataStore 作用域，重置主调度器并关闭数据库。
     */
    @After
    fun tearDown() {
        dataStoreScope.cancel()
        Dispatchers.resetMain()
        db.close()
    }

    private fun config(
        id: String,
        type: ProviderType = ProviderType.OPENAI,
        baseUrl: String = "https://api.openai.com",
        apiKey: String = "sk-saved",
        model: String = "gpt-5.5",
        cachedModels: List<String> = emptyList(),
    ) = AIProviderConfig(
        id = id,
        name = type.name,
        type = type,
        baseUrl = baseUrl,
        apiKey = apiKey,
        model = model,
        isPreset = true,
        cachedModels = cachedModels,
    )

    // ──────────────────────────────────────
    // Provider 选择与回填
    // ──────────────────────────────────────

    /**
     * 测试默认选中的服务商类型为 DEEPSEEK。
     */
    @Test
    fun testInitialState_defaultSelectedTypeIsDeepseek() = runTest(testScheduler) {
        val state = viewModel.uiState.first()
        assertEquals(ProviderType.DEEPSEEK, state.provider.selectedType)
    }

    /**
     * 测试选中未保存过的服务商：apiKey 清空，模型与 baseUrl 回填为该服务商默认值。
     */
    @Test
    fun testOnProviderSelected_noSavedConfig_backfillsDefaults() = runTest(testScheduler) {
        viewModel.onProviderSelected(ProviderType.OPENAI)

        val state = viewModel.uiState.first { it.model.selectedModel == "gpt-5.6-sol" }
        assertEquals(ProviderType.OPENAI, state.provider.selectedType)
        assertEquals("", state.apiKey.apiKey)
        assertEquals("https://api.openai.com", state.endpoint.baseUrl)
        assertEquals(emptyList<String>(), state.model.availableModels)
    }

    /**
     * 测试选中已保存过的服务商：表单回填已保存的凭据、模型与缓存模型列表。
     */
    @Test
    fun testOnProviderSelected_withSavedConfig_backfillsSavedValues() = runTest(testScheduler) {
        providerConfigRepo.insert(
            config(
                id = "OPENAI",
                baseUrl = "https://proxy.example.com",
                apiKey = "sk-saved",
                model = "gpt-5.5",
                cachedModels = listOf("gpt-5.5", "gpt-5.6-sol"),
            ),
        )

        viewModel.onProviderSelected(ProviderType.OPENAI)

        val state = viewModel.uiState.first { it.apiKey.apiKey == "sk-saved" }
        assertEquals("gpt-5.5", state.model.selectedModel)
        assertEquals(listOf("gpt-5.5", "gpt-5.6-sol"), state.model.availableModels)
        assertEquals("https://proxy.example.com", state.endpoint.baseUrl)
    }

    /**
     * 测试表单输入事件更新对应字段。
     */
    @Test
    fun testFormInputEvents_updateState() = runTest(testScheduler) {
        viewModel.onApiKeyChange("sk-new")
        viewModel.onModelChange("my-model")
        viewModel.onBaseUrlChange("https://custom.example.com")
        viewModel.onCustomEndpointChange("/v1/chat")
        viewModel.onApiVersionChange("2024-02-01")

        val state = viewModel.uiState.first { it.apiKey.apiKey == "sk-new" }
        assertEquals("my-model", state.model.selectedModel)
        assertEquals("https://custom.example.com", state.endpoint.baseUrl)
        assertEquals("/v1/chat", state.endpoint.customEndpoint)
        assertEquals("2024-02-01", state.endpoint.apiVersion)
    }

    /**
     * 测试切换 API Key 明文/密文显示。
     */
    @Test
    fun testOnToggleApiKeyVisibility_flipsFlag() = runTest(testScheduler) {
        assertFalse(viewModel.uiState.first().apiKey.showApiKey)

        viewModel.onToggleApiKeyVisibility()
        assertTrue(viewModel.uiState.first { it.apiKey.showApiKey }.apiKey.showApiKey)

        viewModel.onToggleApiKeyVisibility()
        assertFalse(viewModel.uiState.first { !it.apiKey.showApiKey }.apiKey.showApiKey)
    }

    // ──────────────────────────────────────
    // 保存
    // ──────────────────────────────────────

    /**
     * 测试保存：配置落库（apiKey 可读回明文）、自动设为激活服务商、弹出成功提示。
     */
    @Test
    fun testOnSave_persistsActivatesAndShowsSuccess() = runTest(testScheduler) {
        val cfg = config(id = "OPENAI", apiKey = "sk-to-save")
        viewModel.onSave(cfg)

        val state = viewModel.uiState.first { it.ui.successMessage != null }
        assertEquals("已保存并启用 OPENAI", state.ui.successMessage)

        assertEquals("sk-to-save", providerConfigRepo.getById("OPENAI")?.apiKey)
        assertEquals("OPENAI", providerConfigRepo.activeProviderId.first())
    }

    /**
     * 测试 onErrorShown / onSuccessShown 清除一次性提示。
     */
    @Test
    fun testOneShotMessages_cleared() = runTest(testScheduler) {
        viewModel.onSave(config(id = "OPENAI"))
        viewModel.uiState.first { it.ui.successMessage != null }

        viewModel.onSuccessShown()
        assertNull(viewModel.uiState.first().ui.successMessage)

        viewModel.onErrorShown() // 无错误时调用也应安全无副作用
        assertNull(viewModel.uiState.first().ui.errorMessage)
    }

    // ──────────────────────────────────────
    // 拉取模型列表
    // ──────────────────────────────────────

    /**
     * 测试 apiKey 为空时拉取被守卫拦截：不发网络请求，不产生提示。
     */
    @Test
    fun testOnFetchModels_blankApiKey_guarded() = runTest(testScheduler) {
        viewModel.onFetchModels()

        assertTrue(fakeApi.modelsCalls.isEmpty())
        val state = viewModel.uiState.first()
        assertEquals("", state.model.fetchResult)
        assertFalse(state.model.isLoading)
    }

    /**
     * 测试拉取成功且配置不存在时：模型列表回填 UI，但**不落库**
     * （表单中的 apiKey 尚未确认保存，不得随拉取静默写入数据库）。
     */
    @Test
    fun testOnFetchModels_successWithoutExistingConfig_updatesUiWithoutPersisting() = runTest(testScheduler) {
        fakeApi.modelsHandler = {
            ModelsResponseDto(data = listOf(ModelItemDto("gpt-5.6-sol"), ModelItemDto("gpt-5.5")))
        }

        viewModel.onProviderSelected(ProviderType.OPENAI)
        // 等待回填完成（onProviderSelected 内部要挂起查询 Room，是异步的）
        viewModel.uiState.first { it.model.selectedModel == "gpt-5.6-sol" }
        viewModel.onApiKeyChange("sk-new")
        viewModel.onFetchModels()

        val state = viewModel.uiState.first { it.model.fetchResult.isNotEmpty() }
        assertEquals("✅ 成功拉取 2 个模型", state.model.fetchResult)
        assertEquals(listOf("gpt-5.6-sol", "gpt-5.5"), state.model.availableModels)
        assertFalse(state.model.isLoading)

        // 配置从未保存：拉取不得产生任何落库（apiKey 也不落）
        assertNull(providerConfigRepo.getById("OPENAI"))
    }

    /**
     * 测试拉取成功且配置已存在时：仅更新缓存模型列表，不覆盖已保存的 apiKey。
     */
    @Test
    fun testOnFetchModels_successWithExistingConfig_updatesModelsOnly() = runTest(testScheduler) {
        providerConfigRepo.insert(config(id = "DEEPSEEK", type = ProviderType.DEEPSEEK, apiKey = "sk-old"))

        // 默认选中 DEEPSEEK；表单里输入了新的 key 但尚未保存
        viewModel.onApiKeyChange("sk-unsaved")
        // 配置组装统一走表单状态（endpointState），需显式填写 baseUrl
        viewModel.onBaseUrlChange("https://api.deepseek.com")
        viewModel.onFetchModels()

        viewModel.uiState.first { it.model.fetchResult.isNotEmpty() }

        val saved = providerConfigRepo.getById("DEEPSEEK")
        assertEquals(listOf("model-a", "model-b"), saved?.cachedModels)
        // 已保存的 apiKey 不被表单值覆盖
        assertEquals("sk-old", saved?.apiKey)
    }

    /**
     * 测试拉取失败：提示失败原因，模型列表保持原值不阻塞手动输入。
     */
    @Test
    fun testOnFetchModels_failure_showsErrorAndKeepsModels() = runTest(testScheduler) {
        fakeApi.modelsHandler = { throw IOException("鉴权失败") }

        viewModel.onProviderSelected(ProviderType.OPENAI)
        viewModel.uiState.first { it.model.selectedModel == "gpt-5.6-sol" }
        viewModel.onApiKeyChange("sk-bad")
        viewModel.onFetchModels()

        val state = viewModel.uiState.first { it.model.fetchResult.isNotEmpty() }
        assertEquals("❌ 拉取模型失败：鉴权失败", state.model.fetchResult)
        assertFalse(state.model.isLoading)
        // 拉取失败不污染已有模型列表
        assertEquals(emptyList<String>(), state.model.availableModels)
    }

    /**
     * 测试 onFetchResultShown 清除拉取结果提示。
     */
    @Test
    fun testOnFetchResultShown_clearsMessage() = runTest(testScheduler) {
        viewModel.onProviderSelected(ProviderType.OPENAI)
        viewModel.uiState.first { it.model.selectedModel == "gpt-5.6-sol" }
        viewModel.onApiKeyChange("sk-new")
        viewModel.onFetchModels()
        viewModel.uiState.first { it.model.fetchResult.isNotEmpty() }

        viewModel.onFetchResultShown()
        assertEquals("", viewModel.uiState.first().model.fetchResult)
    }

    // ──────────────────────────────────────
    // 连通性测试
    // ──────────────────────────────────────

    /**
     * 测试表单字段不完整时连通性测试被守卫拦截。
     */
    @Test
    fun testOnTestConnection_blankFields_guarded() = runTest(testScheduler) {
        viewModel.onTestConnection()

        assertTrue(fakeApi.chatCalls.isEmpty())
        assertFalse(viewModel.uiState.first().test.isTesting)
        assertEquals("", viewModel.uiState.first().test.lastResult)
    }

    /**
     * 测试连通性测试成功：使用表单中未保存的凭据发最小消息。
     */
    @Test
    fun testOnTestConnection_success() = runTest(testScheduler) {
        fakeApi.chatHandler = {
            ChatCompletionResponseDto(
                choices = listOf(ChoiceDto(message = MessageDto("assistant", "Hi!"))),
            )
        }

        viewModel.onProviderSelected(ProviderType.OPENAI)
        // 等待回填完成，否则 onTestConnection 会因 model 为空被守卫拦截
        viewModel.uiState.first { it.model.selectedModel == "gpt-5.6-sol" }
        viewModel.onApiKeyChange("sk-test")
        viewModel.onTestConnection()

        val state = viewModel.uiState.first { it.test.lastResult.isNotEmpty() }
        assertEquals("✅ 连接成功", state.test.lastResult)
        assertFalse(state.test.isTesting)

        // 验证用的是表单凭据而非已保存配置
        val call = fakeApi.chatCalls[0]
        assertEquals("Bearer sk-test", call.headers["Authorization"])
        assertEquals("https://api.openai.com/v1/chat/completions", call.url)
    }

    /**
     * 测试连通性测试失败：提示失败原因。
     */
    @Test
    fun testOnTestConnection_failure() = runTest(testScheduler) {
        fakeApi.chatHandler = { throw IOException("HTTP 401") }

        viewModel.onProviderSelected(ProviderType.OPENAI)
        viewModel.uiState.first { it.model.selectedModel == "gpt-5.6-sol" }
        viewModel.onApiKeyChange("sk-bad")
        viewModel.onTestConnection()

        val state = viewModel.uiState.first { it.test.lastResult.isNotEmpty() }
        assertEquals("❌ 连接失败：HTTP 401", state.test.lastResult)
        assertFalse(state.test.isTesting)
    }

    /**
     * 测试切换服务商后旧的测试结果被清空。
     */
    @Test
    fun testOnProviderSelected_resetsTestState() = runTest(testScheduler) {
        viewModel.onProviderSelected(ProviderType.OPENAI)
        viewModel.uiState.first { it.model.selectedModel == "gpt-5.6-sol" }
        viewModel.onApiKeyChange("sk-test")
        viewModel.onTestConnection()
        viewModel.uiState.first { it.test.lastResult.isNotEmpty() }

        viewModel.onProviderSelected(ProviderType.MOONSHOT)

        val state = viewModel.uiState.first {
            it.provider.selectedType == ProviderType.MOONSHOT && it.test.lastResult == ""
        }
        assertFalse(state.test.isTesting)
    }

    // ──────────────────────────────────────
    // 初始定位
    // ──────────────────────────────────────

    /**
     * 测试已有激活服务商时，ViewModel 初始化后表单定位到该服务商。
     */
    @Test
    fun testInit_withActiveProvider_formLandsOnActiveType() = runTest(testScheduler) {
        // 先落库、后激活、再建 VM：与真实启动时序一致
        // （activeProvider 对 DataStore ID 首值立即重发，Room 行此时已存在）
        providerConfigRepo.insert(
            config(id = "MOONSHOT", type = ProviderType.MOONSHOT, baseUrl = "https://api.moonshot.cn"),
        )
        providerConfigRepo.setActiveProviderId("MOONSHOT")
        advanceUntilIdle()

        val vm = AISettingsViewModel(providerConfigRepo, AIChatRepository(fakeApi, providerConfigRepo))

        // 等回填全部完成（apiKey 仅在回填链路写入，以其出现为完成信号）
        val state = vm.uiState.first {
            it.provider.selectedType == ProviderType.MOONSHOT && it.apiKey.apiKey.isNotEmpty()
        }
        assertEquals("sk-saved", state.apiKey.apiKey)
        assertEquals("https://api.moonshot.cn", state.endpoint.baseUrl)
    }

    /**
     * 测试 init 竞态守卫：激活服务商回填解析完成前用户已手动输入，
     * 则放弃回填——用户输入保留、选中类型不被强切回激活 provider。
     */
    @Test
    fun testInit_doesNotOverwriteUserInputAfterInteraction() = runTest(testScheduler) {
        providerConfigRepo.insert(
            config(id = "MOONSHOT", type = ProviderType.MOONSHOT, baseUrl = "https://api.moonshot.cn"),
        )
        providerConfigRepo.setActiveProviderId("MOONSHOT")

        val vm = AISettingsViewModel(providerConfigRepo, AIChatRepository(fakeApi, providerConfigRepo))
        // 用户在 init 回填解析完成前立即输入
        vm.onApiKeyChange("sk-typed")
        vm.onModelChange("my-model")

        val state = vm.uiState.first { it.apiKey.apiKey == "sk-typed" }
        assertEquals("sk-typed", state.apiKey.apiKey)
        assertEquals("my-model", state.model.selectedModel)
        // 未被回填强切回 MOONSHOT
        assertEquals(ProviderType.DEEPSEEK, state.provider.selectedType)
    }

    /**
     * 测试拉取模型串台守卫：请求在途时切换 provider，过期结果不写入新表单。
     */
    @Test
    fun testOnFetchModels_staleResponseIgnoredAfterProviderSwitch() = runTest(testScheduler) {
        val called = CompletableDeferred<Unit>()
        val latch = CompletableDeferred<Unit>()
        fakeApi.modelsHandler = {
            called.complete(Unit)
            latch.await()
            ModelsResponseDto(data = listOf(ModelItemDto("stale-model")))
        }

        viewModel.onProviderSelected(ProviderType.OPENAI)
        viewModel.uiState.first { it.model.selectedModel == "gpt-5.6-sol" }
        viewModel.onApiKeyChange("sk-new")
        viewModel.onFetchModels()

        // 等待请求到达 Fake 网络层，然后切走 provider
        called.await()
        viewModel.onProviderSelected(ProviderType.MOONSHOT)
        viewModel.uiState.first { it.provider.selectedType == ProviderType.MOONSHOT }

        // 放行旧请求
        latch.complete(Unit)

        val state = viewModel.uiState.first { !it.model.isLoading }
        assertEquals(ProviderType.MOONSHOT, state.provider.selectedType)
        // 过期结果被丢弃：新 provider 表单无模型列表、无提示
        assertEquals("", state.model.fetchResult)
        assertEquals(emptyList<String>(), state.model.availableModels)
    }

    /**
     * 测试连通性测试串台守卫：请求在途时切换 provider，过期结果不写入新表单。
     */
    @Test
    fun testOnTestConnection_staleResultIgnoredAfterProviderSwitch() = runTest(testScheduler) {
        val called = CompletableDeferred<Unit>()
        val latch = CompletableDeferred<Unit>()
        fakeApi.chatHandler = {
            called.complete(Unit)
            latch.await()
            ChatCompletionResponseDto(
                choices = listOf(ChoiceDto(message = MessageDto("assistant", "Hi"))),
            )
        }

        viewModel.onProviderSelected(ProviderType.OPENAI)
        viewModel.uiState.first { it.model.selectedModel == "gpt-5.6-sol" }
        viewModel.onApiKeyChange("sk-test")
        viewModel.onTestConnection()

        called.await()
        viewModel.onProviderSelected(ProviderType.MOONSHOT)
        viewModel.uiState.first { it.provider.selectedType == ProviderType.MOONSHOT }

        latch.complete(Unit)

        val state = viewModel.uiState.first {
            it.provider.selectedType == ProviderType.MOONSHOT && !it.test.isTesting
        }
        // 过期测试结果被丢弃
        assertEquals("", state.test.lastResult)
    }
}
