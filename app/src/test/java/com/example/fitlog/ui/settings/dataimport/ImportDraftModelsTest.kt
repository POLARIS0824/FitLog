package com.example.fitlog.ui.settings.dataimport

import com.example.fitlog.model.SetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * [ImportDraftModels] 的有效性检查与清洗规则单元测试。
 *
 * 验证：
 * 1. 空动作名、全空白动作名不作为有效动作，不计入 [ImportDraftWorkout.validExerciseCount]；
 * 2. 占位组（reps <= 0）不构成有效组，无有效组的动作不作为有效动作；
 * 3. [ImportDraftWorkout.toWorkout] 剔除空白动作名与全占位组动作，清洗后动作名自动 trim；
 * 4. [ImportDraftWorkout.toExerciseLogs] 与 [ImportDraftWorkout.toWorkout] 口径严格一致。
 */
class ImportDraftModelsTest {

    @Test
    fun `isValid returns false for empty or whitespace exercise names`() {
        val emptyNameExercise = ImportDraftExercise(
            localId = 1L,
            name = "",
            exerciseKey = "barbell-bench-press",
            sets = listOf(ImportDraftSet(10L, 60f, 10, SetType.WORKING)),
        )
        val whitespaceNameExercise = ImportDraftExercise(
            localId = 2L,
            name = "   \t\n  ",
            exerciseKey = "barbell-bench-press",
            sets = listOf(ImportDraftSet(11L, 60f, 10, SetType.WORKING)),
        )
        val validExercise = ImportDraftExercise(
            localId = 3L,
            name = "杠铃卧推",
            exerciseKey = "barbell-bench-press",
            sets = listOf(ImportDraftSet(12L, 60f, 10, SetType.WORKING)),
        )

        assertTrue(emptyNameExercise.hasValidSet)
        assertFalse(emptyNameExercise.isValid)

        assertTrue(whitespaceNameExercise.hasValidSet)
        assertFalse(whitespaceNameExercise.isValid)

        assertTrue(validExercise.hasValidSet)
        assertTrue(validExercise.isValid)
    }

    @Test
    fun `isValid returns false when all sets have reps 0 or less`() {
        val placeholderOnlyExercise = ImportDraftExercise(
            localId = 1L,
            name = "杠铃卧推",
            exerciseKey = "barbell-bench-press",
            sets = listOf(
                ImportDraftSet(10L, 60f, 0, SetType.WORKING),
                ImportDraftSet(11L, 0f, -1, SetType.WARMUP),
            ),
        )

        assertFalse(placeholderOnlyExercise.hasValidSet)
        assertFalse(placeholderOnlyExercise.isValid)
    }

    @Test
    fun `validExerciseCount accurately counts only exercises with valid names and valid sets`() {
        val draft = ImportDraftWorkout(
            exercises = listOf(
                // 有效动作
                ImportDraftExercise(
                    localId = 1L,
                    name = "杠铃卧推",
                    exerciseKey = "barbell-bench-press",
                    sets = listOf(ImportDraftSet(10L, 60f, 10, SetType.WORKING)),
                ),
                // 空动作名（即使有 reps > 0 也不计入有效动作）
                ImportDraftExercise(
                    localId = 2L,
                    name = "",
                    exerciseKey = "dumbbell-bench-press",
                    sets = listOf(ImportDraftSet(20L, 20f, 10, SetType.WORKING)),
                ),
                // 全空白动作名（不计入有效动作）
                ImportDraftExercise(
                    localId = 3L,
                    name = "   ",
                    exerciseKey = "pull-up",
                    sets = listOf(ImportDraftSet(30L, 0f, 8, SetType.WORKING)),
                ),
                // 仅占位组（不计入有效动作）
                ImportDraftExercise(
                    localId = 4L,
                    name = "深蹲",
                    exerciseKey = "barbell-squat",
                    sets = listOf(ImportDraftSet(40L, 0f, 0, SetType.WORKING)),
                ),
            ),
        )

        assertEquals(1, draft.validExerciseCount)
    }

    @Test
    fun `toWorkout drops blank exercise names and cleans placeholder sets`() {
        val draft = ImportDraftWorkout(
            feelings = "状态不错",
            exercises = listOf(
                ImportDraftExercise(
                    localId = 1L,
                    name = "  杠铃卧推  ",
                    exerciseKey = "barbell-bench-press",
                    sets = listOf(
                        ImportDraftSet(10L, 60f, 10, SetType.WORKING),
                        ImportDraftSet(11L, 0f, 0, SetType.WORKING), // 占位组应被丢弃
                    ),
                ),
                ImportDraftExercise(
                    localId = 2L,
                    name = "",
                    exerciseKey = "pull-up",
                    sets = listOf(ImportDraftSet(20L, 0f, 8, SetType.WORKING)),
                ),
                ImportDraftExercise(
                    localId = 3L,
                    name = "   ",
                    exerciseKey = null,
                    sets = listOf(ImportDraftSet(30L, 0f, 10, SetType.WORKING)),
                ),
            ),
        )

        val workout = draft.toWorkout(
            date = LocalDate.of(2026, 5, 7),
            sourceKey = "2026-05-07.md",
            rawContent = "# 训练日志",
        )

        assertEquals(1, workout.exercises.size)
        val exercise = workout.exercises.single()
        assertEquals("杠铃卧推", exercise.name) // 自动 trim
        assertEquals("barbell-bench-press", exercise.exerciseKey)
        assertEquals(1, exercise.sets.size)
        assertEquals(10, exercise.sets.single().reps)
    }

    @Test
    fun `toWorkout returns empty exercise list when all exercises are blank or placeholder only`() {
        val draft = ImportDraftWorkout(
            exercises = listOf(
                ImportDraftExercise(
                    localId = 1L,
                    name = "",
                    exerciseKey = "barbell-bench-press",
                    sets = listOf(ImportDraftSet(10L, 60f, 10, SetType.WORKING)),
                ),
                ImportDraftExercise(
                    localId = 2L,
                    name = "   ",
                    exerciseKey = "pull-up",
                    sets = listOf(ImportDraftSet(20L, 0f, 8, SetType.WORKING)),
                ),
                ImportDraftExercise(
                    localId = 3L,
                    name = "深蹲",
                    exerciseKey = "barbell-squat",
                    sets = listOf(ImportDraftSet(30L, 0f, 0, SetType.WORKING)),
                ),
            ),
        )

        val workout = draft.toWorkout(
            date = LocalDate.of(2026, 5, 7),
            sourceKey = "2026-05-07.md",
            rawContent = "# 训练日志",
        )

        assertTrue(workout.exercises.isEmpty())
        assertEquals(0, draft.validExerciseCount)
    }

    @Test
    fun `toExerciseLogs filters out invalid exercises and trims names`() {
        val draft = ImportDraftWorkout(
            exercises = listOf(
                ImportDraftExercise(
                    localId = 1L,
                    name = "  引体向上  ",
                    exerciseKey = "pull-up",
                    sets = listOf(ImportDraftSet(10L, 0f, 8, SetType.WORKING)),
                ),
                ImportDraftExercise(
                    localId = 2L,
                    name = "",
                    exerciseKey = "barbell-bench-press",
                    sets = listOf(ImportDraftSet(20L, 60f, 10, SetType.WORKING)),
                ),
            ),
        )

        val exerciseLogs = draft.toExerciseLogs()
        assertEquals(1, exerciseLogs.size)
        assertEquals("引体向上", exerciseLogs.single().name)
        assertEquals("pull-up", exerciseLogs.single().exerciseKey)
    }
}
