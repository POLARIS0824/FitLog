package com.example.fitlog.ui.components

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 设置页通用 UI 组件的 Compose 仪器化测试。
 *
 * 验证 [SectionLabel] 与 [SettingsCard] 的基本渲染契约。
 */
@RunWith(AndroidJUnit4::class)
class SettingsComponentsTest {

    /**
     * Compose 测试规则。
     */
    @get:Rule
    val composeRule = createComposeRule()

    /**
     * 测试 SectionLabel 渲染传入的标签文本。
     */
    @Test
    fun sectionLabel_displaysText() {
        composeRule.setContent {
            SectionLabel(text = "AI 服务商")
        }

        composeRule.onNodeWithText("AI 服务商").assertIsDisplayed()
    }

    /**
     * 测试 SettingsCard 渲染其插槽内容。
     */
    @Test
    fun settingsCard_displaysSlotContent() {
        composeRule.setContent {
            SettingsCard {
                Text("卡片第一行")
                Text("卡片第二行")
            }
        }

        composeRule.onNodeWithText("卡片第一行").assertIsDisplayed()
        composeRule.onNodeWithText("卡片第二行").assertIsDisplayed()
    }
}
