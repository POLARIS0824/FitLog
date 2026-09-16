package com.example.fitlog.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [ExerciseDisplayName.keyForDisplayName] 的单元测试。
 *
 * 验证中文展示名反查动作库 key：精确命中、同义名取首个（确定性）、
 * 未命中返回 null、空白输入返回 null。
 */
class ExerciseDisplayNameTest {

    /** 常见中文名精确反查。 */
    @Test
    fun testKeyForDisplayName_exactMatch() {
        assertEquals("barbell-bench-press", ExerciseDisplayName.keyForDisplayName("杠铃卧推"))
        assertEquals("pull-up", ExerciseDisplayName.keyForDisplayName("引体向上"))
        assertEquals("barbell-deadlift", ExerciseDisplayName.keyForDisplayName("杠铃硬拉"))
    }

    /** 前后空白容忍（AI 解析结果可能带空白）。 */
    @Test
    fun testKeyForDisplayName_trimsInput() {
        assertEquals("barbell-bench-press", ExerciseDisplayName.keyForDisplayName(" 杠铃卧推 "))
    }

    /** 同义中文名（多个 key 映射同一中文名）按声明顺序取首个，保证确定性。 */
    @Test
    fun testKeyForDisplayName_duplicateChineseName_takesFirst() {
        // CHINESE_NAMES 中 "杠铃深蹲" 先映射 barbell-full-squat，后映射 barbell-squat
        assertEquals("barbell-full-squat", ExerciseDisplayName.keyForDisplayName("杠铃深蹲"))
    }

    /** 库外中文名/英文名/空串返回 null（调用方继续走英文名匹配）。 */
    @Test
    fun testKeyForDisplayName_missReturnsNull() {
        assertNull(ExerciseDisplayName.keyForDisplayName("不存在的动作"))
        assertNull(ExerciseDisplayName.keyForDisplayName("Barbell Bench Press"))
        assertNull(ExerciseDisplayName.keyForDisplayName(""))
        assertNull(ExerciseDisplayName.keyForDisplayName("   "))
    }
}
