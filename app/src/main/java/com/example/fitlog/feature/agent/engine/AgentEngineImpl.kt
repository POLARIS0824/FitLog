package com.example.fitlog.feature.agent.engine

import android.content.Context
import com.example.fitlog.data.remote.AIApi
import com.example.fitlog.data.repository.AIProviderConfigRepository
import com.example.fitlog.data.repository.UserProfileRepository
import com.example.fitlog.data.repository.WorkoutPlanRepository
import com.example.fitlog.feature.agent.tools.FitnessTools
import com.example.fitlog.feature.agent.tools.generatedTools
import com.example.fitlog.model.ai.AIProviderConfig
import com.example.fitlog.model.ai.ChatMessage
import com.google.adk.kt.agents.Instruction
import com.google.adk.kt.agents.LlmAgent
import com.google.adk.kt.apps.App
import com.google.adk.kt.events.Event
import com.google.adk.kt.events.ToolConfirmation
import com.google.adk.kt.memory.MemoryService
import com.google.adk.kt.runners.InMemoryRunner
import com.google.adk.kt.sessions.SessionKey
import com.google.adk.kt.sessions.room.RoomSessionService
import com.google.adk.kt.tools.PreloadMemoryTool
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.FunctionCall
import com.google.adk.kt.types.FunctionResponse
import com.google.adk.kt.types.Part
import com.google.adk.kt.types.Role
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * [AgentEngine] 的默认实现：持有 agent 图与 [InMemoryRunner]，向 UI 层暴露发消息入口。
 *
 * ## 生命周期与重建
 *
 * - **惰性构建**：首次发消息时才按当前激活配置创建 [LlmAgent] + [InMemoryRunner]
 *   （模型工厂 [AgentModelFactory]：Gemini 端点走内置模型，其余走 OpenAI 兼容适配层）
 * - **配置切换重建**：以 `id + model + baseUrl + apiKey 哈希 + customEndpoint + apiVersion`
 *   为重建键，检测到变化即废弃旧 runner 重建。
 *   由于 ADK 会话与 agent 解耦（会话在 SessionService，agent 只是执行器），
 *   重建不影响既有会话历史。
 * - **会话持久化**：[RoomSessionService] 独立 SQLite（`adk_sessions.db`），
 *   与 FitLog 主库隔离；同一 (userId, sessionId) 的后续消息自动续接历史。
 *
 * ## Instruction 动态注入
 *
 * 使用 [Instruction.Provider]：每一轮 turn 都重新拉取用户资料与激活计划，
 * 保证跨天/跨配置的上下文新鲜；静态教练人设走 [LlmAgent.instruction]。
 *
 * ## 长期记忆
 *
 * [PreloadMemoryTool] 在每轮请求前以当前用户输入检索 ADK 记忆库
 * （AppSearch 持久化，数据不出设备），命中即以 `<PAST_CONVERSATIONS>` 块并入系统指令；
 * [clearSession] 删除会话前先把整段会话归档进记忆库——清空对话后教练仍记得长期要点。
 *
 * ## 未配置服务商
 *
 * [sendMessage] 返回 [Result.failure]，UI 据此展示引导卡而非发请求。
 */
class AgentEngineImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val providerConfigRepo: AIProviderConfigRepository,
    private val aiApi: AIApi,
    private val fitnessTools: FitnessTools,
    private val userProfileRepository: UserProfileRepository,
    private val workoutPlanRepository: WorkoutPlanRepository,
    private val memoryService: MemoryService,
) : AgentEngine {

    /** ADK 应用名（会话命名空间；Room 库按此 + userId + sessionId 寻址）。 */
    companion object {
        const val APP_NAME = "fitlog"
        const val USER_ID = "local_user"
        const val SESSION_DB = "adk_sessions.db"
        const val COACH_AGENT_NAME = "fitness_coach"
        /** 单轮工具调用步数上限，防模型在工具间死循环。 */
        const val MAX_STEPS = 8
        private const val TAG = "AgentEngine"
    }

    private val rebuildLock = Mutex()

    /** 会话服务（Room 持久化），进程内单例。 */
    private val sessionService by lazy {
        RoomSessionService.fromContext(context.applicationContext, SESSION_DB)
    }

    /** 当前 runner（按配置键重建；键变化时旧实例被 GC 回收）。 */
    @Volatile
    private var currentRunner: InMemoryRunner? = null

    @Volatile
    private var currentConfigKey: String? = null

    /** {@inheritDoc} */
    override suspend fun sendMessage(sessionId: String, text: String): Result<Flow<Event>> {
        val config = providerConfigRepo.activeProvider.first()
            ?: return Result.failure(
                IllegalStateException("未配置 AI 服务商，请先在设置中配置 API Key"),
            )
        config.ensureUsableCredentials()

        val runner = getOrCreateRunner(config) ?: return Result.failure(
            IllegalStateException("Agent 引擎初始化失败"),
        )

        return Result.success(
            runner.runAsync(
                userId = USER_ID,
                sessionId = sessionId,
                newMessage = Content.fromText("user", text),
            ),
        )
    }

    /**
     * 回复一次工具确认请求：确认结果以 user 角色的 FunctionResponse 回传，
     * 名称固定为 REQUEST_CONFIRMATION_FUNCTION_CALL_NAME（框架按此识别并恢复原始调用）。
     */
    override suspend fun respondToConfirmation(
        sessionId: String,
        confirmationCallId: String,
        confirmed: Boolean,
    ): Result<Flow<Event>> {
        val config = providerConfigRepo.activeProvider.first()
            ?: return Result.failure(
                IllegalStateException("未配置 AI 服务商，请先在设置中配置 API Key"),
            )
        config.ensureUsableCredentials()

        val runner = getOrCreateRunner(config) ?: return Result.failure(
            IllegalStateException("Agent 引擎初始化失败"),
        )

        val newMessage = Content(
            role = Role.USER,
            parts = listOf(
                Part(
                    functionResponse = FunctionResponse(
                        name = FunctionCall.REQUEST_CONFIRMATION_FUNCTION_CALL_NAME,
                        id = confirmationCallId,
                        response = mapOf(ToolConfirmation.CONFIRMED_KEY to confirmed),
                    ),
                ),
            ),
        )

        return Result.success(
            runner.runAsync(
                userId = USER_ID,
                sessionId = sessionId,
                newMessage = newMessage,
            ),
        )
    }

    /**
     * {@inheritDoc}
     *
     * 删除前先归档进长期记忆；归档链路（含 AppSearch 惰性初始化）失败仅记日志，
     * 不阻断删除——清空的主语义是自愈坏历史，不能因记忆库故障而失败。
     */
    override suspend fun clearSession(sessionId: String): Result<Unit> = runCatching {
        val key = SessionKey(APP_NAME, USER_ID, sessionId)
        val session = runCatching { sessionService.getSession(key) }
            .onFailure { android.util.Log.w(TAG, "归档前读取会话失败，跳过记忆归档", it) }
            .getOrNull()
        if (session != null && session.events.isNotEmpty()) {
            runCatching { memoryService.addSessionToMemory(session) }
                .onFailure { android.util.Log.w(TAG, "会话归档进长期记忆失败", it) }
        }
        sessionService.deleteSession(key)
    }.let { result ->
        // runCatching 会把取消当成失败吞掉，破坏协程取消传播，须原样上抛
        if (result.isFailure && result.exceptionOrNull() is CancellationException) {
            throw result.exceptionOrNull()!!
        }
        result
    }

    /** {@inheritDoc} */
    override suspend fun replayHistory(sessionId: String): List<ChatMessage> {
        // 同 clearSession：取消不被 runCatching 吞成"空历史"
        val session = runCatching {
            sessionService.getSession(SessionKey(APP_NAME, USER_ID, sessionId))
        }.let { result ->
            if (result.isFailure && result.exceptionOrNull() is CancellationException) {
                throw result.exceptionOrNull()!!
            }
            result.getOrNull()
        } ?: return emptyList()

        return session.events.mapNotNull { event ->
            val content = event.content
            when {
                // 用户消息 → user 消息
                event.author == "user" && content?.parts?.any { !it.text.isNullOrEmpty() } == true ->
                    ChatMessage(
                        role = "user",
                        content = content.parts.mapNotNull { it.text }.joinToString(""),
                        id = 0L,
                    )

                // 模型最终文本回复（跳过工具调用轮次的中间事件与 partial）
                event.author != "user" && event.isFinalResponse && !event.partial -> {
                    val text = content?.parts?.mapNotNull { it.text }
                        ?.joinToString("") ?: ""
                    if (text.isNotBlank()) {
                        ChatMessage(role = "assistant", content = text, id = 0L)
                    } else {
                        null
                    }
                }

                else -> null
            }
        }
    }

    /**
     * 配置键 = id + model + baseUrl + apiKey 哈希 + customEndpoint + apiVersion。
     * 任一变化都触发重建：runner 闭包捕获的是构建时的配置快照，漏掉任一字段
     * 都会出现"改了 key 但请求仍带旧值"（如只改 API Key 时一直用旧 key 直到 401）。
     * apiKey 只进哈希不进明文，避免泄漏到日志/toString。
     */
    private fun configKey(config: AIProviderConfig): String =
        "${config.id}|${config.model}|${config.baseUrl}" +
            "|${config.apiKey.sha256Prefix()}" +
            "|${config.customEndpoint ?: ""}|${config.apiVersion ?: ""}"

    /** SHA-256 取前 12 个十六进制字符，用于配置键中的 apiKey 指纹。 */
    private fun String?.sha256Prefix(): String {
        if (this.isNullOrEmpty()) return "none"
        val digest = MessageDigest.getInstance("SHA-256").digest(toByteArray(Charsets.UTF_8))
        return digest.take(6).joinToString("") { "%02x".format(it) }
    }

    /**
     * 发请求前的凭据前置校验：密钥解密失败会降级为空串入库（换机恢复等场景），
     * 若照常发请求，用户只会看到服务商 401「Invalid API key」，无从排查。
     * 此处拦截并给出明确指引。
     */
    private fun AIProviderConfig.ensureUsableCredentials() {
        if (apiKey.isBlank()) {
            throw IllegalStateException(
                "API Key 无法读取（可能因备份恢复或系统凭据变更失效），请到 AI 设置中重新保存密钥",
            )
        }
    }

    private suspend fun getOrCreateRunner(config: AIProviderConfig): InMemoryRunner? =
        rebuildLock.withLock {
            val key = configKey(config)
            currentRunner?.takeIf { currentConfigKey == key }?.let { return it }
            val runner = buildRunner(config) ?: return null
            currentRunner = runner
            currentConfigKey = key
            runner
        }

    /** 构建 agent 图与 runner（失败返回 null，如服务商不支持等可检测情形）。 */
    private suspend fun buildRunner(config: AIProviderConfig): InMemoryRunner? {
        // KSP 处理器为 FitnessTools 的每个 @Tool 方法生成 XxxTool 包装类，
        // 并生成扩展函数 FitnessTools.generatedTools() 聚合全部工具实例。
        val tools = fitnessTools.generatedTools()
        if (tools.isEmpty()) {
            // KSP 产物为空几乎必然是构建配置问题（processor 未生效），必须留痕定位
            android.util.Log.w(TAG, "generatedTools() 为空：检查 ksp(google-adk-processor) 配置")
            return null
        }

        val agent = LlmAgent(
            name = COACH_AGENT_NAME,
            model = AgentModelFactory.create(config, aiApi),
            description = "FitLog 私人健身教练：结合用户全部训练数据提供个性化建议",
            // PreloadMemoryTool：每轮请求前以当前用户输入检索长期记忆库，命中即并入系统
            // 指令（<PAST_CONVERSATIONS> 块）。declaration 为 null——不暴露给模型、不占
            // maxSteps 预算；落点是 systemInstruction，经 OpenAI 适配层合并进单条 system 消息
            tools = tools + PreloadMemoryTool(),
            instruction = Instruction { _ ->
                Content.fromText("user", buildCoachInstruction())
            },
            maxSteps = MAX_STEPS,
        )

        return InMemoryRunner(
            app = App(appName = APP_NAME, rootAgent = agent),
            sessionService = sessionService,
            memoryService = memoryService,
        )
    }

    /** 每轮动态拼装的教练指令：人设 + 数据契约 + 当前上下文快照。 */
    private suspend fun buildCoachInstruction(): String = buildString {
        appendLine("你是 FitLog 的私人健身教练，一名熟悉用户数据的专业私教。")
        appendLine("你可以通过工具查询用户的真实数据：训练记录、计划进度、体重趋势、动作库等。")
        appendLine("回答要求：")
        appendLine("- 优先使用工具获取真实数据，再基于数据给出建议；不要编造训练记录")
        appendLine("- 中文回答，语气温和专业，像了解用户的私教；不使用 emoji；不提供医疗建议")
        appendLine("- 涉及修改数据的操作（记体重、切换计划）会弹确认，用户拒绝则不要执行")
        appendLine("- 计划优先：用户有今日计划课次时，建议围绕该课次展开")
        appendLine("- 指令附带 <PAST_CONVERSATIONS> 记忆块时，将其视为与该用户过往对话的存档：自然参考其中的偏好与约定作答，不要提及记忆或存档等技术来源")
        appendLine("")
        appendLine("【当前上下文】")
        appendLine("今天是 ${java.time.LocalDate.now()}")

        runCatching { userProfileRepository.getFirst() }
            .onFailure { android.util.Log.w(TAG, "读取用户资料失败，指令缺少用户上下文", it) }
            .getOrNull()?.let { p ->
                val parts = buildList {
                    p.name?.takeIf { it.isNotBlank() }?.let { add("名字:$it") }
                    p.trainingGoal?.let { add("目标:${it.displayName()}") }
                    p.age?.let { add("年龄:$it") }
                }
                if (parts.isNotEmpty()) appendLine("用户资料：${parts.joinToString("，")}")
            }

        runCatching { workoutPlanRepository.activePlan.first() }
            .onFailure { android.util.Log.w(TAG, "读取激活计划失败，指令缺少计划上下文", it) }
            .getOrNull()?.let { plan ->
                appendLine("当前激活计划：「${plan.name}」（每周 ${plan.sessionsPerWeek} 次，共 ${plan.durationWeeks} 周）")
            }
    }

    private fun com.example.fitlog.model.user.TrainingGoal.displayName(): String = when (this) {
        com.example.fitlog.model.user.TrainingGoal.HYPERTROPHY -> "增肌"
        com.example.fitlog.model.user.TrainingGoal.FATLOSS -> "减脂"
        com.example.fitlog.model.user.TrainingGoal.STRENGTH -> "力量"
    }
}
