package com.example.fitlog.data.agent

import com.example.fitlog.data.repository.UserProfileRepository
import com.example.fitlog.model.ai.ChatMessage
import com.example.fitlog.model.ai.ChatRole
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

/**
 * 构造 agent 对话的 system prompt。
 *
 * 四段式结构：角色 → 上下文注入（日期 + 用户档案）→ 工具使用指引 → 回复风格。
 * 放在 data 层而非 model/ai/Prompt.kt，因为构造依赖 Repository。
 */
class AgentPromptBuilder @Inject constructor(
    private val userProfileRepository: UserProfileRepository,
) {

    /**
     * 构建本次对话的 system prompt 消息。
     *
     * 每轮对话开头构建一次——日期与档案在对话过程中可能变化。
     */
    suspend fun build(): ChatMessage {
        val today = LocalDate.now()
        val dateStr = today.format(DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE", Locale.CHINA))

        val profile = userProfileRepository.getFirst()
        val profileSummary = if (profile == null) {
            "用户尚未填写个人资料。"
        } else {
            buildString {
                append("用户资料：").append(profile.name)
                profile.age?.let { append("，${it}岁") }
                profile.gender?.let { append("，性别：${it.name}") }
                profile.height?.let { append("，身高：${it}cm") }
                profile.weight?.let { append("，体重：${it}kg") }
                profile.trainingGoal?.let { append("，训练目标：${it.name}") }
                append("。")
            }
        }

        val content = """
            你是 FitLog 的 AI 健身教练，可以通过工具访问用户的真实训练数据，回答要具体、个性化、可执行。

            【当前上下文】
            今天是 $dateStr。$profileSummary

            【工具使用规则】
            - 回答涉及用户的训练记录、训练计划、动作信息之前，必须先调用工具查询，严禁编造数据
            - 涉及日期的推算（如"这周""上次"）基于今天
            - 工具查无数据时如实告知用户，不要虚构
            - 拿到工具结果后直接回答，不要重复调用相同的工具

            【回复风格】
            简洁的中文回复，用数据支撑观点。不要使用 Markdown 表格（当前界面无法渲染）。
        """.trimIndent()

        return ChatMessage(role = ChatRole.SYSTEM, content = content)
    }
}
