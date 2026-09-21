package com.example.fitlog.feature.chat

import com.example.fitlog.data.repository.ChatRepository
import com.example.fitlog.feature.agent.engine.AgentEngine
import com.example.fitlog.model.ai.AgentStep
import com.example.fitlog.model.ai.AgentStepType
import com.example.fitlog.model.ai.ChatThreadMessage
import com.example.fitlog.testing.MainDispatcherRule
import com.google.adk.kt.events.Event
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.FunctionCall
import com.google.adk.kt.types.Part
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
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

    /** clearSession 的可编程响应；未设置时默认成功。 */
    var clearSessionHandler: (suspend (String) -> Result<Unit>)? = null

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
        return clearSessionHandler?.invoke(sessionId) ?: Result.success(Unit)
    }

    override suspend fun replayHistory(sessionId: String): List<com.example.fitlog.model.ai.ChatMessage> =
        history
}

/**
 * [ChatRepository] 的内存假实现：写入按 Room 自增语义分配 id（从 1 起），
 * 步骤按 runId 挂载到消息（与 RoomChatRepository.loadThread 同规则）；
 * 记录清空调用次数，供清空对话分支断言"本地两张表确被清理"。
 */
private class FakeChatRepository : ChatRepository {

    private data class StoredMessage(
        val id: Long,
        val role: String,
        val content: String,
        val runId: String?,
        val durationMs: Long?,
        val createdAt: Long,
    )    private data class StoredStep(
        val id: Long,
        val runId: String,
        val order: Int,
        val type: AgentStepType,
        val toolKey: String?,
        val label: String,
        val detail: String?,
        val elapsedMs: Long,
        val createdAt: Long,
    )

    /** 已写入的步骤数（清空断言用）。 */
    var stepCount = 0
        private set

    /** deleteStepsByRun 收到的 runId（孤儿清理断言用）。 */
    val deletedRunIds = mutableListOf<String>()

    /** clearAll 被调用的次数。 */
    var clearCount = 0
        private set

    /** clearAll 的可编程挂起/异常处理器。 */
    var clearAllHandler: (suspend () -> Unit)? = null

    /** loadThread 的可编程挂起/返回值处理器。 */
    var loadThreadHandler: (suspend () -> List<ChatThreadMessage>?)? = null

    private val stored = mutableListOf<StoredMessage>()
    private val storedSteps = mutableListOf<StoredStep>()
    private var nextId = 1L

    override suspend fun insertMessage(
        role: String,
        content: String,
        runId: String?,
        durationMs: Long?,
        createdAt: Long,
    ): Long {
        val id = nextId++
        stored += StoredMessage(id, role, content, runId, durationMs, createdAt)
        return id
    }

    override suspend fun insertStep(
        runId: String,
        order: Int,
        type: AgentStepType,
        toolKey: String?,
        label: String,
        detail: String?,
        elapsedMs: Long,
        createdAt: Long,
    ): Long {
        val id = nextId++
        storedSteps += StoredStep(id, runId, order, type, toolKey, label, detail, elapsedMs, createdAt)
        stepCount++
        return id
    }

    /** 全部消息按写入顺序（createdAt 升序）返回，步骤按 runId 挂载。 */
    override suspend fun loadThread(): List<ChatThreadMessage> {
        loadThreadHandler?.invoke()?.let { return it }
        return stored.map { message ->
            ChatThreadMessage(
                id = message.id,
                role = message.role,
                content = message.content,
                durationMs = message.durationMs,
                steps = storedSteps
                    .filter { it.runId == message.runId }
                    .sortedBy { it.order }
                    .map { step ->
                        AgentStep(
                            id = step.id,
                            type = step.type,
                            toolKey = step.toolKey,
                            label = step.label,
                            detail = step.detail,
                            elapsedMs = step.elapsedMs,
                        )
                    },
            )
        }
    }

    override suspend fun count(): Long = stored.size.toLong()

    override suspend fun insertMessages(
        messages: List<ChatRepository.SeedMessage>,
    ): List<Long> = messages.map { seed ->
        // 假实现逐条委托即可：事务性由真实现（Room withTransaction）保证
        insertMessage(role = seed.role, content = seed.content, createdAt = seed.createdAt)
    }

    override suspend fun deleteStepsByRun(runId: String) {
        deletedRunIds += runId
        storedSteps.removeAll { it.runId == runId }
    }

    /** 按 runId 统计现存的步骤行数（孤儿清理断言用：删除后应为全零/空）。 */
    fun stepCountForRuns(): Map<String, Int> = storedSteps.groupingBy { it.runId }.eachCount()

    override suspend fun clearAll() {
        clearCount++
        clearAllHandler?.invoke()
        stored.clear()
        storedSteps.clear()
    }
}

/**
 * [ChatViewModel] 的单元测试（纯 JVM，[FakeAgentEngine] 与内存仓库假实现驱动）。
 *
 * 覆盖：输入状态、发送防重入、成功回复上屏、引擎失败与事件流异常的错误提示、
 * 工具确认流程（弹框 → 回传 → 后续回复）、清空对话的本地表清理。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private lateinit var fakeEngine: FakeAgentEngine
    private lateinit var fakeChatRepository: FakeChatRepository
    private lateinit var viewModel: ChatViewModel

    @Before
    fun setUp() {
        fakeEngine = FakeAgentEngine()
        fakeChatRepository = FakeChatRepository()
        viewModel = ChatViewModel(fakeEngine, fakeChatRepository)
    }

    /** 构造一条模型最终文本回复事件。 */
    private fun finalTextEvent(text: String): Event = Event(
        author = "model",
        content = Content.fromText("model", text),
        turnComplete = true,
    )

    /**
     * 构造一条中间工具调用事件（记 TOOL_CALL 步骤）。
     *
     * 必须携带 functionCall：ADK 的 isFinalResponse 只看 skipSummarization/
     * longRunningToolIds/无函数 part/非 partial——纯文本事件天然是 final，
     * 唯有工具调用轮才构成"中间步骤"。
     */
    private fun toolCallStepEvent(): Event = Event(
        author = "model",
        content = Content(
            role = "model",
            parts = listOf(
                Part(
                    functionCall = FunctionCall(
                        name = "getUserProfile",
                        args = emptyMap(),
                        id = "call-1",
                    ),
                ),
            ),
        ),
        partial = false,
    )

    /** 构造一条 ADK 工具确认请求事件（adk_request_confirmation 合成调用）。 */    private fun confirmationEvent(callId: String, toolName: String, args: Map<String, Any?>): Event =
        Event(
            author = "model",
            content = Content(
                role = "model",
                parts = listOf(
                    Part(
                        functionCall = FunctionCall(
                            name = FunctionCall.REQUEST_CONFIRMATION_FUNCTION_CALL_NAME,
                            args = mapOf(
                                // ADK 1.0.1 收为 internal 的 JSON key 常量，字面量与构件内值一致
                                "originalFunctionCall" to mapOf(
                                    "name" to toolName,
                                    "args" to args,
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
    fun testOnInputChange_updatesInput() = runTest(mainDispatcher.scheduler) {
        viewModel.onInputChange("今天练胸")
        assertEquals("今天练胸", viewModel.uiState.value.input)
    }

    /** 空白输入不可发送：不产生消息、不触达引擎。 */
    @Test
    fun testSend_blankInput_doesNothing() = runTest(mainDispatcher.scheduler) {
        viewModel.onInputChange("   ")
        viewModel.send()

        val state = viewModel.uiState.value
        assertTrue(state.messages.isEmpty())
        assertFalse(state.isSending)
        assertTrue(fakeEngine.sentTexts.isEmpty())
    }

    /** 成功发送：用户消息立即上屏、输入框清空、isSending 复位、回复追加且 id 唯一。 */
    @Test
    fun testSend_success_appendsReplyAndResetsState() = runTest(mainDispatcher.scheduler) {
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
    fun testSend_engineFailure_showsErrorMessage() = runTest(mainDispatcher.scheduler) {
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
    fun testSend_streamException_showsErrorMessageNotCrash() = runTest(mainDispatcher.scheduler) {
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
    fun testSend_errorEvent_showsErrorMessage() = runTest(mainDispatcher.scheduler) {
        fakeEngine.sendHandler = {
            Result.success(flow<Event> { emit(Event(author = "model", errorMessage = "HTTP 429")) })
        }

        viewModel.onInputChange("你好")
        viewModel.send()

        assertEquals("HTTP 429", viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isSending)
    }

    /**
     * 错误事件作废运行：错误前已记的步骤就地删除（防孤儿步骤永久累积）。
     * 错误分支置空 activeRun 后流结束块的 runId 守卫不会再走收尾，
     * 删除必须在错误分支内完成。
     */
    @Test
    fun testSend_errorEvent_deletesOrphanSteps() = runTest(mainDispatcher.scheduler) {
        fakeEngine.sendHandler = {
            Result.success(
                flow<Event> {
                    emit(toolCallStepEvent())
                    emit(Event(author = "model", errorMessage = "HTTP 429"))
                },
            )
        }

        viewModel.onInputChange("你好")
        viewModel.send()

        assertEquals("HTTP 429", viewModel.uiState.value.errorMessage)
        // 步骤确实落过库（stepCount=1）且其 runId 已被删除清理
        assertEquals(1, fakeChatRepository.stepCount)
        assertEquals(1, fakeChatRepository.deletedRunIds.size)
        assertEquals(0, fakeChatRepository.stepCountForRuns().values.sum())
    }

    /** 流无声结束且无最终回答：兜底提示落位，孤儿步骤删除。 */
    @Test
    fun testSend_noFinalAnswer_deletesOrphanStepsAndExplains() = runTest(mainDispatcher.scheduler) {
        fakeEngine.sendHandler = {
            Result.success(flowOf(toolCallStepEvent()))
        }

        viewModel.onInputChange("你好")
        viewModel.send()

        assertTrue(viewModel.uiState.value.errorMessage?.contains("未生成最终回复") == true)
        assertEquals(1, fakeChatRepository.deletedRunIds.size)
        assertEquals(0, fakeChatRepository.stepCountForRuns().values.sum())
        assertFalse(viewModel.uiState.value.isSending)
    }

    /** 停止运行：挂起中已记的步骤就地删除（该 runId 永远不会再挂到消息上）。 */
    @Test
    fun testStopRun_deletesStepsOfInterruptedRun() = runTest(mainDispatcher.scheduler) {
        fakeEngine.sendHandler = {
            Result.success(
                flow<Event> {
                    emit(toolCallStepEvent())
                    awaitCancellation()
                },
            )
        }

        viewModel.onInputChange("你好")
        viewModel.send()
        assertTrue(viewModel.uiState.value.isSending)
        assertEquals(1, fakeChatRepository.stepCount)

        viewModel.stopRun()

        assertFalse(viewModel.uiState.value.isSending)
        assertEquals(1, fakeChatRepository.deletedRunIds.size)
        assertEquals(0, fakeChatRepository.stepCountForRuns().values.sum())
    }

    /** 发送过程中再次发送被忽略（防重复并发请求）。 */
    @Test
    fun testSend_whileSending_secondSendIgnored() = runTest(mainDispatcher.scheduler) {
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
    fun testConfirmationFlow_pausesAndResumes() = runTest(mainDispatcher.scheduler) {
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
    fun testOnErrorShown_clearsErrorMessage() = runTest(mainDispatcher.scheduler) {
        viewModel.onInputChange("你好")
        viewModel.send()
        assertNotNull(viewModel.uiState.value.errorMessage)

        viewModel.onErrorShown()
        assertNull(viewModel.uiState.value.errorMessage)
    }

    /** init 时回放持久化历史：消息按序上屏、展示 id 从 1 起唯一递增。 */
    @Test
    fun testInit_replaysPersistedHistory() = runTest(mainDispatcher.scheduler) {
        fakeEngine.history = listOf(
            com.example.fitlog.model.ai.ChatMessage(role = "user", content = "查体重"),
            com.example.fitlog.model.ai.ChatMessage(role = "assistant", content = "72.5kg"),
        )
        val vm = ChatViewModel(fakeEngine, fakeChatRepository)

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
    fun testOnClearChat_clearsMessagesAndSession() = runTest(mainDispatcher.scheduler) {
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
        // 本地两张表与 ADK 会话一并清理，否则重启后消息"复活"而模型已失忆
        assertEquals(1, fakeChatRepository.clearCount)
        assertEquals(0L, fakeChatRepository.count())
        assertEquals(0, fakeChatRepository.stepCount)
    }

    /**
     * 清空中发送：发送操作被拦截忽略，不发往引擎也不产生新消息，清空完成后不误删新消息。
     */
    @Test
    fun testClearChat_sendDuringClear_ignored() = runTest(mainDispatcher.scheduler) {
        fakeEngine.sendHandler = { Result.success(flowOf(finalTextEvent("第一条回复"))) }
        viewModel.onInputChange("第一条消息")
        viewModel.send()
        assertEquals(2, viewModel.uiState.value.messages.size)

        // 挂起清空操作，模拟清空在途
        val clearLatch = CompletableDeferred<Unit>()
        fakeEngine.clearSessionHandler = {
            clearLatch.await()
            Result.success(Unit)
        }

        viewModel.onClearChat()
        assertTrue(viewModel.uiState.value.isClearing)

        // 清空中尝试发送新消息
        viewModel.onInputChange("清空中发送的新消息")
        viewModel.send()

        // 验证：新发送被忽略，未向引擎发送，也未进入发送态
        assertFalse(viewModel.uiState.value.isSending)
        assertEquals(1, fakeEngine.sentTexts.size)

        // 放行清空完成
        clearLatch.complete(Unit)

        // 验证：清空正常完成，旧消息清空，且 isClearing 复位
        val state = viewModel.uiState.value
        assertFalse(state.isClearing)
        assertTrue(state.messages.isEmpty())
        assertEquals(1, fakeChatRepository.clearCount)
    }

    /**
     * 重复清空：清空在途期间再次触发清空被忽略，防止并发重复清空。
     */
    @Test
    fun testClearChat_duplicateClear_ignored() = runTest(mainDispatcher.scheduler) {
        val clearLatch = CompletableDeferred<Unit>()
        fakeEngine.clearSessionHandler = {
            clearLatch.await()
            Result.success(Unit)
        }

        viewModel.onClearChat()
        assertTrue(viewModel.uiState.value.isClearing)
        assertEquals(1, fakeEngine.clearedSessions.size)

        // 再次触发清空
        viewModel.onClearChat()
        assertEquals(1, fakeEngine.clearedSessions.size)

        // 放行第一次清空
        clearLatch.complete(Unit)
        assertFalse(viewModel.uiState.value.isClearing)
        assertEquals(1, fakeEngine.clearedSessions.size)
        assertEquals(1, fakeChatRepository.clearCount)
    }

    /**
     * 清空成功：旧消息与状态清空，且清空后可继续正常发送新消息。
     */
    @Test
    fun testClearChat_success_clearsMessagesAndEnablesNewOperations() = runTest(mainDispatcher.scheduler) {
        fakeEngine.sendHandler = { Result.success(flowOf(finalTextEvent("回复 1"))) }
        viewModel.onInputChange("消息 1")
        viewModel.send()
        assertEquals(2, viewModel.uiState.value.messages.size)

        viewModel.onClearChat()

        val state = viewModel.uiState.value
        assertTrue(state.messages.isEmpty())
        assertFalse(state.isClearing)
        assertFalse(state.isSending)
        assertNull(state.errorMessage)
        assertNull(state.pendingConfirmation)
        assertEquals("", state.input)
        assertEquals(1, fakeChatRepository.clearCount)
        assertEquals(0L, fakeChatRepository.count())

        // 清空后可继续使用
        fakeEngine.sendHandler = { Result.success(flowOf(finalTextEvent("清空后的新回复"))) }
        viewModel.onInputChange("清空后的新消息")
        viewModel.send()

        val afterState = viewModel.uiState.value
        assertEquals(2, afterState.messages.size)
        assertEquals("清空后的新消息", afterState.messages[0].content)
        assertEquals("清空后的新回复", afterState.messages[1].content)
    }

    /**
     * ADK 失败：界面不误清空，保留已有消息并提示错误，清空态复位且后继可继续使用。
     */
    @Test
    fun testClearChat_adkFailure_retainsMessagesAndShowsError() = runTest(mainDispatcher.scheduler) {
        fakeEngine.sendHandler = { Result.success(flowOf(finalTextEvent("回复 1"))) }
        viewModel.onInputChange("消息 1")
        viewModel.send()
        assertEquals(2, viewModel.uiState.value.messages.size)

        fakeEngine.clearSessionHandler = {
            Result.failure(IOException("ADK 服务不可用"))
        }

        viewModel.onClearChat()

        val state = viewModel.uiState.value
        // 界面状态与实际状态一致：未清空本地数据，消息保留
        assertEquals(2, state.messages.size)
        assertFalse(state.isClearing)
        assertEquals(0, fakeChatRepository.clearCount)
        assertNotNull(state.errorMessage)
        assertTrue(state.errorMessage!!.contains("ADK 服务不可用"))

        // 错误后可继续使用
        viewModel.onErrorShown()
        assertNull(viewModel.uiState.value.errorMessage)
        fakeEngine.sendHandler = { Result.success(flowOf(finalTextEvent("错误后的回复"))) }
        viewModel.onInputChange("新消息")
        viewModel.send()
        assertEquals(4, viewModel.uiState.value.messages.size)
    }

    /**
     * 本地删除失败：界面反馈与实际数据一致（保留消息），提示错误，清空态复位且后继可继续使用。
     */
    @Test
    fun testClearChat_localDeleteFailure_retainsMessagesAndShowsError() = runTest(mainDispatcher.scheduler) {
        fakeEngine.sendHandler = { Result.success(flowOf(finalTextEvent("回复 1"))) }
        viewModel.onInputChange("消息 1")
        viewModel.send()
        assertEquals(2, viewModel.uiState.value.messages.size)

        fakeChatRepository.clearAllHandler = {
            throw java.sql.SQLException("本地数据库写入失败")
        }

        viewModel.onClearChat()

        val state = viewModel.uiState.value
        // 界面消息保留，与本地 DB 实际数据一致（未被假清空）
        assertEquals(2, state.messages.size)
        assertFalse(state.isClearing)
        assertEquals(1, fakeChatRepository.clearCount)
        assertEquals(2L, fakeChatRepository.count())
        assertNotNull(state.errorMessage)
        assertTrue(state.errorMessage!!.contains("本地聊天记录删除失败"))

        // 错误后可继续使用
        viewModel.onErrorShown()
        assertNull(viewModel.uiState.value.errorMessage)
    }

    /**
     * 历史恢复在途时清空对话，恢复完成不会覆盖清空后的会话状态。
     */
    @Test
    fun testRestoreHistory_doesNotOverwriteClearedSession() = runTest(mainDispatcher.scheduler) {
        fakeEngine.history = listOf(
            com.example.fitlog.model.ai.ChatMessage(role = "user", content = "旧历史消息"),
            com.example.fitlog.model.ai.ChatMessage(role = "assistant", content = "旧历史回复"),
        )
        val restoreLatch = CompletableDeferred<Unit>()
        val repo = FakeChatRepository().apply {
            loadThreadHandler = {
                restoreLatch.await()
                null
            }
        }
        val vm = ChatViewModel(fakeEngine, repo)

        // 在恢复历史挂起期间执行清空
        vm.onClearChat()
        assertTrue(vm.uiState.value.messages.isEmpty())

        // 放行历史恢复
        restoreLatch.complete(Unit)

        // 历史恢复不得覆盖清空后的状态
        val state = vm.uiState.value
        assertTrue(state.messages.isEmpty())
        assertFalse(state.isClearing)
    }
}
