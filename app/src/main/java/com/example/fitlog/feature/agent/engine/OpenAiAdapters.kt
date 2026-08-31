package com.example.fitlog.feature.agent.engine

import com.example.fitlog.data.remote.dto.FunctionCallDto
import com.example.fitlog.data.remote.dto.FunctionDefinitionDto
import com.example.fitlog.data.remote.dto.MessageDto
import com.example.fitlog.data.remote.dto.ToolCallDto
import com.example.fitlog.data.remote.dto.ToolDefinitionDto
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.FunctionCall
import com.google.adk.kt.types.FunctionResponse
import com.google.adk.kt.types.Part
import com.google.adk.kt.types.Schema
import com.google.adk.kt.types.Type
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * ADK 类型 ↔ OpenAI 兼容 DTO 的双向映射纯函数集。
 *
 * [OpenAiCompatibleModel] 只做"发 HTTP 请求"这一件事，类型翻译全部沉淀在本对象中，
 * 均为无副作用纯函数，可脱离 Android 直接单测。
 *
 * ## 协议映射要点
 *
 * - ADK `contents` 的 role "model" → OpenAI "assistant"
 * - ADK `config.systemInstruction` → OpenAI 首条 `role="system"` 消息
 * - ADK `Part.functionCall`（assistant 产出）→ OpenAI assistant 消息的 `tool_calls`
 * - ADK `Part.functionResponse`（工具结果）→ OpenAI `role="tool"` + `tool_call_id`
 * - ADK `Schema` → OpenAI 工具 `parameters` 的 JSON Schema（[Type] 枚举 → 小写字符串）
 *
 * ## ⚠️ 分派必须按 part 而非 role（关键坑）
 *
 * ADK 的会话内容里，**函数响应事件的 role 是 "user"**（见 ADK 源码
 * `InvocationContext.buildResponseEvent`：`Content(role = "user", parts = [functionResponse])`），
 * 而不是 "tool"。因此 [toOpenAiMessages] 必须**先按 part 类型分派**：
 * 含 functionResponse → tool 消息；含 functionCall → assistant tool_calls 消息；
 * 其余才按 role 映射纯文本。若按 role 分派，函数响应会被误当普通用户文本，
 * assistant 的 tool_calls 之后没有对应 tool 结果，OpenAI 协议直接断裂（服务商 400）。
 *
 * 分派之间**不互斥**：并行调用中某个 arguments 非法时，[toAdkContent] 会把
 * 合法调用与 error 响应存进同一 Content——若响应对独占该 Content，合法调用会被
 * 静默丢弃。同一 Content 内响应先于调用翻译，保证 tool 消息紧跟其 tool_calls。
 *
 * ## tool_call_id 容错
 *
 * OpenAI 协议要求 tool 消息携带与 assistant tool_calls 一致的 id。ADK 的
 * [FunctionResponse.id] 正常情况下等于对应 functionCall 的 id（框架自动填充），
 * 本对象维护三层容错：
 * 1. id 必须命中历史已发出的 tool_call 集合才可信任（会话截断/分支会产生悬空引用）；
 * 2. id 缺失时按"最近一次 assistant 消息同名调用"队列回填（并行同名调用按序取用）；
 * 3. 均不可得则退化为 user 文本消息，避免请求被服务商 400 拒绝。
 *
 * ## 两类协议剥离（关键坑）
 *
 * - **ADK 确认协议的合成对**：`adk_request_confirmation` 合成调用及其响应
 *   在会话中插在原始调用与其结果之间，直接翻译会破坏 "tool 消息紧跟其
 *   tool_calls" 的顺序契约——两者均剥离，保留原始对的相邻配对
 * - **role="model" 的函数响应**：[toAdkContent] 对非法 arguments 的兜底产物，
 *   会话中没有携带该 id 的 assistant tool_calls——降级为 user 文本，
 *   避免悬空 tool_call_id 持久化毒化会话
 */
object OpenAiAdapters {

    /**
     * 消息历史的默认总字符预算（不含 system 指令）。
     * 按最不利口径估算：本应用对话以中文为主（约 1 token/字），32k 字 ≈ 32k
     * token，落在常见 32k 起步的兼容网关窗口内。英文内容同预算下更省，
     * 留出的余量吸收工具结果与多轮调用开销。
     */
    const val DEFAULT_MAX_HISTORY_CHARS = 32_000

    /**
     * 单条 tool 结果内容的字符上限：`getImportedWorkoutContent` 等工具会把整篇
     * 导入原文塞进 tool 消息，一条即可挤爆预算。超限截断并标注，模型至少
     * 能看到结果开头并知晓被截断。
     */
    const val MAX_TOOL_CONTENT_CHARS = 8_000

    /** 悬空 tool_call 自愈时注入的合成 tool 结果。 */
    private const val DANGLING_TOOL_RESULT_JSON = """{"error":"运行被中断，工具未执行"}"""

    /** 宽松 JSON 解析器：容忍未加引号的键/尾逗号等模型常见输出，忽略未知键。 */
    private val lenientJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    // ──────────────────────────────────────
    // 请求侧：ADK → OpenAI
    // ──────────────────────────────────────

    /**
     * 把 ADK 一轮请求的消息历史翻译为 OpenAI `messages` 数组。
     *
     * @param systemInstruction 系统提示（来自 `GenerateContentConfig.systemInstruction`），
     *     非空时翻译为首条 `role="system"` 消息
     * @param contents ADK 会话内容（user/model 交替，含工具调用与工具结果）
     * @param maxHistoryChars 消息历史总字符预算（超出时从最旧的完整轮次开始丢弃，
     *     见 [truncateHistory]；工具结果单条上限另见 [MAX_TOOL_CONTENT_CHARS]）
     */
    fun toOpenAiMessages(
        systemInstruction: Content?,
        contents: List<Content>,
        maxHistoryChars: Int = DEFAULT_MAX_HISTORY_CHARS,
    ): List<MessageDto> {
        val messages = mutableListOf<MessageDto>()
        systemInstruction?.let { sys ->
            val text = sys.parts.mapNotNull { it.text }.joinToString("\n").trim()
            if (text.isNotEmpty()) {
                messages += MessageDto(role = "system", content = text)
            }
        }

        // 最近一次 assistant 消息发出的 tool_call id（按函数名聚合出现顺序），
        // 供 FunctionResponse 缺 id 时回填；用队列而非单值：模型一轮并行调用
        // 同名函数两次时，两条响应按出现顺序各取各的 id，不会撞车
        val toolCallIdsByName = mutableMapOf<String, ArrayDeque<String>>()

        // 历史中已发出的全部 tool_call id（分派 2 写入）：
        // FunctionResponse 自带的 id 必须命中本集合才可信任，否则是悬空引用
        val emittedToolCallIds = mutableSetOf<String>()

        contents.forEach { content ->
            val functionResponses = content.parts.mapNotNull { it.functionResponse }
            val functionCalls = content.parts.mapNotNull { it.functionCall }

            // ── 分派 1：函数响应（ADK 存为 role="user"，必须按 part 判定）→ role="tool" ──
            // 注意分派不互斥（见分派 2 注释），但响应必须先于调用翻译，
            // 保证 tool 消息紧跟其 assistant tool_calls
            functionResponses.forEach { fr ->
                when {
                    // ADK 确认协议的合成响应（respondToConfirmation 写入）：会话中不存在
                    // 携带该 id 的真实工具调用，进入 tool 通道必然违反协议，直接剥离
                    // （原始调用与其结果由其余消息自然配对）
                    fr.name == FunctionCall.REQUEST_CONFIRMATION_FUNCTION_CALL_NAME -> Unit

                    // role="model" 的函数响应是 [toAdkContent] 对非法 arguments 的兜底产物：
                    // 原始 tool_call 已被丢弃，会话中没有携带该 id 的 assistant tool_calls，
                    // 翻成 role="tool" 会造成悬空 tool_call_id（严格服务商 400，且坏历史
                    // 持久化后每轮必 400）——降级为 user 文本保留自我纠正信号
                    content.role == "model" -> messages += MessageDto(
                        role = "user",
                        content = "（工具 ${fr.name} 参数解析失败：" +
                            "${fr.response["error"] ?: "请重新生成合法参数"}）",
                    )

                    else -> {
                        // fr.id 必须命中历史已发出的 tool_call 才可信任：会话截断/分支
                        // 会产生悬空 id（同 role="model" 响应的危险）；未命中则按同名
                        // 队列回填，仍无则降级为 user 文本，避免协议 400
                        val queue = toolCallIdsByName[fr.name]
                        val knownId = fr.id?.takeIf(emittedToolCallIds::contains)
                        if (knownId != null) {
                            queue?.remove(knownId)
                            messages += MessageDto(
                                role = "tool",
                                content = responseMapToJsonString(fr.response),
                                toolCallId = knownId,
                            )
                        } else {
                            val fallbackId = queue?.removeFirstOrNull()
                            if (fallbackId != null) {
                                messages += MessageDto(
                                    role = "tool",
                                    content = responseMapToJsonString(fr.response),
                                    toolCallId = fallbackId,
                                )
                            } else {
                                messages += MessageDto(
                                    role = "user",
                                    content = "[工具 ${fr.name} 结果] " +
                                        responseMapToJsonString(fr.response),
                                )
                            }
                        }
                    }
                }
            }

            // ── 分派 2：函数调用（assistant 产出）→ assistant + tool_calls ──
            // 分派 1 不因存在 functionCall 而 return：并行调用中某个 arguments 非法时，
            // [toAdkContent] 会把合法调用与 error 响应存进同一 Content——分派互斥会
            // 静默丢弃合法调用，其后 ADK 执行产生的 tool 消息沦为悬空 tool_call_id
            if (functionCalls.isNotEmpty()) {
                // 剥离 ADK 确认协议的合成调用（其响应在分派 1 同步剥离）：
                // 若保留，翻译结果为 assistant(原始X)→assistant(合成Y)→…→tool(Y)→tool(X)，
                // 原始调用的 tool 消息不再紧跟其 tool_calls，严格服务商 400
                val realCalls = functionCalls.filter {
                    it.name != FunctionCall.REQUEST_CONFIRMATION_FUNCTION_CALL_NAME
                }
                if (realCalls.isNotEmpty()) {
                    val text = content.parts.mapNotNull { it.text }
                        .joinToString("\n").trim().ifEmpty { null }
                    val dtos = realCalls.mapIndexed { index, fc ->
                        // id 缺失时用带序号的兜底：并行同名调用必须有可区分的唯一 id
                        val id = fc.id ?: "call_${fc.name}_$index"
                        emittedToolCallIds.add(id)
                        toolCallIdsByName.getOrPut(fc.name) { ArrayDeque() }.addLast(id)
                        ToolCallDto(
                            id = id,
                            // 显式传 type：encodeDefaults=false 下默认值会被跳过，缺 "type" 服务商 400
                            type = "function",
                            function = FunctionCallDto(
                                name = fc.name,
                                arguments = argsMapToJsonString(fc.args),
                            ),
                        )
                    }
                    messages += MessageDto(role = "assistant", content = text, toolCalls = dtos)
                }
            }

            // ── 分派 3：纯文本 → 按 role 映射（model → assistant，其余原样） ──
            // 含函数 part 的 Content 不走此分派：文本已随分派 2 的 assistant 消息携带
            if (functionResponses.isEmpty() && functionCalls.isEmpty()) {
                val text = content.parts.mapNotNull { it.text }
                    .joinToString("\n").trim()
                if (text.isNotEmpty()) {
                    messages += MessageDto(role = normalizeRole(content.role), content = text)
                }
            }
        }
        // 运行中断（取消/进程被杀）会在会话里留下「assistant 携带 tool_calls 却没有
        // 对应 tool 结果」的坏历史——严格服务商从此每轮 400（会话毒化）。请求装配
        // 阶段补齐合成结果，配合截断护栏（截断只在 user 边界切，不拆散调用对）
        return truncateHistory(repairDanglingToolCalls(messages), maxHistoryChars)
    }

    /**
     * 悬空 tool_call 自愈：为「已发出但始终没有 tool 结果」的调用补一条合成
     * tool 消息，使 `assistant(tool_calls)` → `tool` 的协议对恢复完整。
     *
     * 扫描消息序列维护未消费调用集合：遇到非 tool 消息（即该批调用的结果
     * 不会再出现）时立即在其前插入合成结果；序列尾部悬空同样补齐。
     * 合成内容明确标注「被中断」，模型可据此向用户解释而非困惑于空结果。
     */
    private fun repairDanglingToolCalls(messages: List<MessageDto>): List<MessageDto> {
        if (messages.none { it.toolCalls != null }) return messages
        val repaired = mutableListOf<MessageDto>()
        // id → 函数名：已发出但尚未等到结果的调用（按发出顺序，合成响应对齐同序）
        var pending = LinkedHashMap<String, String>()
        messages.forEach { message ->
            if (message.role != "tool" && pending.isNotEmpty()) {
                pending.keys.forEach { id ->
                    repaired += MessageDto(
                        role = "tool",
                        content = DANGLING_TOOL_RESULT_JSON,
                        toolCallId = id,
                    )
                }
                pending = LinkedHashMap()
            }
            message.toolCalls?.forEach { call -> pending[call.id] = call.function.name }
            if (message.role == "tool") {
                pending.remove(message.toolCallId)
            }
            repaired += message
        }
        pending.keys.forEach { id ->
            repaired += MessageDto(
                role = "tool",
                content = DANGLING_TOOL_RESULT_JSON,
                toolCallId = id,
            )
        }
        return repaired
    }

    /**
     * 历史长度护栏：全量翻译历史 + 工具大 payload 会超出模型上下文窗口（服务商 400，
     * 除清空对话外无自愈路径）。超预算时从最旧的完整轮次开始丢弃。
     *
     * 截断点只取 `role="user"` 文本消息边界：tool 消息永远紧跟其 assistant
     * tool_calls（见 [toOpenAiMessages] 分派顺序），在 user 边界切开不会拆散
     * 任何调用/结果对，也无需重跑悬空修复。system 消息始终保留。
     */
    private fun truncateHistory(messages: List<MessageDto>, maxChars: Int): List<MessageDto> {
        if (messages.sumOf { it.approximateChars() } <= maxChars) return messages
        val systemMessages = messages.takeWhile { it.role == "system" }
        val rest = messages.drop(systemMessages.size)
        val budget = maxChars - systemMessages.sumOf { it.approximateChars() }
        // 最早的、使保留部分 ≤ 预算的 user 边界（越晚保留越少）
        val boundary = rest.withIndex()
            .filter { it.value.role == "user" }
            .firstOrNull { (index, _) ->
                rest.subList(index, rest.size).sumOf { it.approximateChars() } <= budget
            }?.index
            ?: return messages // 连最后一个完整轮次都超预算：不硬截，交给服务商报错
        val kept = systemMessages + rest.subList(boundary, rest.size)
        return if (kept.size == messages.size) messages else kept
    }

    /** 消息近似字符数（中文≈1 token/字，英文≈4 chars/token，取宽估算做预算）。 */
    private fun MessageDto.approximateChars(): Int =
        (content?.length ?: 0) +
            (toolCalls?.sumOf { it.function.name.length + it.function.arguments.length } ?: 0)

    /**
     * 把 ADK 工具定义翻译为 OpenAI `tools` 数组。
     *
     * @param tools ADK `GenerateContentConfig.tools`（每项含 functionDeclarations）
     */
    fun toOpenAiTools(tools: List<com.google.adk.kt.types.Tool>?): List<ToolDefinitionDto>? {
        val declarations = tools
            ?.flatMap { it.functionDeclarations ?: emptyList() }
            ?: return null
        if (declarations.isEmpty()) return null
        return declarations.map { decl ->
            ToolDefinitionDto(
                // 显式传 type：encodeDefaults=false 下默认值会被跳过，缺 "type" 服务商 400
                type = "function",
                function = FunctionDefinitionDto(
                    name = decl.name,
                    description = decl.description,
                    parameters = decl.parameters?.let { schemaToJsonObject(it) }
                        ?: buildJsonObject {
                            put("type", "object")
                            put("properties", buildJsonObject {})
                        },
                ),
            )
        }
    }

    /**
     * ADK [Schema] → OpenAI JSON Schema [JsonObject]（递归）。
     */
    fun schemaToJsonObject(schema: Schema): JsonObject = buildJsonObject {
        schema.type?.let { put("type", typeToJsonString(it)) }
        schema.description?.let { put("description", it) }
        schema.enum?.let { values -> put("enum", JsonArray(values.map { JsonPrimitive(it) })) }
        when (schema.type) {
            Type.OBJECT -> {
                schema.properties?.let { props ->
                    put("properties", buildJsonObject {
                        props.forEach { (name, propSchema) ->
                            put(name, schemaToJsonObject(propSchema))
                        }
                    })
                }
                schema.required?.takeIf { it.isNotEmpty() }?.let { required ->
                    put("required", JsonArray(required.map { JsonPrimitive(it) }))
                }
            }

            Type.ARRAY -> schema.items?.let { put("items", schemaToJsonObject(it)) }
            else -> Unit
        }
    }

    // ──────────────────────────────────────
    // 响应侧：OpenAI → ADK
    // ──────────────────────────────────────

    /**
     * 把 OpenAI assistant 回复翻译为 ADK [Content]（role="model"）。
     *
     * @param content 文本内容（可能为 null，仅 tool_calls 时）
     * @param toolCalls 模型请求的工具调用（可能为 null）
     * @param parseErrorFallback arguments JSON 非法时的兜底处理：
     *     true → 该工具调用转为携带 error 的 [FunctionResponse] part（回喂模型自我纠正）；
     *     false → 跳过该 part
     */
    fun toAdkContent(
        content: String?,
        toolCalls: List<ToolCallDto>?,
        parseErrorFallback: Boolean = true,
    ): Content {
        val parts = mutableListOf<Part>()
        content?.takeIf { it.isNotBlank() }?.let { parts += Part(text = it) }
        toolCalls?.forEach { call ->
            val args = parseArguments(call.function.arguments)
            if (args != null) {
                parts += Part(
                    functionCall = FunctionCall(
                        name = call.function.name,
                        args = args,
                        id = call.id,
                    ),
                )
            } else if (parseErrorFallback) {
                // arguments 不是合法 JSON：转成一个 error 工具结果，让模型下一轮自我纠正
                parts += Part(
                    functionResponse = FunctionResponse(
                        name = call.function.name,
                        response = mapOf("error" to "arguments JSON 解析失败，请重新生成合法参数"),
                        id = call.id,
                    ),
                )
            }
        }
        return Content(role = "model", parts = parts)
    }

    /**
     * 解析模型输出的 arguments JSON 字符串为 `Map<String, Any?>`。
     *
     * @return 顶层为 JSON object 时返回其键值映射；非法 JSON 或顶层非 object 返回 null
     */
    fun parseArguments(arguments: String): Map<String, Any?>? {
        if (arguments.isBlank()) return emptyMap()
        val element = runCatching { lenientJson.parseToJsonElement(arguments) }.getOrNull()
        return (element as? JsonObject)?.toAnyMap()
    }

    // ──────────────────────────────────────
    // 内部工具
    // ──────────────────────────────────────

    /** ADK role（"model"/"user" 等）→ OpenAI role；仅用于纯文本内容（见类注释的分派规则）。 */
    private fun normalizeRole(role: String?): String = when (role) {
        "model" -> "assistant"
        else -> "user"
    }

    /** ADK [Type] → JSON Schema 类型字符串。 */
    private fun typeToJsonString(type: Type): String = when (type) {
        Type.STRING -> "string"
        Type.NUMBER -> "number"
        Type.INTEGER -> "integer"
        Type.BOOLEAN -> "boolean"
        Type.ARRAY -> "array"
        Type.OBJECT -> "object"
        Type.NULL -> "null"
        Type.TYPE_UNSPECIFIED -> "string"
    }

    /** 工具调用参数 Map → JSON 字符串。 */
    private fun argsMapToJsonString(args: Map<String, Any?>): String =
        buildJsonObject {
            args.forEach { (k, v) -> put(k, anyToJsonElement(v)) }
        }.toString()

    /** 工具结果 Map → JSON 字符串（作为 tool 消息 content，超 [MAX_TOOL_CONTENT_CHARS] 截断）。 */
    private fun responseMapToJsonString(response: Map<String, Any?>): String {
        val json = buildJsonObject {
            response.forEach { (k, v) -> put(k, anyToJsonElement(v)) }
        }.toString()
        if (json.length <= MAX_TOOL_CONTENT_CHARS) return json
        // 截断点回退避开 UTF-16 代理对（emoji 等），避免产生孤立代理乱码；
        // 截断后的文本可能不再是合法 JSON，但 tool content 是自由文本，
        // 模型能识别"结果被截断"并基于可见前缀继续
        var end = MAX_TOOL_CONTENT_CHARS
        while (end > 0 && Character.isHighSurrogate(json[end - 1])) end--
        return json.take(end) + "...（结果过长已截断）"
    }

    /** 任意 JSON 原生值 → [JsonElement]。 */
    private fun anyToJsonElement(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is JsonElement -> value
        is String -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Int -> JsonPrimitive(value)
        is Long -> JsonPrimitive(value)
        is Double -> JsonPrimitive(value)
        is Float -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value.toDouble())
        is Map<*, *> -> buildJsonObject {
            value.forEach { (k, v) ->
                if (k is String) put(k, anyToJsonElement(v))
            }
        }

        is Iterable<*> -> buildJsonArray { value.forEach { add(anyToJsonElement(it)) } }
        is Array<*> -> buildJsonArray { value.forEach { add(anyToJsonElement(it)) } }
        else -> JsonPrimitive(value.toString())
    }

    /** [JsonObject] → `Map<String, Any?>`（递归，值为 Kotlin 原生类型）。 */
    private fun JsonObject.toAnyMap(): Map<String, Any?> =
        entries.associate { (k, v) -> k to v.toAny() }

    /** [JsonElement] → Kotlin 原生值（递归）。 */
    private fun JsonElement.toAny(): Any? = when (this) {
        is JsonNull -> null
        is JsonObject -> toAnyMap()
        is JsonArray -> map { it.toAny() }
        is JsonPrimitive -> when {
            isString -> contentOrNull
            booleanOrNull != null -> booleanOrNull
            intOrNull != null -> intOrNull
            longOrNull != null -> longOrNull
            doubleOrNull != null -> doubleOrNull
            else -> contentOrNull
        }
    }
}
