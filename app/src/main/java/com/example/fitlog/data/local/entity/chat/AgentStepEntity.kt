package com.example.fitlog.data.local.entity.chat

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Agent 执行过程时间线的单步实体（"我帮你查一下" / 调用了什么工具 / 请求确认）。
 *
 * 每轮 Agent 运行拥有一个 runId（UUID），步骤按 [stepOrder] 排序；
 * 运行成功结束时最终回答作为 [ChatMessageEntity] 落库并携带同一 runId，
 * 回放时据此把步骤挂到消息上。运行中途出错/放弃的步骤成为无挂载的孤儿数据，
 * 回放时不渲染（量小，不做清理）。
 *
 * @property id 自增主键
 * @property runId 所属 Agent 运行 id
 * @property stepOrder 运行内步骤序号（从 0 递增，展示排序用）
 * @property type 步骤类型："thinking" / "tool_call" / "confirm_request"
 * @property toolKey 工具函数名（如 getRecentWorkouts；thinking 步骤为 null），供图标选择
 * @property label 展示主文本（思考原文或工具中文名，落库时格式化）
 * @property detail 展示副文本（参数摘要，如"最近 5 次"；无则为 null）
 * @property elapsedMs 相对本轮运行开始的活跃耗时（毫秒，确认等待不计入）
 * @property createdAt 落库时间（epoch 毫秒）
 */
@Entity(
    tableName = "agent_steps",
    indices = [Index(value = ["runId", "stepOrder"])],
)
data class AgentStepEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val runId: String,
    val stepOrder: Int,
    val type: String,
    val toolKey: String?,
    val label: String,
    val detail: String?,
    val elapsedMs: Long,
    val createdAt: Long,
)
