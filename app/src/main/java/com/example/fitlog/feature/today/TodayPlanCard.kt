package com.example.fitlog.feature.today

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.fitlog.ui.components.FitLogCard

/** 今日训练计划卡片：标题 + 状态徽章 + 进度条 + 三态按钮。 */
@Composable
internal fun TodayPlanCard(
    todayPlan: TodayPlanState,
    onActionClick: () -> Unit,
) {
    FitLogCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = todayPlan.title,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = todayPlan.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            PlanStatusChip(status = todayPlan.status)
        }

        if (todayPlan.status != PlanStatus.NO_PLAN) {
            LinearProgressIndicator(
                progress = { todayPlan.progress },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = todayPlan.progressPercentageText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        when (todayPlan.status) {
            PlanStatus.NOT_STARTED, PlanStatus.IN_PROGRESS -> {
                Button(
                    onClick = onActionClick,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(todayPlan.buttonText)
                }
            }

            PlanStatus.COMPLETED, PlanStatus.NO_PLAN -> {
                FilledTonalButton(
                    onClick = onActionClick,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(todayPlan.buttonText)
                }
            }
        }
    }
}

/**
 * 计划状态徽章：纯展示的静态 pill。
 *
 * 不用 AssistChip——它是可聚焦可点击的交互组件，onClick 为空时 TalkBack
 * 仍会将其宣读为按钮，误导无障碍用户。
 */
@Composable
private fun PlanStatusChip(status: PlanStatus) {
    val label = when (status) {
        PlanStatus.NO_PLAN -> "无计划"
        PlanStatus.NOT_STARTED -> "未开始"
        PlanStatus.IN_PROGRESS -> "进行中"
        PlanStatus.COMPLETED -> "已完成"
    }
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}
