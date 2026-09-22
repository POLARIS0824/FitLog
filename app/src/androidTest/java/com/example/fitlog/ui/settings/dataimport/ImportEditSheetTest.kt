package com.example.fitlog.ui.settings.dataimport

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.fitlog.model.SetType
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [ImportEditSheet] 的 Compose UI 仪器化测试。
 *
 * 验证保存中（isSaving=true）表单输入框与动作按钮禁用，而「取消」按钮始终可用。
 */
@RunWith(AndroidJUnit4::class)
class ImportEditSheetTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun testFormDisabledWhileSaving_cancelRemainsEnabled() {
        val testDraft = ImportDraftWorkout(
            feelings = "感觉良好",
            exercises = listOf(
                ImportDraftExercise(
                    localId = 1L,
                    name = "杠铃卧推",
                    exerciseKey = "barbell-bench-press",
                    sets = listOf(
                        ImportDraftSet(localId = 10L, weightKg = 80f, reps = 8, setType = SetType.WORKING),
                    ),
                ),
            ),
        )

        val callbacks = ImportEditCallbacks(
            onFeelingsChange = {},
            onExerciseNameChange = { _, _ -> },
            onSetChange = { _, _, _, _ -> },
            onToggleSetType = { _, _ -> },
            onRemoveSet = { _, _ -> },
            onAddSet = {},
            onRemoveExercise = {},
            onAddExercise = {},
            onSave = {},
            onDismiss = {},
        )

        var isSaving by mutableStateOf(false)

        composeRule.setContent {
            ImportEditSheet(
                title = "2026-05-07.md · 2026-05-07",
                draft = testDraft,
                catalog = emptyList(),
                callbacks = callbacks,
                isSaving = isSaving,
            )
        }

        // isSaving = false 时，保存与取消均启用
        composeRule.onNodeWithText("保存").assertIsEnabled()
        composeRule.onNodeWithText("取消").assertIsEnabled()
        composeRule.onNodeWithText("添加动作").assertIsEnabled()
        composeRule.onNodeWithText("添加一组").assertIsEnabled()

        // 切换为 isSaving = true
        isSaving = true

        // 表单与保存按钮禁用，取消按钮仍保持启用
        composeRule.onNodeWithText("保存").assertIsNotEnabled()
        composeRule.onNodeWithText("取消").assertIsEnabled()
        composeRule.onNodeWithText("添加动作").assertIsNotEnabled()
        composeRule.onNodeWithText("添加一组").assertIsNotEnabled()
    }
}
