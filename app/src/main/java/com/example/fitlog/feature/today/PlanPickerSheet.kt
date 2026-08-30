package com.example.fitlog.feature.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.fitlog.model.WorkoutPlan

/** 计划选择弹层：列出全部计划，标注当前激活，点选即设为激活计划。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlanPickerSheet(
    plans: List<WorkoutPlan>,
    activePlanId: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            "选择训练计划",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        if (plans.isEmpty()) {
            Text(
                "暂无可用计划",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
        }
        plans.forEach { plan ->
            ListItem(
                modifier = Modifier.clickable { onSelect(plan.id) },
                overlineContent = null,
                supportingContent = {
                    Text(
                        text = "${plan.sessionsPerWeek} 次/周 · ${plan.durationWeeks} 周",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                trailingContent = {
                    if (plan.id == activePlanId) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "当前激活",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                content = { Text(plan.name) },
            )
        }
        Spacer(modifier = Modifier.height(32.dp)) // 避开底部手势区
    }
}
