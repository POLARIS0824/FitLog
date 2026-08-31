package com.example.fitlog.feature.chat

import com.example.fitlog.model.ai.AgentStep

/**
 * 正在进行中（或暂停等待确认）的一轮 Agent 运行。
 *
 * 列表尾部据此渲染展开的时间线卡片；运行正常结束时整轮步骤转为
 * [com.example.fitlog.model.ai.ChatThreadMessage.steps] 挂到最终回答上，本对象清空。
 *
 * @property runId 运行 id（UUID，与落库的 agent_steps.runId 一致）
 * @property steps 已发生的步骤（按时间升序）
 * @property activeMs 累计活跃耗时（毫秒；ViewModel 每秒 tick，确认等待期间暂停）
 * @property awaitingConfirmation 是否正暂停等待用户确认写操作
 */
data class ActiveRun(
    val runId: String,
    val steps: List<AgentStep> = emptyList(),
    val activeMs: Long = 0L,
    val awaitingConfirmation: Boolean = false,
)
