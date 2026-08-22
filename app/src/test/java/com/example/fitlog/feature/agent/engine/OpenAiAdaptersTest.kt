package com.example.fitlog.feature.agent.engine

import com.example.fitlog.data.remote.dto.ToolCallDto
import com.example.fitlog.data.remote.dto.FunctionCallDto
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.FunctionCall
import com.google.adk.kt.types.FunctionResponse
import com.google.adk.kt.types.Part
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [OpenAiAdapters] 的纯 JVM 单元测试。
 *
 * 重点覆盖 OpenAI 协议正确性：工具调用/响应的消息顺序契约（每条 role="tool"
 * 消息必须紧跟携带同 id tool_calls 的 assistant 消息）、确认流程历史的剥离、
 * 非法 arguments 兜底、role 分派规则（ADK 函数响应存为 user）。
 */
class OpenAiAdaptersTest {

    /** 用户纯文本内容。 */
    private fun userText(text: String) = Content.fromText("user", text)

    /** 模型纯文本内容。 */
    private fun modelText(text: String) = Content.fromText("model", text)

    /** 模型工具调用内容（模拟一轮 assistant tool_calls）。 */
    private fun modelCall(name: String, id: String?, args: Map<String, Any?> = emptyMap()) =
        Content(
            role = "model",
            parts = listOf(Part(functionCall = FunctionCall(name = name, args = args, id = id))),
        )

    /** 工具结果内容（ADK 存为 role="user"，见 OpenAiAdapters 类注释）。 */
    private fun toolResponse(name: String, id: String?, result: Map<String, Any?>) =
        Content(
            role = "user",
            parts = listOf(
                Part(
                    functionResponse = FunctionResponse(
                        name = name,
                        id = id,
                        response = result,
                    ),
                ),
            ),
        )

    /**
     * 断言消息序列满足 OpenAI 协议：每条 role="tool" 消息的 toolCallId
     * 必须出现在其前方最近的 assistant tool_calls 消息中，且二者之间
     * 不允许插入其他 assistant/tool 消息（紧跟配对）。
     */
    private fun assertToolMessagesFollowTheirCalls(messages: List<com.example.fitlog.data.remote.dto.MessageDto>) {
        var lastCallIds: Set<String>? = null // 最近一条 assistant tool_calls 的 id 集合
        messages.forEach { msg ->
            when {
                msg.role == "assistant" && msg.toolCalls != null -> {
                    lastCallIds = msg.toolCalls!!.map { it.id }.toSet()
                }

                msg.role == "tool" -> {
                    val ids = lastCallIds
                        ?: error("tool 消息（id=${msg.toolCallId}）之前没有任何 assistant tool_calls")
                    assertTrueMsg(
                        "tool 消息（id=${msg.toolCallId}）未紧跟其 assistant tool_calls（$ids）",
                        msg.toolCallId in ids,
                    )
                    lastCallIds = null // 配对完成，下一条 tool 必须有新的 tool_calls 在前
                }
            }
        }
    }

    /** 带失败消息断言条件为真。 */
    private fun assertTrueMsg(message: String, condition: Boolean) = assertTrue(message, condition)

    /** 带失败消息断言条件为假。 */
    private fun assertFalseMsg(message: String, condition: Boolean) =
        org.junit.Assert.assertFalse(message, condition)

    // ──────────────────────────────────────
    // 基础分派
    // ──────────────────────────────────────

    /** system 指令翻译为首条 system 消息；user/model 文本按 role 映射。 */
    @Test
    fun `system instruction maps to first system message`() {
        val messages = OpenAiAdapters.toOpenAiMessages(
            systemInstruction = Content.fromText("user", "你是教练"),
            contents = listOf(userText("你好"), modelText("请说")),
        )
        assertEquals("system", messages[0].role)
        assertEquals("你是教练", messages[0].content)
        assertEquals("user", messages[1].role)
        assertEquals("assistant", messages[2].role)
    }

    /** ADK 的函数响应（role="user"）必须按 part 分派为 role="tool"，而非 user 文本。 */
    @Test
    fun `function response with user role maps to tool message`() {
        val messages = OpenAiAdapters.toOpenAiMessages(
            systemInstruction = null,
            contents = listOf(
                userText("查体重"),
                modelCall("getBodyMetrics", "call-1"),
                toolResponse("getBodyMetrics", "call-1", mapOf("weightKg" to 72.5)),
                modelText("你的体重是 72.5kg"),
            ),
        )
        assertEquals(4, messages.size)
        assertEquals("assistant", messages[1].role)
        assertEquals("call-1", messages[1].toolCalls?.get(0)?.id)
        assertEquals("tool", messages[2].role)
        assertEquals("call-1", messages[2].toolCallId)
        assertEquals("assistant", messages[3].role)
        assertToolMessagesFollowTheirCalls(messages)
    }

    // ──────────────────────────────────────
    // M1：非法 arguments 兜底
    // ──────────────────────────────────────

    /**
     * 模型返回非法 arguments 时，toAdkContent 的兜底会产出 role="model" 的
     * FunctionResponse；下一轮翻译必须把它降级为 user 文本，绝不能生成
     * 引用不存在 tool_call 的 role="tool" 消息（否则严格服务商 400，
     * 且坏历史持久化后每轮必 400）。
     */
    @Test
    fun `model-role function response degrades to user text not dangling tool`() {
        // 模拟 toAdkContent(parseErrorFallback=true) 对非法 arguments 的兜底产物
        val poisoned = Content(
            role = "model",
            parts = listOf(
                Part(
                    functionResponse = FunctionResponse(
                        name = "getBodyMetrics",
                        id = "call-bad",
                        response = mapOf("error" to "arguments JSON 解析失败"),
                    ),
                ),
            ),
        )
        val messages = OpenAiAdapters.toOpenAiMessages(
            systemInstruction = null,
            contents = listOf(userText("查体重"), poisoned),
        )
        // 断言：没有任何 role="tool" 消息被生成
        assertFalseMsg(
            "role=model 的 functionResponse 必须降级，实际：$messages",
            messages.any { it.role == "tool" },
        )
    }

    /** 正常路径不受影响：role="user" 的函数响应仍翻译为 tool 消息。 */
    @Test
    fun `user-role function response still maps to tool message after M1 fix`() {
        val messages = OpenAiAdapters.toOpenAiMessages(
            systemInstruction = null,
            contents = listOf(
                modelCall("getBodyMetrics", "call-1"),
                toolResponse("getBodyMetrics", "call-1", mapOf("ok" to true)),
            ),
        )
        assertEquals(1, messages.count { it.role == "tool" })
        assertToolMessagesFollowTheirCalls(messages)
    }

    // ──────────────────────────────────────
    // M2：确认流程历史
    // ──────────────────────────────────────

    /**
     * 确认流程持久化的序列（原始调用 X → 合成确认调用 Y → 确认响应 Y → 工具结果 X）
     * 翻译后必须剥离合成对（Y），保留合法相邻的 assistant(X)+tool(X)。
     */
    @Test
    fun `confirmation round strips synthetic pair and keeps adjacent call-response`() {
        val x = "call-original"
        val y = "call-confirm"
        val contents = listOf(
            userText("帮我记体重 72.5"),
            // 模型原始调用（会话中持久化）
            modelCall("logBodyWeight", x, mapOf("weightKg" to 72.5)),
            // ADK 合成的确认调用事件
            Content(
                role = "model",
                parts = listOf(
                    Part(
                        functionCall = FunctionCall(
                            name = FunctionCall.REQUEST_CONFIRMATION_FUNCTION_CALL_NAME,
                            args = mapOf(
                                FunctionCall.ORIGINAL_FUNCTION_CALL_KEY to mapOf(
                                    FunctionCall.NAME_KEY to "logBodyWeight",
                                    FunctionCall.ARGS_KEY to mapOf("weightKg" to 72.5),
                                ),
                            ),
                            id = y,
                        ),
                    ),
                ),
            ),
            // 用户确认响应（respondToConfirmation 写入，role="user"）
            toolResponse(
                FunctionCall.REQUEST_CONFIRMATION_FUNCTION_CALL_NAME,
                y,
                mapOf("confirmed" to true),
            ),
            // 框架重执行后的工具结果事件
            toolResponse("logBodyWeight", x, mapOf("success" to true)),
            modelText("已记录体重 72.5kg"),
        )
        val messages = OpenAiAdapters.toOpenAiMessages(systemInstruction = null, contents = contents)

        // 合成的确认调用/响应不得进入 tool_calls/tool 通道
        assertFalseMsg(
            "合成确认调用必须被剥离：$messages",
            messages.any { msg ->
                msg.toolCalls?.any { it.function.name == FunctionCall.REQUEST_CONFIRMATION_FUNCTION_CALL_NAME } == true
            },
        )
        assertFalseMsg(
            "合成确认响应必须被剥离：$messages",
            messages.any { msg -> msg.role == "tool" && msg.toolCallId == y },
        )
        // 原始调用对保留且满足顺序契约
        assertTrueMsg(
            "原始工具调用必须保留：$messages",
            messages.any { msg ->
                msg.role == "assistant" && msg.toolCalls?.any { it.id == x } == true
            },
        )
        assertTrueMsg(
            "原始工具结果必须保留：$messages",
            messages.any { msg -> msg.role == "tool" && msg.toolCallId == x },
        )
        assertToolMessagesFollowTheirCalls(messages)
    }

    // ──────────────────────────────────────
    // 响应侧
    // ──────────────────────────────────────

    /** 合法 arguments 的 tool_call 正常翻译为 FunctionCall part。 */
    @Test
    fun `toAdkContent parses valid tool call arguments`() {
        val content = OpenAiAdapters.toAdkContent(
            content = "我帮你查一下",
            toolCalls = listOf(
                ToolCallDto(
                    id = "call-1",
                    type = "function",
                    function = FunctionCallDto(
                        name = "getBodyMetrics",
                        arguments = """{"count":5}""",
                    ),
                ),
            ),
        )
        assertEquals("model", content.role)
        assertEquals("我帮你查一下", content.parts[0].text)
        val fc = content.parts[1].functionCall
        assertEquals("getBodyMetrics", fc?.name)
        assertEquals(5, fc?.args?.get("count"))
        assertEquals("call-1", fc?.id)
    }

    /** 非法 arguments 走兜底：产出 error FunctionResponse part（供下一轮自我纠正）。 */
    @Test
    fun `toAdkContent invalid arguments produce error fallback part`() {
        val content = OpenAiAdapters.toAdkContent(
            content = null,
            toolCalls = listOf(
                ToolCallDto(
                    id = "call-2",
                    type = "function",
                    function = FunctionCallDto(name = "getBodyMetrics", arguments = "not-json{"),
                ),
            ),
        )
        val fr = content.parts.firstNotNullOfOrNull { it.functionResponse }
        assertEquals("getBodyMetrics", fr?.name)
        assertEquals("call-2", fr?.id)
        assertTrueMsg(
            "兜底产物应为 error FunctionResponse",
            fr?.response?.containsKey("error") == true,
        )
    }

    /** parseArguments：空白返回空 map；非法 JSON / 顶层非 object 返回 null。 */
    @Test
    fun `parseArguments handles blank invalid and non-object input`() {
        assertEquals(emptyMap<String, Any?>(), OpenAiAdapters.parseArguments(""))
        assertNull(OpenAiAdapters.parseArguments("not-json{"))
        assertNull(OpenAiAdapters.parseArguments("""[1,2]"""))
        assertEquals(
            mapOf("a" to 1),
            OpenAiAdapters.parseArguments("""{"a":1}"""),
        )
    }
}
