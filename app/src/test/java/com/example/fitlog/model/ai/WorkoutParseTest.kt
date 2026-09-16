package com.example.fitlog.model.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [parseWorkoutJson] 的单元测试。
 *
 * 验证对模型回复的容错解析：纯 JSON、代码块围栏包裹、前后杂文字、
 * 缺省字段、未知字段、畸形输入（返回 null）。
 */
class WorkoutParseTest {

    /** 合法最小 JSON：全部字段齐备。 */
    @Test
    fun testParse_plainJson() {
        val raw = """
            {"feelings": "状态不错", "startTime": "19:30", "endTime": "21:00",
             "exercises": [{"name": "杠铃卧推", "sets": [
                 {"weightKg": 60, "reps": 10, "type": "WARMUP"},
                 {"weightKg": 80, "reps": 8, "type": "WORKING"}
             ]}]}
        """.trimIndent()

        val dto = parseWorkoutJson(raw)!!

        assertEquals("状态不错", dto.feelings)
        assertEquals("19:30", dto.startTime)
        assertEquals("21:00", dto.endTime)
        assertEquals(1, dto.exercises.size)
        assertEquals("杠铃卧推", dto.exercises[0].name)
        assertEquals(2, dto.exercises[0].sets.size)
        assertEquals(60.0, dto.exercises[0].sets[0].weightKg!!, 0.001)
        assertEquals(10, dto.exercises[0].sets[0].reps)
        assertEquals("WARMUP", dto.exercises[0].sets[0].type)
    }

    /** markdown 代码块围栏包裹（部分服务商无视 json_mode 的常见脏输出）。 */
    @Test
    fun testParse_codeFenced() {
        val raw = """
            ```json
            {"exercises": [{"name": "深蹲", "sets": [{"weightKg": 100, "reps": 5}]}]}
            ```
        """.trimIndent()

        val dto = parseWorkoutJson(raw)!!

        assertEquals(1, dto.exercises.size)
        assertEquals("深蹲", dto.exercises[0].name)
        // 缺省 type 由调用方按 WORKING 处理，解析层保留 null
        assertNull(dto.exercises[0].sets[0].type)
    }

    /** JSON 前后有解释文字（无围栏）也能截取解析。 */
    @Test
    fun testParse_withSurroundingText() {
        val raw = "好的，以下是解析结果：{\"feelings\":null,\"exercises\":[]} 请确认。"

        val dto = parseWorkoutJson(raw)!!

        assertNull(dto.feelings)
        assertEquals(0, dto.exercises.size)
    }

    /** 全部字段缺省：空对象解码为全默认值（调用方按 exercises 为空判失败）。 */
    @Test
    fun testParse_emptyObjectDefaults() {
        val dto = parseWorkoutJson("{}")!!

        assertNull(dto.feelings)
        assertNull(dto.startTime)
        assertNull(dto.endTime)
        assertEquals(0, dto.exercises.size)
    }

    /** 未知字段忽略（模型偶尔附加说明性字段）。 */
    @Test
    fun testParse_ignoresUnknownFields() {
        val raw = """{"exercises": [], "note": "解析自用户日志", "confidence": 0.9}"""

        val dto = parseWorkoutJson(raw)!!

        assertEquals(0, dto.exercises.size)
    }

    /** 完全没有花括号的垃圾输入返回 null。 */
    @Test
    fun testParse_garbageReturnsNull() {
        assertNull(parseWorkoutJson("抱歉，我无法解析这段内容"))
        assertNull(parseWorkoutJson(""))
        assertNull(parseWorkoutJson("{截断的不完整"))
    }
}
