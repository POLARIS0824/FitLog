package com.example.fitlog.data.agent

import com.example.fitlog.data.local.dao.UserProfileDao
import com.example.fitlog.data.local.entity.UserProfileEntity
import com.example.fitlog.data.repository.AICompletion
import com.example.fitlog.data.repository.UserProfileRepository
import com.example.fitlog.model.ai.AgentTool
import com.example.fitlog.model.ai.ChatMessage
import com.example.fitlog.model.ai.ChatRole
import com.example.fitlog.model.ai.ToolCall
import com.example.fitlog.model.ai.ToolDefinition
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * [AgentOrchestrator] 的单元测试：脚本化 fake LLM + fake 工具，验证 agent loop 行为。
 */
class AgentOrchestratorTest {

    /** 脚本化的 LLM fake：按队列依次返回响应，并记录每次收到的消息。 */
    private class FakeChatCompletionClient(
        private val responses: MutableList<Result<AICompletion>>,
    ) : ChatCompletionClient {
        val requests = mutableListOf<List<ChatMessage>>()

        override suspend fun chatCompletion(
            messages: List<ChatMessage>,
            tools: List<ToolDefinition>?,
        ): Result<AICompletion> {
            requests += messages
            return responses.removeFirst()
        }
    }

    /** 脚本化的工具 fake：可配置返回结果或抛异常。 */
    private class FakeAgentTool(
        override val name: String = "fake_tool",
        private val result: String = """{"ok": true}""",
        private val throws: Boolean = false,
    ) : AgentTool {
        override val description = "测试工具"
        override val parametersSchema: JsonObject = buildJsonObject {}

        override suspend fun execute(arguments: JsonObject): String {
            if (throws) throw IllegalStateException("boom")
            return result
        }
    }

    /** getFirst() 恒 null 的 UserProfileDao fake（AgentPromptBuilder 链路用）。 */
    private class FakeUserProfileDao : UserProfileDao {
        override suspend fun insert(userProfileEntity: UserProfileEntity) {}
        override suspend fun update(userProfileEntity: UserProfileEntity) {}
        override suspend fun delete(userProfileEntity: UserProfileEntity) {}
        override suspend fun getById(id: Long): UserProfileEntity? = null
        override suspend fun getFirst(): UserProfileEntity? = null
    }

    private fun createOrchestrator(
        client: FakeChatCompletionClient,
        tools: List<AgentTool>,
    ): AgentOrchestrator {
        val promptBuilder = AgentPromptBuilder(UserProfileRepository(FakeUserProfileDao()))
        return AgentOrchestrator(
            chatClient = client,
            toolRegistry = AgentToolRegistry(tools.toSet()),
            promptBuilder = promptBuilder,
            json = Json { ignoreUnknownKeys = true },
        )
    }

    private fun assistantWithToolCall(call: ToolCall): AICompletion = AICompletion(
        ChatMessage(role = ChatRole.ASSISTANT, content = null, toolCalls = listOf(call)),
        finishReason = "tool_calls",
    )

    private fun assistantText(text: String): AICompletion = AICompletion(
        ChatMessage(role = ChatRole.ASSISTANT, content = text),
        finishReason = "stop",
    )

    private val userMessage = ChatMessage(role = ChatRole.USER, content = "我卧推最高多少？")

    @Test
    fun `direct text reply ends loop without events`() = runTest {
        val client = FakeChatCompletionClient(
            mutableListOf(Result.success(assistantText("你好，我是你的教练")))
        )
        val orchestrator = createOrchestrator(client, emptyList())
        val events = mutableListOf<AgentEvent>()

        val turn = orchestrator.run(listOf(userMessage)) { events += it }.getOrThrow()

        assertEquals("你好，我是你的教练", turn.finalReply.content)
        assertEquals(1, turn.newMessages.size)
        assertTrue(events.isEmpty())
        // system prompt 被注入到请求开头
        assertEquals(ChatRole.SYSTEM, client.requests[0][0].role)
    }

    @Test
    fun `tool call round trip feeds result back and completes`() = runTest {
        val call = ToolCall(id = "c1", name = "fake_tool", argumentsJson = "{}")
        val client = FakeChatCompletionClient(
            mutableListOf(
                Result.success(assistantWithToolCall(call)),
                Result.success(assistantText("你卧推最高 85kg")),
            )
        )
        val orchestrator = createOrchestrator(client, listOf(FakeAgentTool()))
        val events = mutableListOf<AgentEvent>()

        val turn = orchestrator.run(listOf(userMessage)) { events += it }.getOrThrow()

        // 事件序列：Started → Finished(success)
        assertEquals(
            listOf(AgentEvent.ToolCallStarted(call), AgentEvent.ToolCallFinished(call, true)),
            events,
        )
        // 第二次请求携带 role=tool 的结果消息，tool_call_id 回指
        val toolMessage = client.requests[1].first { it.role == ChatRole.TOOL }
        assertEquals("c1", toolMessage.toolCallId)
        assertTrue(toolMessage.content!!.contains("ok"))
        // newMessages：assistant(tool_calls) + tool + assistant(stop)，多轮对话不丢上下文
        assertEquals(3, turn.newMessages.size)
        assertEquals("你卧推最高 85kg", turn.finalReply.content)
    }

    @Test
    fun `tool exception is fed back as error and marked failed`() = runTest {
        val call = ToolCall(id = "c1", name = "fake_tool", argumentsJson = "{}")
        val client = FakeChatCompletionClient(
            mutableListOf(
                Result.success(assistantWithToolCall(call)),
                Result.success(assistantText("查询出了点问题")),
            )
        )
        val orchestrator = createOrchestrator(client, listOf(FakeAgentTool(throws = true)))
        val events = mutableListOf<AgentEvent>()

        orchestrator.run(listOf(userMessage)) { events += it }

        assertEquals(AgentEvent.ToolCallFinished(call, false), events.last())
        val toolMessage = client.requests[1].first { it.role == ChatRole.TOOL }
        assertTrue(toolMessage.content!!.contains("error"))
        assertTrue(toolMessage.content.orEmpty().contains("boom"))
    }

    @Test
    fun `unknown tool is reported back to model`() = runTest {
        val call = ToolCall(id = "c1", name = "ghost_tool", argumentsJson = "{}")
        val client = FakeChatCompletionClient(
            mutableListOf(
                Result.success(assistantWithToolCall(call)),
                Result.success(assistantText("我无法使用这个工具")),
            )
        )
        val orchestrator = createOrchestrator(client, emptyList())

        orchestrator.run(listOf(userMessage)) {}

        val toolMessage = client.requests[1].first { it.role == ChatRole.TOOL }
        assertTrue(toolMessage.content!!.contains("unknown tool"))
    }

    @Test
    fun `max rounds circuit breaker returns fallback reply`() = runTest {
        val call = ToolCall(id = "c1", name = "fake_tool", argumentsJson = "{}")
        // LLM 永远要求调 tool → 触发 5 轮硬熔断
        val client = FakeChatCompletionClient(
            MutableList(10) { Result.success(assistantWithToolCall(call)) }
        )
        val orchestrator = createOrchestrator(client, listOf(FakeAgentTool()))
        val events = mutableListOf<AgentEvent>()

        val turn = orchestrator.run(listOf(userMessage)) { events += it }.getOrThrow()

        assertEquals(5, client.requests.size)
        assertTrue(events.last() is AgentEvent.MaxRoundsReached)
        assertTrue(turn.finalReply.content.orEmpty().contains("查询的次数太多"))
    }

    @Test
    fun `request failure propagates as failure`() = runTest {
        val client = FakeChatCompletionClient(
            mutableListOf(Result.failure(IOException("timeout")))
        )
        val orchestrator = createOrchestrator(client, emptyList())

        val result = orchestrator.run(listOf(userMessage)) {}

        assertTrue(result.isFailure)
        assertEquals("timeout", result.exceptionOrNull()?.message)
    }
}
