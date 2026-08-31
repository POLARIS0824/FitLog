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
import com.example.fitlog.model.ai.AgentStep
import com.example.fitlog.model.ai.AgentStepType
import com.example.fitlog.model.ai.ChatThreadMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [ChatScreen] 的 Compose UI 仪器化测试。
 *
 * 验证输入框事件转发、发送按钮的启用/禁用、进行中时间线的可见性，
 * 以及 assistant 消息时间线的折叠/展开。
 *
 * 消息渲染路径（[UserMessageBubble] / AI 纯文本）待后续补充覆盖。
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
     * 测试发送中状态：发送按钮被禁用，且列表尾部显示进行中的执行过程时间线。
     */
    @Test
    fun sendingState_disablesButtonAndShowsThinkingIndicator() {
        composeRule.setContent {
            ChatScreen(
                uiState = ChatUiState(
                    isSending = true,
                    activeRun = ActiveRun(runId = "test-run"),
                ),
                onInputChange = {},
                onSend = {},
            )
        }

        composeRule.onNodeWithContentDescription("发送").assertIsNotEnabled()
        composeRule.onNodeWithText("正在思考", substring = true).assertIsDisplayed()
    }

    /**
     * 测试非发送中状态：发送按钮可用，且不显示进行中的时间线。
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
        composeRule.onNodeWithText("正在思考", substring = true).assertDoesNotExist()
    }

    /**
     * 测试含步骤的 assistant 消息：默认渲染折叠的时间线（"已思考 Xs"），
     * 点击后展开可见步骤行。
     */
    @Test
    fun assistantMessageWithSteps_rendersCollapsibleTimeline() {
        val steps = listOf(
            AgentStep(id = 1, type = AgentStepType.THINKING, label = "我先查一下训练记录", elapsedMs = 1_000),
            AgentStep(
                id = 2,
                type = AgentStepType.TOOL_CALL,
                toolKey = "getRecentWorkouts",
                label = "查询最近训练",
                detail = "最近 5 次",
                elapsedMs = 2_000,
            ),
        )
        composeRule.setContent {
            ChatScreen(
                uiState = ChatUiState(
                    messages = listOf(
                        ChatThreadMessage(
                            id = 10,
                            role = "assistant",
                            content = "基于你的训练数据，建议……",
                            steps = steps,
                            durationMs = 45_000,
                        ),
                    ),
                ),
                onInputChange = {},
                onSend = {},
            )
        }

        composeRule.onNodeWithText("已思考 45s").assertIsDisplayed()
        composeRule.onNodeWithText("查询最近训练").assertDoesNotExist()

        composeRule.onNodeWithText("已思考 45s").performClick()

        composeRule.onNodeWithText("查询最近训练").assertIsDisplayed()
        composeRule.onNodeWithText("最近 5 次").assertIsDisplayed()
    }
}
