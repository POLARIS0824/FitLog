package com.example.fitlog.feature.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [ChatScreen] 的 Compose UI 仪器化测试。
 *
 * 验证输入框事件转发、发送按钮的启用/禁用与"AI 正在思考"提示的可见性。
 *
 * 注意：测试始终使用空消息列表——[MessageBubble] 目前仍是 `TODO()` 未实现，
 * 渲染任何消息都会抛出 NotImplementedError，因此消息气泡路径刻意不覆盖。
 */
@RunWith(AndroidJUnit4::class)
class ChatScreenTest {

    /**
     * Compose 测试规则（使用 ui-test-manifest 提供的宿主 Activity）。
     */
    @get:Rule
    val composeRule = createComposeRule()

    /**
     * 测试在输入框中键入文本时，onInputChange 收到最新全文。
     */
    @Test
    fun typingInInputField_forwardsEvents() {
        var state by mutableStateOf(ChatUiState())
        composeRule.setContent {
            ChatScreen(
                uiState = state,
                onInputChange = { state = state.copy(input = it) },
                onSend = {},
            )
        }

        composeRule.onNode(hasSetTextAction()).performTextInput("今天练胸")

        composeRule.runOnIdle {
            assertEquals("今天练胸", state.input)
        }
        composeRule.onNodeWithText("今天练胸").assertIsDisplayed()
    }

    /**
     * 测试点击发送按钮触发 onSend 回调。
     */
    @Test
    fun sendButton_click_invokesOnSend() {
        var sendClicked = false
        composeRule.setContent {
            ChatScreen(
                uiState = ChatUiState(input = "你好"),
                onInputChange = {},
                onSend = { sendClicked = true },
            )
        }

        composeRule.onNodeWithContentDescription("发送").assertIsEnabled().performClick()

        assertTrue(sendClicked)
    }

    /**
     * 测试发送中状态：发送按钮被禁用，且显示"AI 正在思考"提示。
     */
    @Test
    fun sendingState_disablesButtonAndShowsThinkingIndicator() {
        composeRule.setContent {
            ChatScreen(
                uiState = ChatUiState(isSending = true),
                onInputChange = {},
                onSend = {},
            )
        }

        composeRule.onNodeWithContentDescription("发送").assertIsNotEnabled()
        composeRule.onNodeWithText("AI 正在思考…").assertIsDisplayed()
    }

    /**
     * 测试非发送中状态：发送按钮可用，且不显示"AI 正在思考"提示。
     */
    @Test
    fun idleState_enablesButtonAndHidesThinkingIndicator() {
        composeRule.setContent {
            ChatScreen(
                uiState = ChatUiState(),
                onInputChange = {},
                onSend = {},
            )
        }

        composeRule.onNodeWithContentDescription("发送").assertIsEnabled()
        composeRule.onNodeWithText("AI 正在思考…").assertDoesNotExist()
    }
}
