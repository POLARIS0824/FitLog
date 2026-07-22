package com.example.fitlog.data.agent.tools

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [buildToolListResult] 的单元测试。
 */
class ToolResultJsonTest {

    private val json = Json

    @Test
    fun `under limit keeps all items without truncated flag`() {
        val items = (1..3).map { buildJsonObject { put("n", it) } }

        val result = json.parseToJsonElement(buildToolListResult(items)).jsonObject

        assertEquals(3, result["results"]!!.jsonArray.size)
        assertNull(result["truncated"])
    }

    @Test
    fun `over limit truncates to a prefix and marks truncated`() {
        // 每个元素约 200 字符，100 个必然超过 4000 字硬上限
        val items = (1..100).map { buildJsonObject { put("data", "x".repeat(180)) } }

        val raw = buildToolListResult(items)

        // 结构开销（results/truncated 字段）的合理宽限
        assertTrue(raw.length <= MAX_RESULT_CHARS + 64)
        val result = json.parseToJsonElement(raw).jsonObject
        assertTrue(result["results"]!!.jsonArray.size < 100)
        assertEquals(true, result["truncated"]?.jsonPrimitive?.booleanOrNull)
    }

    @Test
    fun `empty list with note is preserved`() {
        val raw = buildToolListResult(emptyList(), note = "动作库为空")

        val result = json.parseToJsonElement(raw).jsonObject
        assertEquals(0, result["results"]!!.jsonArray.size)
        assertEquals("动作库为空", result["note"]!!.jsonPrimitive.content)
    }
}
