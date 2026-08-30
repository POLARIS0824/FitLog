package com.example.fitlog.feature.chat

import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fitlog.data.local.dao.AgentStepDao
import com.example.fitlog.data.local.dao.ChatMessageDao
import com.example.fitlog.data.local.entity.chat.AgentStepEntity
import com.example.fitlog.data.local.entity.chat.ChatMessageEntity
import com.example.fitlog.feature.agent.engine.AgentEngine
import com.example.fitlog.model.ai.ChatMessage
import com.google.adk.kt.events.Event
import com.google.adk.kt.types.FunctionCall
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * AI 教练对话页的 ViewModel，驱动 ADK Agent（[AgentEngine]）。
 *
 * ## 与旧版（纯文本聊天）的区别
 *
 * 消息不再直接发给 AI 服务商，而是进入 ADK agent 管线：模型可以调用
 * [com.example.fitlog.feature.agent.tools.FitnessTools] 查询用户真实数据再作答；
 * 涉及写操作的工具（记体重 / 切计划）会先暂停等待用户确认（见 [PendingConfirmation]）。
 *
 * ## 会话模型
 *
 * 单页面模式：固定 sessionId。UI 历史由本地 Room（chat_messages / agent_steps）持久化，
 * 模型上下文由 ADK 的 RoomSessionService 持久化；进程重启后两边各自延续。
 *
 * ## 过程时间线
 *
 * 每次发送开启一轮运行（runId = UUID），[collectAgentEvents] 逐事件把
 * 中间说明文本 / 工具调用 / 确认请求记为步骤（即时落库 agent_steps）；
 * 最终回答落库 chat_messages 并携带同一 runId，重启后步骤仍能挂回消息。
 * 计时为暂停感知：确认弹框等待用户的时间不计入活跃耗时。
 *
 * ## 事件流消费
 *
 * [collectAgentEvents] 逐条消费 ADK [Event]：
 * 1. 含 `adk_request_confirmation` 调用的事件 → 记确认步骤、暂停计时、置 [PendingConfirmation] 弹框；
 * 2. `errorMessage` → 统一错误提示（运行作废，步骤成为无挂载孤儿数据）；
 * 3. 中间轮文本 → 思考步骤；工具调用 → 工具步骤（含与文本同轮的调用）；
 * 4. 最终回复（[Event.isFinalResponse] 且非 partial）→ 消息落库上屏，运行收尾。
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val agentEngine: AgentEngine,
    private val chatMessageDao: ChatMessageDao,
    private val agentStepDao: AgentStepDao,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    /** 单页面模式：固定会话 id，模型上下文经 ADK RoomSessionService 持久化，重启后延续。 */
    private val sessionId = "main_chat"

    /** DB 落库失败时的兜底展示 id（负数递减，不与 Room 自增 id 冲突或重复）。 */
    private var fallbackId = -1L

    // ── 运行计时（暂停感知：分段累计，确认等待期间不计入）──

    /** 已完成时间段的累计活跃耗时。 */
    private var accumulatedActiveMs = 0L

    /** 当前时间段起点（SystemClock.elapsedRealtime）；null = 暂停中。 */
    private var segmentStartElapsed: Long? = null

    /** 每秒刷新 activeRun.activeMs 的计时协程。 */
    private var tickerJob: Job? = null

    init {
        // 进程重启后恢复历史：本地库优先（含时间线）；
        // 本地为空而 ADK 有历史（老版本升级首启）时做一次性 seed。
        // 首帧不等 DB：先渲染空列表，恢复完成后补上（与旧行为一致的异步恢复）。
        viewModelScope.launch { restoreHistory() }
    }

    /**
     * 输入框文本变化事件
     */
    fun onInputChange(text: String) {
        _uiState.update { it.copy(input = text) }
    }

    /**
     * 发送按钮点击事件：进入 ADK agent 管线，并开启一轮新的 Agent 运行。
     */
    fun send() {
        // 空白或者正在发送中不可发送
        val input = _uiState.value.input.trim()
        if (input.isEmpty()) return
        if (_uiState.value.isSending) return
        // 待确认期间不可发起新运行：悬空的确认调用必须先被回答（允许/拒绝），
        // 直接丢弃会毒化会话（模态弹框下正常不可达，此为键盘/无障碍等旁路的防御）
        if (_uiState.value.pendingConfirmation != null) return

        // 立即进入发送态并清残留状态；用户消息行与时间线在落库后上屏（毫秒级延迟）。
        _uiState.update {
            it.copy(
                input = "",
                isSending = true,
                errorMessage = null,
                pendingConfirmation = null,
                activeRun = null,
            )
        }

        viewModelScope.launch {
            val runId = startNewRun()
            val entity = ChatMessageEntity(
                role = "user",
                content = input,
                runId = null,
                durationMs = null,
                createdAt = System.currentTimeMillis(),
            )
            val messageId = runCatching { chatMessageDao.insert(entity) }
                .onFailure { Log.w(TAG, "用户消息落库失败", it) }
                .getOrDefault(nextFallbackId())
            _uiState.update {
                it.copy(
                    messages = it.messages + ChatUiMessage(id = messageId, role = "user", content = input),
                    activeRun = ActiveRun(runId = runId),
                )
            }
            agentEngine.sendMessage(sessionId, input)
                .onSuccess { collectAgentEvents(it, runId) }
                .onFailure { onEngineError(it) }
        }
    }

    /**
     * 用户对确认框的选择（同意/拒绝）。
     *
     * 确认是同一轮运行的延续：复用 activeRun 的 runId，计时恢复走行。
     *
     * @param confirmed true=允许执行工具；false=拒绝（模型会向用户解释）
     */
    fun respondToConfirmation(confirmed: Boolean) {
        val pending = _uiState.value.pendingConfirmation ?: return
        val runId = _uiState.value.activeRun?.runId ?: startNewRun()
        _uiState.update {
            it.copy(
                pendingConfirmation = null,
                isSending = true,
                activeRun = it.activeRun?.copy(awaitingConfirmation = false),
            )
        }
        resumeRunTiming()
        // 确认续传失败会停掉 ticker，重试恢复计时时要一并重启，否则头部秒数冻结
        startTicker()

        viewModelScope.launch {
            agentEngine.respondToConfirmation(sessionId, pending.callId, confirmed)
                .onSuccess { collectAgentEvents(it, runId) }
                .onFailure { error ->
                    onEngineError(error, clearActiveRun = false)
                    // 引擎失败时确认响应未写入会话，必须恢复确认框让用户重试——
                    // 若就此丢弃，原始 tool_call 将悬空（其后每轮请求 400，
                    // 会话毒化且只能手动清空）
                    _uiState.update { it.copy(pendingConfirmation = pending) }
                }
        }
    }

    /**
     * 消费一轮 ADK 事件流：确认请求 → 弹框暂停；中间过程 → 时间线步骤；
     * 最终文本 → 上屏；错误 → 提示。
     *
     * ADK 的事件流本身可能抛异常（工具执行失败、服务商中途断流、会话库 IO 错误等），
     * 因此整个收集过程包在 try/catch 中：异常转为 UI 错误提示而非未捕获协程异常
     * （后者会直接闪退）。[CancellationException] 按协程取消语义原样上抛。
     */
    private suspend fun collectAgentEvents(events: Flow<Event>, runId: String) {
        var sawConfirmation = false
        var sawAssistantOutput = false // 见流结束时兜底判断
        var stepOrder = 0
        try {
            events.collect { event ->
                // 1) 确认请求：ADK 已暂停本轮，记步骤 + 弹确认框 + 暂停计时
                val confirmationCall = event.functionCalls()
                    .firstOrNull { it.name == FunctionCall.REQUEST_CONFIRMATION_FUNCTION_CALL_NAME }
                if (confirmationCall != null) {
                    sawConfirmation = true
                    sawAssistantOutput = true
                    pauseRunTiming()
                    val original = confirmationCall.args[FunctionCall.ORIGINAL_FUNCTION_CALL_KEY]
                        as? Map<*, *>
                    val toolName = original?.get(FunctionCall.NAME_KEY) as? String
                        ?: confirmationCall.name
                    val toolArgs = original?.get(FunctionCall.ARGS_KEY) as? Map<String, Any?>
                        ?: emptyMap()
                    addStep(
                        runId = runId,
                        order = stepOrder++,
                        type = AgentStepType.CONFIRM_REQUEST,
                        toolKey = toolName,
                        label = AgentStepFormatter.toolLabel(toolName),
                        detail = AgentStepFormatter.argsSummary(toolName, toolArgs),
                    )
                    _uiState.update {
                        it.copy(
                            isSending = false,
                            activeRun = it.activeRun?.copy(awaitingConfirmation = true),
                            pendingConfirmation = PendingConfirmation(
                                callId = confirmationCall.id ?: "",
                                toolName = toolName,
                                args = toolArgs,
                            ),
                        )
                    }
                    return@collect
                }

                // 2) 引擎/服务商错误：运行作废（已记步骤成为无挂载孤儿数据）
                event.errorMessage?.let { msg ->
                    sawAssistantOutput = true
                    _uiState.update { it.copy(errorMessage = msg, isSending = false, activeRun = null) }
                    return@collect
                }

                val text = event.content?.parts?.mapNotNull { it.text }
                    ?.joinToString("") ?: ""
                val toolCalls = event.functionCalls()
                val isFinal = event.isFinalResponse && !event.partial

                // 3) 工具调用 → 时间线步骤。含与文本同轮的调用——
                //    旧版只在纯工具轮展示"（调用工具：…）"，混排时调用完全不可见
                if (!isFinal && !event.partial && toolCalls.isNotEmpty()) {
                    sawAssistantOutput = true
                    for (call in toolCalls) {
                        addStep(
                            runId = runId,
                            order = stepOrder++,
                            type = AgentStepType.TOOL_CALL,
                            toolKey = call.name,
                            label = AgentStepFormatter.toolLabel(call.name),
                            detail = AgentStepFormatter.argsSummary(call.name, call.args),
                        )
                    }
                }

                // 4) 模型输出：最终轮 → 消息上屏；中间轮文本 → 思考步骤（不再作为消息）
                if (text.isNotBlank()) {
                    sawAssistantOutput = true
                    if (isFinal) {
                        finalizeAnswer(runId, text)
                    } else if (!event.partial) {
                        addStep(
                            runId = runId,
                            order = stepOrder++,
                            type = AgentStepType.THINKING,
                            toolKey = null,
                            label = text.trim(),
                            detail = null,
                        )
                    }
                }
            }

            // 流结束：确认中 → 等用户决定（计时已暂停，运行保持）；其余收尾
            if (!sawConfirmation && !sawAssistantOutput) {
                _uiState.update {
                    it.copy(errorMessage = "本轮对话未产生回复（可能已达到工具调用步数上限），请重试或换个问法")
                }
            }
            if (!sawConfirmation) {
                stopRunTiming()
                _uiState.update { it.copy(isSending = false, activeRun = null) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onEngineError(e)
        }
    }

    /**
     * 最终回答落库上屏：消息携带 runId 与本轮活跃耗时，activeRun 的步骤
     * 随消息固定，时间线卡片转为折叠态。
     */
    private suspend fun finalizeAnswer(runId: String, text: String) {
        val durationMs = stopRunTiming()
        val steps = _uiState.value.activeRun?.steps ?: emptyList()
        val entity = ChatMessageEntity(
            role = "assistant",
            content = text,
            runId = runId,
            durationMs = durationMs,
            createdAt = System.currentTimeMillis(),
        )
        val messageId = runCatching { chatMessageDao.insert(entity) }
            .onFailure { Log.w(TAG, "最终回答落库失败", it) }
            .getOrDefault(nextFallbackId())
        _uiState.update {
            it.copy(
                messages = it.messages + ChatUiMessage(
                    id = messageId,
                    role = "assistant",
                    content = text,
                    steps = steps,
                    durationMs = durationMs,
                ),
                activeRun = null,
                isSending = false,
            )
        }
    }

    /**
     * 记录一步过程并即时落库（写失败仅记日志：时间线是辅助信息，
     * 不应因持久化故障中断对话）。步骤同时追加进 activeRun 供 UI 实时渲染。
     */
    private suspend fun addStep(
        runId: String,
        order: Int,
        type: AgentStepType,
        toolKey: String?,
        label: String,
        detail: String?,
    ) {
        val elapsedMs = currentRunElapsedMs()
        val entity = AgentStepEntity(
            runId = runId,
            stepOrder = order,
            type = type.storageValue,
            toolKey = toolKey,
            label = label,
            detail = detail,
            elapsedMs = elapsedMs,
            createdAt = System.currentTimeMillis(),
        )
        val id = runCatching { agentStepDao.insert(entity) }
            .onFailure { Log.w(TAG, "过程步骤落库失败", it) }
            .getOrDefault(nextFallbackId())
        val step = AgentStepUi(
            id = id,
            type = type,
            toolKey = toolKey,
            label = label,
            detail = detail,
            elapsedMs = elapsedMs,
        )
        _uiState.update { state ->
            val run = state.activeRun ?: return@update state
            state.copy(activeRun = run.copy(steps = run.steps + step))
        }
    }

    /** 开启一轮新运行：生成 runId 并重置计时。 */
    private fun startNewRun(): String {
        startRunTiming()
        return UUID.randomUUID().toString()
    }

    // ── 暂停感知计时 ──

    /** 本轮运行的当前活跃耗时（毫秒）。 */
    private fun currentRunElapsedMs(): Long =
        accumulatedActiveMs + (segmentStartElapsed?.let { SystemClock.elapsedRealtime() - it } ?: 0L)

    private fun startRunTiming() {
        accumulatedActiveMs = 0L
        segmentStartElapsed = SystemClock.elapsedRealtime()
        startTicker()
    }

    /**
     * 启动每秒刷新协程。循环不因 activeRun 短暂为 null 退出（落库间隙会短暂无运行），
     * 统一由 [stopRunTiming] / 新运行启动时取消。
     */
    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            while (isActive) {
                delay(1_000)
                if (_uiState.value.activeRun?.awaitingConfirmation == true) continue
                _uiState.update { state ->
                    state.copy(activeRun = state.activeRun?.copy(activeMs = currentRunElapsedMs()))
                }
            }
        }
    }

    private fun pauseRunTiming() {
        segmentStartElapsed?.let {
            accumulatedActiveMs += SystemClock.elapsedRealtime() - it
            segmentStartElapsed = null
        }
    }

    private fun resumeRunTiming() {
        if (segmentStartElapsed == null) segmentStartElapsed = SystemClock.elapsedRealtime()
    }

    /** 结束计时并停掉每秒刷新，返回本轮累计活跃耗时。 */
    private fun stopRunTiming(): Long {
        pauseRunTiming()
        tickerJob?.cancel()
        tickerJob = null
        return accumulatedActiveMs
    }

    /** 引擎初始化/发送失败（未配置服务商等）。[clearActiveRun]=false 用于确认续传失败（运行要恢复等待态）。 */
    private fun onEngineError(error: Throwable, clearActiveRun: Boolean = true) {
        stopRunTiming()
        _uiState.update {
            it.copy(
                isSending = false,
                activeRun = if (clearActiveRun) null else it.activeRun?.copy(awaitingConfirmation = true),
                errorMessage = error.message ?: "Agent 请求失败",
            )
        }
    }

    /**
     * 清空对话：删除 ADK 会话与本地两张表并重置 UI。
     *
     * 也是协议坏历史（悬空 tool_call 等）的唯一自愈入口——用户遇到持续报错时
     * 可借此恢复可用状态。
     *
     * 删除失败时回滚 UI：否则界面已清空而模型上下文仍在，下一条消息
     * AI 仍"记得"被"清空"的对话，界面与模型状态分叉。
     */
    fun onClearChat() {
        if (_uiState.value.isSending) return
        // 待确认等待期间清空可达：一并停掉计时，防协程泄漏
        stopRunTiming()
        val previous = _uiState.value
        _uiState.update {
            it.copy(
                messages = emptyList(),
                errorMessage = null,
                pendingConfirmation = null,
                input = "",
                activeRun = null,
            )
        }
        viewModelScope.launch {
            agentEngine.clearSession(sessionId)
                .onSuccess {
                    // 本地历史与 ADK 会话一并清除，否则重启后消息"复活"而模型已失忆
                    runCatching {
                        chatMessageDao.clearAll()
                        agentStepDao.clearAll()
                    }.onFailure { Log.w(TAG, "聊天记录本地清理失败", it) }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            messages = previous.messages,
                            pendingConfirmation = previous.pendingConfirmation,
                            input = previous.input,
                            activeRun = previous.activeRun,
                            errorMessage = error.message ?: "清空会话失败，请重试",
                        )
                    }
                }
        }
    }

    /**
     * 错误提示展示完毕后调用，清除一次性错误状态（UI 事件命名，供 Snackbar 接线）。
     */
    fun onErrorShown() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    // ── 历史恢复 ──

    /** 启动恢复：本地库优先；本地为空且 ADK 有历史时做一次性 seed（老版本升级路径）。 */
    private suspend fun restoreHistory() {
        val localCount = runCatching { chatMessageDao.count() }
            .onFailure { Log.w(TAG, "读取聊天记录数失败", it) }
            .getOrDefault(0L)
        if (localCount == 0L) seedFromAdkHistory()

        val restored = runCatching { loadMessages() }
            .getOrElse { Log.w(TAG, "读取聊天历史失败", it); emptyList() }
        _uiState.update { state ->
            // init 与 send 并发的防御：若用户已发出新消息，不覆盖现场
            if (state.messages.isNotEmpty()) return@update state
            state.copy(messages = restored)
        }
    }

    /** 从 ADK 会话回放 seed 本地消息表（时间线功能上线前的历史无步骤可挂）。 */
    private suspend fun seedFromAdkHistory() {
        val history: List<ChatMessage> = runCatching { agentEngine.replayHistory(sessionId) }
            .onFailure { Log.w(TAG, "ADK 历史回放失败", it) }
            .getOrDefault(emptyList())
        if (history.isEmpty()) return
        // 时间戳整体回溯一个消息数区间（而非从"现在"起算）：seed 进行期间用户可能已
        // 发出新消息（createdAt 为当下），回溯可保证 seed 的旧历史始终排在新消息之前
        val base = System.currentTimeMillis() - history.size
        history.forEachIndexed { index, message ->
            val entity = ChatMessageEntity(
                role = message.role,
                content = message.content,
                runId = null,
                durationMs = null,
                createdAt = base + index,
            )
            runCatching { chatMessageDao.insert(entity) }
                .onFailure { Log.w(TAG, "历史消息 seed 落库失败", it) }
        }
    }

    /** 本地消息 + 按 runId 挂载的时间线步骤。 */
    private suspend fun loadMessages(): List<ChatUiMessage> {
        val messages = chatMessageDao.getAll()
        val stepsByRun = agentStepDao.getAll().groupBy { it.runId }
        return messages.map { entity ->
            ChatUiMessage(
                id = entity.id,
                role = entity.role,
                content = entity.content,
                steps = entity.runId
                    ?.let { runId ->
                        stepsByRun[runId].orEmpty().map { s ->
                            AgentStepUi(
                                id = s.id,
                                type = AgentStepType.fromStorageValue(s.type),
                                toolKey = s.toolKey,
                                label = s.label,
                                detail = s.detail,
                                elapsedMs = s.elapsedMs,
                            )
                        }
                    }
                    ?: emptyList(),
                durationMs = entity.durationMs,
            )
        }
    }

    private fun nextFallbackId(): Long = fallbackId--

    companion object {
        private const val TAG = "ChatViewModel"
    }
}
