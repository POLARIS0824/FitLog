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
 * ## tool_call_id 容错
 *
 * OpenAI 协议要求 tool 消息携带与 assistant tool_calls 一致的 id。ADK 的
 * [FunctionResponse.id] 正常情况下等于对应 functionCall 的 id（框架自动填充），
 * 本对象仍维护"最近一次 assistant 消息发出的 tool_call id 映射"（按函数名）兜底；
 * 仍缺失则退化为 user 文本消息，避免请求被服务商 400 拒绝。
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
     */
    fun toOpenAiMessages(
        systemInstruction: Content?,
        contents: List<Content>,
    ): List<MessageDto> {
        val messages = mutableListOf<MessageDto>()
        systemInstruction?.let { sys ->
            val text = sys.parts.mapNotNull { it.text }.joinToString("\n").trim()
            if (text.isNotEmpty()) {
                messages += MessageDto(role = "system", content = text)
            }
        }

        // 最近一次 assistant 消息发出的 tool_call id（按函数名），供 FunctionResponse 缺 id 时回填
        val lastToolCallIdsByName = mutableMapOf<String, String>()

        contents.forEach { content ->
            // ── 分派 1：函数响应（ADK 存为 role="user"，必须按 part 判定）→ role="tool" ──
            val functionResponses = content.parts.mapNotNull { it.functionResponse }
            if (functionResponses.isNotEmpty()) {
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
                            val id = fr.id ?: lastToolCallIdsByName[fr.name]
                            if (id != null) {
                                messages += MessageDto(
                                    role = "tool",
                                    content = responseMapToJsonString(fr.response),
                                    toolCallId = id,
                                )
                            } else {
                                // 无法关联到任何 tool_call：退化为 user 文本，避免协议 400
                                messages += MessageDto(
                                    role = "user",
                                    content = "[工具 ${fr.name} 结果] ${responseMapToJsonString(fr.response)}",
                                )
                            }
                        }
                    }
                }
                return@forEach
            }

            // ── 分派 2：函数调用（assistant 产出）→ assistant + tool_calls ──
            val functionCalls = content.parts.mapNotNull { it.functionCall }
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
                    val dtos = realCalls.map { fc ->
                        val id = fc.id ?: "call_${fc.name}"
                        lastToolCallIdsByName[fc.name] = id
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
                return@forEach
            }

            // ── 分派 3：纯文本 → 按 role 映射（model → assistant，其余原样） ──
            val text = content.parts.mapNotNull { it.text }
                .joinToString("\n").trim()
            if (text.isNotEmpty()) {
                messages += MessageDto(role = normalizeRole(content.role), content = text)
            }
        }
        return messages
    }

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

    /** 工具结果 Map → JSON 字符串（作为 tool 消息 content）。 */
    private fun responseMapToJsonString(response: Map<String, Any?>): String =
        buildJsonObject {
            response.forEach { (k, v) -> put(k, anyToJsonElement(v)) }
        }.toString()

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
