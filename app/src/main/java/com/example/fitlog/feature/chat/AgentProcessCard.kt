package com.example.fitlog.feature.chat

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.fitlog.ui.components.FitLogCard
import com.example.fitlog.ui.components.TonalIcon
import com.example.fitlog.ui.theme.FitLogTheme

/**
 * Agent 执行过程时间线卡片（Perplexity "Worked for 45s" 式过程展示）。
 *
 * - 运行中：头部为波浪进度圈 + "正在思考 · Xs"（秒数实时跳动），步骤实时追加，
 *   默认展开；等待确认时文案切换为"等待确认 · Xs"。
 * - 完成：头部为"已思考 Xs"，默认折叠，点击整卡展开回看全部步骤。
 *
 * 步骤行 = 语义化 tonal 图标（思考/查询/写操作/确认）+ 主文本（思考原文或工具中文名）
 * + 副文本（参数摘要），行间以竖线相连，仿过程时间线视觉。
 *
 * @param steps 时间线步骤（按时间升序）
 * @param isRunning 是否仍在执行（true 时头部显示进行中状态）
 * @param awaitingConfirmation 是否暂停等待用户确认写操作（仅 isRunning 时有意义）
 * @param elapsedMs 展示耗时（毫秒）：运行中为实时活跃耗时，完成为最终活跃耗时
 * @param initiallyExpanded 初始展开态（运行中传 true，历史消息传 false）
 * @param modifier 修饰符
 */
@Composable
internal fun AgentProcessCard(
    steps: List<AgentStepUi>,
    isRunning: Boolean,
    elapsedMs: Long,
    awaitingConfirmation: Boolean = false,
    initiallyExpanded: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }

    FitLogCard(
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
        modifier = modifier
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            )
            .clickable { expanded = !expanded },
    ) {
        // ── 头部：状态指示 + 耗时文案 + 折叠箭头 ──
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (isRunning) {
                CircularWavyProgressIndicator(modifier = Modifier.size(16.dp))
            }
            Text(
                text = headerText(isRunning, awaitingConfirmation, elapsedMs),
                style = MaterialTheme.typography.titleSmall,
                color = if (isRunning) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "收起执行过程" else "展开执行过程",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(20.dp)
                    .rotate(if (expanded) 180f else 0f),
            )
        }

        // ── 步骤列表：展开时展示 ──
        if (expanded) {
            Column(
                modifier = Modifier.padding(top = 10.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                steps.forEachIndexed { index, step ->
                    StepRow(step)
                    if (index < steps.lastIndex) {
                        // 图标正下方的竖向连接线，串起时间线
                        Box(
                            modifier = Modifier
                                .padding(start = 11.dp)
                                .width(2.dp)
                                .height(12.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                    shape = RoundedCornerShape(1.dp),
                                ),
                        )
                    }
                }
            }
        }
    }
}

/** 头部文案：运行中 / 等待确认 / 已完成三态。 */
private fun headerText(isRunning: Boolean, awaitingConfirmation: Boolean, elapsedMs: Long): String =
    when {
        isRunning && awaitingConfirmation -> "等待确认 · " + AgentStepFormatter.formatDuration(elapsedMs)
        isRunning -> "正在思考 · " + AgentStepFormatter.formatDuration(elapsedMs)
        else -> "已思考 " + AgentStepFormatter.formatDuration(elapsedMs)
    }

/** 单个步骤行：tonal 图标 + 主/副文本。 */
@Composable
private fun StepRow(step: AgentStepUi) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TonalIcon(
            icon = stepIcon(step),
            index = stepTonalIndex(step),
            size = 24.dp,
        )
        Column(modifier = Modifier.padding(top = 2.dp)) {
            Text(
                text = step.label,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            step.detail?.let { detail ->
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 步骤图标：思考→灯泡；确认→核对；写工具→编辑；读工具→搜索。 */
private fun stepIcon(step: AgentStepUi): ImageVector = when {
    step.type == AgentStepType.THINKING -> Icons.Filled.Lightbulb
    step.type == AgentStepType.CONFIRM_REQUEST -> Icons.AutoMirrored.Filled.FactCheck
    step.toolKey != null && AgentStepFormatter.isWriteTool(step.toolKey) -> Icons.Filled.Edit
    else -> Icons.Filled.Search
}

/** 步骤图标 tonal 色槽：思考=primary、读工具=secondary、写/确认=tertiary（同类型同色，稳定不轮换）。 */
private fun stepTonalIndex(step: AgentStepUi): Int = when {
    step.type == AgentStepType.THINKING -> 0
    step.type == AgentStepType.CONFIRM_REQUEST -> 2
    step.toolKey != null && AgentStepFormatter.isWriteTool(step.toolKey) -> 2
    else -> 1
}

@Preview(name = "运行中")
@Composable
private fun AgentProcessCardRunningPreview() {
    FitLogTheme {
        AgentProcessCard(
            steps = listOf(
                AgentStepUi(id = 1, type = AgentStepType.THINKING, label = "我先查一下你最近的训练记录", elapsedMs = 1_200),
                AgentStepUi(id = 2, type = AgentStepType.TOOL_CALL, toolKey = "getRecentWorkouts", label = "查询最近训练", detail = "最近 5 次", elapsedMs = 2_400),
            ),
            isRunning = true,
            elapsedMs = 12_000,
            initiallyExpanded = true,
        )
    }
}

@Preview(name = "已完成折叠")
@Composable
private fun AgentProcessCardCollapsedPreview() {
    FitLogTheme {
        AgentProcessCard(
            steps = listOf(
                AgentStepUi(id = 1, type = AgentStepType.THINKING, label = "我先查一下你最近的训练记录", elapsedMs = 1_200),
                AgentStepUi(id = 2, type = AgentStepType.TOOL_CALL, toolKey = "getRecentWorkouts", label = "查询最近训练", detail = "最近 5 次", elapsedMs = 2_400),
                AgentStepUi(id = 3, type = AgentStepType.CONFIRM_REQUEST, toolKey = "logBodyWeight", label = "记录体重", detail = "72.5 kg", elapsedMs = 3_100),
            ),
            isRunning = false,
            elapsedMs = 45_000,
        )
    }
}

@Preview(name = "已完成展开")
@Composable
private fun AgentProcessCardExpandedPreview() {
    FitLogTheme {
        AgentProcessCard(
            steps = listOf(
                AgentStepUi(id = 1, type = AgentStepType.THINKING, label = "我先查一下你最近的训练记录", elapsedMs = 1_200),
                AgentStepUi(id = 2, type = AgentStepType.TOOL_CALL, toolKey = "getBodyMetrics", label = "查询体重趋势", detail = "近 30 天", elapsedMs = 2_400),
            ),
            isRunning = false,
            elapsedMs = 45_000,
            initiallyExpanded = true,
        )
    }
}
