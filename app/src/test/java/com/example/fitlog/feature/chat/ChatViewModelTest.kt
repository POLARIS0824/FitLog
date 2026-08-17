package com.example.fitlog.feature.chat

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.fitlog.data.local.AppDatabase
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
 * [ChatViewModel] 的单元测试。
 *
 * AIChatRepository 是具体类，这里采用"真实仓库链 + Fake 网络层"的集成式测法：
 * ViewModel → 真实 AIChatRepository → FakeAIApi（内存替身），
 * 配置层用真实内存 Room + 临时 DataStore。
 *
 * 验证发送流程的状态推进（消息上屏、isSending、回复追加）、
 * 错误路径与并发发送去重。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ChatViewModelTest {

    /**
     * 每个测试方法使用独立的临时目录存放 DataStore 文件。
     */
    @get:Rule
    val tmpFolder = TemporaryFolder()

    private lateinit var db: AppDatabase
    private lateinit var fakeApi: FakeAIApi
    private lateinit var providerConfigRepo: AIProviderConfigRepository
    private lateinit var viewModel: ChatViewModel

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
            tmpFolder.newFile("chat_vm_prefs.preferences_pb"),
            dataStoreScope,
        )
        providerConfigRepo = AIProviderConfigRepository(db.aiProviderConfigDao(), dataStore)
        fakeApi = FakeAIApi()
        viewModel = ChatViewModel(AIChatRepository(fakeApi, providerConfigRepo))
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

    /**
     * 配置并激活一个可用的 AI 服务商（供需要成功发送的用例使用）。
     */
    private suspend fun activateProvider() {
        providerConfigRepo.insert(
            AIProviderConfig(
                id = "OPENAI",
                name = "OpenAI",
                type = ProviderType.OPENAI,
                baseUrl = "https://api.openai.com",
                apiKey = "sk-test",
                model = "gpt-5.6-sol",
                isPreset = true,
            ),
        )
        providerConfigRepo.setActiveProviderId("OPENAI")
    }

    /**
     * 测试输入框文本变化事件。
     */
    @Test
    fun testOnInputChange_updatesInput() = runTest(testScheduler) {
        viewModel.onInputChange("今天练胸")
        assertEquals("今天练胸", viewModel.uiState.value.input)
    }

    /**
     * 测试空白输入不可发送：不产生消息、不发网络请求。
     */
    @Test
    fun testSend_blankInput_doesNothing() = runTest(testScheduler) {
        activateProvider()

        viewModel.onInputChange("   ")
        viewModel.send()

        val state = viewModel.uiState.value
        assertTrue(state.messages.isEmpty())
        assertFalse(state.isSending)
        assertTrue(fakeApi.chatCalls.isEmpty())
    }

    /**
     * 测试成功发送的完整状态推进：
     * 用户消息立即上屏、输入框清空、isSending 复位、AI 回复追加。
     */
    @Test
    fun testSend_success_appendsReplyAndResetsState() = runTest(testScheduler) {
        activateProvider()

        viewModel.onInputChange("你好")
        viewModel.send()

        val state = viewModel.uiState.first { !it.isSending && it.messages.size == 2 }
        assertEquals("", state.input)

        assertEquals("user", state.messages[0].role)
        assertEquals("你好", state.messages[0].content)

        assertEquals("assistant", state.messages[1].role)
        assertEquals("默认回复", state.messages[1].content)
        // 每条消息有稳定且唯一的展示 id（LazyColumn key）
        assertTrue(state.messages[0].id != state.messages[1].id)
    }

    /**
     * 测试发送时请求中注入了系统提示词（第一条为 system 角色）。
     */
    @Test
    fun testSend_success_prependsSystemPrompt() = runTest(testScheduler) {
        activateProvider()

        viewModel.onInputChange("你好")
        viewModel.send()
        viewModel.uiState.first { !it.isSending && it.messages.size == 2 }

        val sentMessages = fakeApi.chatCalls[0].request.messages
        assertEquals("system", sentMessages[0].role)
        assertEquals("user", sentMessages[1].role)
        assertEquals("你好", sentMessages[1].content)
    }

    /**
     * 测试未配置 AI 服务商时发送失败：错误信息引导用户去设置，用户消息保留。
     */
    @Test
    fun testSend_noActiveProvider_showsGuidanceError() = runTest(testScheduler) {
        viewModel.onInputChange("你好")
        viewModel.send()

        val state = viewModel.uiState.first { it.errorMessage != null }
        assertEquals("未设置 AI 服务商，请先在设置中配置 API Key", state.errorMessage)
        assertFalse(state.isSending)
        // 用户消息仍然保留在列表中
        assertEquals(1, state.messages.size)
        assertEquals("你好", state.messages[0].content)
    }

    /**
     * 测试网络异常路径：errorMessage 为异常描述，isSending 复位。
     */
    @Test
    fun testSend_networkFailure_showsErrorMessage() = runTest(testScheduler) {
        activateProvider()
        fakeApi.chatHandler = { throw IOException("连接超时") }

        viewModel.onInputChange("你好")
        viewModel.send()

        val state = viewModel.uiState.first { it.errorMessage != null }
        assertEquals("连接超时", state.errorMessage)
        assertFalse(state.isSending)
    }

    /**
     * 测试 onErrorShown 清除一次性错误状态。
     */
    @Test
    fun testOnErrorShown_clearsErrorMessage() = runTest(testScheduler) {
        viewModel.onInputChange("你好")
        viewModel.send()
        viewModel.uiState.first { it.errorMessage != null }

        viewModel.onErrorShown()
        assertNull(viewModel.uiState.value.errorMessage)
    }

    /**
     * 测试发送过程中再次点击发送被忽略（防重复并发请求）。
     */
    @Test
    fun testSend_whileSending_secondSendIgnored() = runTest(testScheduler) {
        activateProvider()

        // 用闩锁阻塞 Fake API 响应，使第一次发送停留在 isSending 状态；
        // called 信号量确保断言发生在 Fake API 真正被调用之后
        val called = CompletableDeferred<Unit>()
        val latch = CompletableDeferred<Unit>()
        fakeApi.chatHandler = {
            called.complete(Unit)
            latch.await()
            com.example.fitlog.data.remote.dto.ChatCompletionResponseDto(
                choices = listOf(
                    com.example.fitlog.data.remote.dto.ChoiceDto(
                        message = com.example.fitlog.data.remote.dto.MessageDto("assistant", "回复"),
                    ),
                ),
            )
        }

        viewModel.onInputChange("第一条")
        viewModel.send()

        // 等待第一次请求到达 Fake API（经过 DataStore/Room 真实异步链路）
        called.await()
        assertEquals(1, fakeApi.chatCalls.size)
        assertTrue(viewModel.uiState.value.isSending)
        assertEquals(1, viewModel.uiState.value.messages.size)

        // 发送中再次发送：直接被忽略，不产生新请求、不上屏新消息
        viewModel.onInputChange("第二条")
        viewModel.send()
        assertEquals(1, fakeApi.chatCalls.size)
        assertEquals(1, viewModel.uiState.value.messages.size)

        // 放行第一次请求，状态正常收尾
        latch.complete(Unit)
        val state = viewModel.uiState.first { !it.isSending }
        assertEquals(2, state.messages.size)
        assertEquals("回复", state.messages[1].content)
    }
}
