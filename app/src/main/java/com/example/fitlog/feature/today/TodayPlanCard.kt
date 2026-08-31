package com.example.fitlog.feature.today

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.fitlog.ui.components.FitLogCard

/**
 * 今日训练计划卡片：顶部计划/分化标签 + 状态徽章 + 标题/描述 + 进度条 + 动作打卡列表。
 *
 * @param todayPlan 今日计划状态
 * @param onToggleExerciseCheck 切换动作打卡状态回调
 * @param onActionClick 主动作（如选择计划）回调
 * @param onExerciseClick 点击动作行回调
 */
@Composable
internal fun TodayPlanCard(
    todayPlan: TodayPlanState,
    onToggleExerciseCheck: (String) -> Unit = {},
    onActionClick: () -> Unit = {},
    onExerciseClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    FitLogCard(
        modifier = modifier,
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ── 顶部头部区域 ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 顶部分化标签与状态徽章
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = todayPlan.tagText,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PlanStatusChip(status = todayPlan.status)
            }

            // 训练日主标题
            Text(
                text = todayPlan.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            // 训练日副标题描述
            Text(
                text = todayPlan.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // 进度条与百分比
            if (todayPlan.status != PlanStatus.NO_PLAN) {
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { todayPlan.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Text(
                    text = todayPlan.progressPercentageText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ── 空计划状态操作按钮 ──
        if (todayPlan.status == PlanStatus.NO_PLAN) {
            Button(
                onClick = onActionClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
            ) {
                Text(todayPlan.buttonText)
            }
        }

        // ── 动作清单与打卡列表 ──
        if (todayPlan.exercises.isNotEmpty()) {
            HorizontalDivider(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )

            Column(modifier = Modifier.fillMaxWidth()) {
                todayPlan.exercises.forEachIndexed { index, exercise ->
                    ExerciseItemRow(
                        exercise = exercise,
                        onToggleCheck = { onToggleExerciseCheck(exercise.exerciseKey) },
                        onItemClick = { onExerciseClick(exercise.exerciseKey) },
                    )
                    if (index < todayPlan.exercises.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                        )
                    }
                }
            }
        }
    }
}

/**
 * 今日计划中的单个动作项行视图。
 */
@Composable
private fun ExerciseItemRow(
    exercise: TodayPlanExerciseState,
    onToggleCheck: () -> Unit,
    onItemClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onItemClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 左侧圆形打卡框
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .clickable(onClick = onToggleCheck)
                .then(
                    if (exercise.isCompleted) {
                        Modifier.background(MaterialTheme.colorScheme.primary)
                    } else {
                        Modifier.border(
                            width = 1.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                            shape = CircleShape,
                        )
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (exercise.isCompleted) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "已打卡",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        // 中间动作名称与组数次数处方
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = exercise.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = exercise.setsRepsText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // 右侧建议/历史重量
        exercise.weightText?.let { weight ->
            Text(
                text = weight,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 计划状态徽章：纯展示的静态 pill。
 */
@Composable
private fun PlanStatusChip(status: PlanStatus) {
    val label = when (status) {
        PlanStatus.NO_PLAN -> "无计划"
        PlanStatus.NOT_STARTED -> "未开始"
        PlanStatus.IN_PROGRESS -> "进行中"
        PlanStatus.COMPLETED -> "已完成"
    }
    val containerColor = when (status) {
        PlanStatus.NO_PLAN, PlanStatus.NOT_STARTED -> MaterialTheme.colorScheme.surfaceVariant
        PlanStatus.IN_PROGRESS -> MaterialTheme.colorScheme.secondaryContainer
        PlanStatus.COMPLETED -> MaterialTheme.colorScheme.primaryContainer
    }
    val contentColor = when (status) {
        PlanStatus.NO_PLAN, PlanStatus.NOT_STARTED -> MaterialTheme.colorScheme.onSurfaceVariant
        PlanStatus.IN_PROGRESS -> MaterialTheme.colorScheme.onSecondaryContainer
        PlanStatus.COMPLETED -> MaterialTheme.colorScheme.onPrimaryContainer
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
        contentColor = contentColor,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

