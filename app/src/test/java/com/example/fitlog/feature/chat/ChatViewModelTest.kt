package com.example.fitlog.feature.chat

import com.example.fitlog.feature.agent.engine.AgentEngine
import com.google.adk.kt.events.Event
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.FunctionCall
import com.google.adk.kt.types.Part
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [AgentEngine] 的测试替身：记录调用并以可编程的 handler 返回事件流，
 * 让 [ChatViewModelTest] 无需 Room/DataStore/网络即可覆盖 ViewModel 全部分支。
 */
class FakeAgentEngine : AgentEngine {

    /** sendMessage 收到的用户输入（按调用顺序）。 */
    val sentTexts = mutableListOf<String>()

    /** respondToConfirmation 收到的 (sessionId, callId, confirmed) 三元组。 */
    val confirmations = mutableListOf<Triple<String, String, Boolean>>()

    /** sendMessage 的可编程响应；未设置时返回"未配置服务商"失败。 */
    var sendHandler: (suspend (String) -> Result<Flow<Event>>)? = null

    /** respondToConfirmation 的可编程响应；未设置时返回空事件流。 */
    var confirmHandler: (suspend (String, String, Boolean) -> Result<Flow<Event>>)? = null

    /** clearSession 收到的会话 id（按调用顺序）。 */
    val clearedSessions = mutableListOf<String>()

    /** replayHistory 的可编程返回值；未设置时返回空列表（无历史）。 */
    var history: List<com.example.fitlog.model.ai.ChatMessage> = emptyList()

    override suspend fun sendMessage(sessionId: String, text: String): Result<Flow<Event>> {
        sentTexts += text
        return sendHandler?.invoke(text)
            ?: Result.failure(IllegalStateException("未配置 AI 服务商，请先在设置中配置 API Key"))
    }

    override suspend fun respondToConfirmation(
        sessionId: String,
        confirmationCallId: String,
        confirmed: Boolean,
    ): Result<Flow<Event>> {
        confirmations += Triple(sessionId, confirmationCallId, confirmed)
        return confirmHandler?.invoke(sessionId, confirmationCallId, confirmed)
            ?: Result.success(flow {})
    }

    override suspend fun clearSession(sessionId: String): Result<Unit> {
        clearedSessions += sessionId
        return Result.success(Unit)
    }

    override suspend fun replayHistory(sessionId: String): List<com.example.fitlog.model.ai.ChatMessage> =
        history
}

/**
 * [ChatViewModel] 的单元测试（纯 JVM，[FakeAgentEngine] 替身驱动）。
 *
 * 覆盖：输入状态、发送防重入、成功回复上屏、引擎失败与事件流异常的错误提示、
 * 工具确认流程（弹框 → 回传 → 后续回复）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    private val testScheduler = TestCoroutineScheduler()
    private lateinit var fakeEngine: FakeAgentEngine
    private lateinit var viewModel: ChatViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        fakeEngine = FakeAgentEngine()
        viewModel = ChatViewModel(fakeEngine)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** 构造一条模型最终文本回复事件。 */
    private fun finalTextEvent(text: String): Event = Event(
        author = "model",
        content = Content.fromText("model", text),
        turnComplete = true,
    )

    /** 构造一条 ADK 工具确认请求事件（adk_request_confirmation 合成调用）。 */
    private fun confirmationEvent(callId: String, toolName: String, args: Map<String, Any?>): Event =
        Event(
            author = "model",
            content = Content(
                role = "model",
                parts = listOf(
                    Part(
                        functionCall = FunctionCall(
                            name = FunctionCall.REQUEST_CONFIRMATION_FUNCTION_CALL_NAME,
                            args = mapOf(
                                FunctionCall.ORIGINAL_FUNCTION_CALL_KEY to mapOf(
                                    FunctionCall.NAME_KEY to toolName,
                                    FunctionCall.ARGS_KEY to args,
                                ),
                            ),
                            id = callId,
                        ),
                    ),
                ),
            ),
        )

    /** 输入框文本变化反映到状态。 */
    @Test
    fun testOnInputChange_updatesInput() = runTest(testScheduler) {
        viewModel.onInputChange("今天练胸")
        assertEquals("今天练胸", viewModel.uiState.value.input)
    }

    /** 空白输入不可发送：不产生消息、不触达引擎。 */
    @Test
    fun testSend_blankInput_doesNothing() = runTest(testScheduler) {
        viewModel.onInputChange("   ")
        viewModel.send()

        val state = viewModel.uiState.value
        assertTrue(state.messages.isEmpty())
        assertFalse(state.isSending)
        assertTrue(fakeEngine.sentTexts.isEmpty())
    }

    /** 成功发送：用户消息立即上屏、输入框清空、isSending 复位、回复追加且 id 唯一。 */
    @Test
    fun testSend_success_appendsReplyAndResetsState() = runTest(testScheduler) {
        fakeEngine.sendHandler = { Result.success(kotlinx.coroutines.flow.flowOf(finalTextEvent("建议先做热身"))) }

        viewModel.onInputChange("你好")
        viewModel.send()

        // Unconfined 调度器下 send() 返回时事件流已同步消费完毕，直接读终态
        val state = viewModel.uiState.value
        assertEquals("", state.input)
        assertEquals("user", state.messages[0].role)
        assertEquals("你好", state.messages[0].content)
        assertEquals("assistant", state.messages[1].role)
        assertEquals("建议先做热身", state.messages[1].content)
        // 每条消息有稳定且唯一的展示 id（LazyColumn key）
        assertTrue(state.messages[0].id != state.messages[1].id)
    }

    /** 未配置服务商（引擎构建失败）：错误信息展示、isSending 复位、用户消息保留。 */
    @Test
    fun testSend_engineFailure_showsErrorMessage() = runTest(testScheduler) {
        viewModel.onInputChange("你好")
        viewModel.send()

        val state = viewModel.uiState.value
        assertEquals("未配置 AI 服务商，请先在设置中配置 API Key", state.errorMessage)
        assertFalse(state.isSending)
        assertEquals(1, state.messages.size)
        assertEquals("你好", state.messages[0].content)
    }

    /** 事件流中途抛异常：转为错误提示而非未捕获协程异常（闪退回归测试）。 */
    @Test
    fun testSend_streamException_showsErrorMessageNotCrash() = runTest(testScheduler) {
        fakeEngine.sendHandler = {
            Result.success(flow<Event> { throw IOException("连接超时") })
        }

        viewModel.onInputChange("你好")
        viewModel.send()

        val state = viewModel.uiState.value
        assertEquals("连接超时", state.errorMessage)
        assertFalse(state.isSending)
    }

    /** ADK 错误事件（errorMessage）：直接展示且 isSending 复位。 */
    @Test
    fun testSend_errorEvent_showsErrorMessage() = runTest(testScheduler) {
        fakeEngine.sendHandler = {
            Result.success(flow<Event> { emit(Event(author = "model", errorMessage = "HTTP 429")) })
        }

        viewModel.onInputChange("你好")
        viewModel.send()

        assertEquals("HTTP 429", viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isSending)
    }

    /** 发送过程中再次发送被忽略（防重复并发请求）。 */
    @Test
    fun testSend_whileSending_secondSendIgnored() = runTest(testScheduler) {
        val latch = CompletableDeferred<Unit>()
        fakeEngine.sendHandler = {
            Result.success(
                flow {
                    latch.await()
                    emit(finalTextEvent("回复"))
                },
            )
        }

        viewModel.onInputChange("第一条")
        viewModel.send()
        assertTrue(viewModel.uiState.value.isSending)
        assertEquals(1, fakeEngine.sentTexts.size)

        // 发送中再次发送：直接被忽略，不产生新请求、不上屏新消息
        viewModel.onInputChange("第二条")
        viewModel.send()
        assertEquals(1, fakeEngine.sentTexts.size)
        assertEquals(1, viewModel.uiState.value.messages.size)

        // 放行第一次请求，状态正常收尾
        latch.complete(Unit)
        val state = viewModel.uiState.value
        assertFalse(state.isSending)
        assertEquals(2, state.messages.size)
        assertEquals("回复", state.messages[1].content)
    }

    /** 确认流程：确认请求事件弹框并暂停；同意后回传 callId，最终回复上屏。 */
    @Test
    fun testConfirmationFlow_pausesAndResumes() = runTest(testScheduler) {
        fakeEngine.sendHandler = {
            Result.success(
                flow {
                    emit(
                        confirmationEvent(
                            callId = "confirm-1",
                            toolName = "logBodyWeight",
                            args = mapOf("weightKg" to 72.5),
                        ),
                    )
                },
            )
        }
        fakeEngine.confirmHandler = { _, _, _ ->
            Result.success(flow { emit(finalTextEvent("已记录体重")) })
        }

        viewModel.onInputChange("帮我记体重 72.5")
        viewModel.send()

        // 确认请求到达：暂停发送、弹出确认框
        val paused = viewModel.uiState.value
        assertFalse(paused.isSending)
        val pending = paused.pendingConfirmation
        assertNotNull(pending)
        assertEquals("confirm-1", pending!!.callId)
        assertEquals("logBodyWeight", pending.toolName)
        assertEquals(72.5, pending.args["weightKg"])

        // 同意执行：回传 callId，收到最终回复
        viewModel.respondToConfirmation(confirmed = true)
        assertEquals(1, fakeEngine.confirmations.size)
        assertEquals(Triple("main_chat", "confirm-1", true), fakeEngine.confirmations[0])

        val state = viewModel.uiState.value
        assertFalse(state.isSending)
        assertNull(state.pendingConfirmation)
        assertEquals(2, state.messages.size)
        assertEquals("已记录体重", state.messages[1].content)
    }

    /** onErrorShown 清除一次性错误状态。 */
    @Test
    fun testOnErrorShown_clearsErrorMessage() = runTest(testScheduler) {
        viewModel.onInputChange("你好")
        viewModel.send()
        assertNotNull(viewModel.uiState.value.errorMessage)

        viewModel.onErrorShown()
        assertNull(viewModel.uiState.value.errorMessage)
    }

    /** init 时回放持久化历史：消息按序上屏、展示 id 从 1 起唯一递增。 */
    @Test
    fun testInit_replaysPersistedHistory() = runTest(testScheduler) {
        fakeEngine.history = listOf(
            com.example.fitlog.model.ai.ChatMessage(role = "user", content = "查体重"),
            com.example.fitlog.model.ai.ChatMessage(role = "assistant", content = "72.5kg"),
        )
        val vm = ChatViewModel(fakeEngine)

        val state = vm.uiState.value
        assertEquals(2, state.messages.size)
        assertEquals("查体重", state.messages[0].content)
        assertEquals("72.5kg", state.messages[1].content)
        assertTrue(state.messages[0].id != state.messages[1].id)
        // 回放后新消息的 id 续接，不得与历史消息撞 key
        fakeEngine.sendHandler = {
            Result.success(flow { emit(finalTextEvent("还有问题吗")) })
        }
        vm.onInputChange("谢谢")
        vm.send()
        val after = vm.uiState.value
        assertEquals(4, after.messages.size)
        // 全部展示 id 两两不同（LazyColumn key 唯一性）
        assertEquals(4, after.messages.map { it.id }.toSet().size)
    }

    /** 清空对话：删除会话历史、重置全部 UI 状态。 */
    @Test
    fun testOnClearChat_clearsMessagesAndSession() = runTest(testScheduler) {
        fakeEngine.sendHandler = { Result.success(flow { emit(finalTextEvent("你好呀")) }) }
        viewModel.onInputChange("你好")
        viewModel.send()
        assertEquals(2, viewModel.uiState.value.messages.size)

        viewModel.onClearChat()

        val state = viewModel.uiState.value
        assertTrue(state.messages.isEmpty())
        assertNull(state.errorMessage)
        assertNull(state.pendingConfirmation)
        assertEquals("", state.input)
        assertEquals(listOf("main_chat"), fakeEngine.clearedSessions)
    }
}
