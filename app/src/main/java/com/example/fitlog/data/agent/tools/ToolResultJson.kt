package com.example.fitlog.data.agent.tools

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * tool 结果 JSON 的硬上限字符数。
 *
 * 防 context 爆炸的统一兜底：各 tool 在自身 limit 策略之后再过这一道。
 */
const val MAX_RESULT_CHARS = 4000

/**
 * 把 tool 的结果元素列表序列化为 JSON 字符串，并做长度兜底截断。
 *
 * 逐个追加元素，一旦整体序列化结果超过 [MAX_RESULT_CHARS] 就停止，
 * 并在结果中标记 "truncated": true，引导模型缩小查询范围。
 *
 * @param results 结果元素列表
 * @param note 可选的说明文字（如"历史最佳重量: 80.0kg"）
 * @return {"results": [...], "truncated"?: true, "note"?: "..."} 形式的 JSON 字符串
 */
fun buildToolListResult(
    results: List<JsonObject>,
    note: String? = null,
): String {
    val kept = mutableListOf<JsonObject>()
    var truncated = false
    for ((index, item) in results.withIndex()) {
        // 估算加入该元素后的整体长度（含可能的 truncated 标记开销）
        val candidateLength = serializeListResult(
            results = kept + item,
            truncated = index < results.lastIndex,
            note = note,
        ).length
        if (candidateLength > MAX_RESULT_CHARS) {
            truncated = true
            break
        }
        kept += item
    }
    return serializeListResult(results = kept, truncated = truncated, note = note)
}

/**
 * 序列化 {"results": [...], "truncated"?: true, "note"?: "..."} 结构。
 */
private fun serializeListResult(
    results: List<JsonObject>,
    truncated: Boolean,
    note: String?,
): String {
    return buildJsonObject {
        putJsonArray("results") { results.forEach { add(it) } }
        if (truncated) put("truncated", true)
        note?.let { put("note", it) }
    }.toString()
}
