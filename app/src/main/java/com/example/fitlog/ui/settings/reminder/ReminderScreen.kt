package com.example.fitlog.ui.settings.reminder

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fitlog.ui.components.CollapsingTitleScaffold
import com.example.fitlog.ui.components.FitLogCard
import com.example.fitlog.ui.components.SectionLabel

/**
 * 1. 容器层 (Stateful)
 */
@Composable
fun ReminderRoute(
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ReminderViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ReminderScreen(
        uiState = uiState,
        onBack = onBack,
        onEnabledChange = viewModel::onEnabledChange,
        onTimeChange = viewModel::onTimeChange,
        modifier = modifier,
    )
}

/**
 * 2. 纯 UI 展示层 (Stateless)
 *
 * 动态双标题交互契约见 [CollapsingTitleScaffold]。
 */
@Composable
fun ReminderScreen(
    uiState: ReminderUiState,
    onBack: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onTimeChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showTimePicker by remember { mutableStateOf(false) }

    CollapsingTitleScaffold(
        title = "Training Reminder",
        onBack = onBack,
        parentTitle = "Settings",
        modifier = modifier,
    ) {
        SectionLabel("提醒")
        FitLogCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("启用提醒", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "每天到点提醒你训练",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = uiState.enabled,
                    onCheckedChange = onEnabledChange,
                )
            }

            val timeText = "%02d:%02d".format(uiState.minutes / 60, uiState.minutes % 60)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("提醒时间", style = MaterialTheme.typography.titleMedium)
                    Text(
                        timeText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilledTonalButton(
                    onClick = { showTimePicker = true },
                    enabled = uiState.enabled,
                ) {
                    Text("修改")
                }
            }
        }

        Text(
            "提醒调度将在 WorkManager 接入后生效（TODO）",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))
    }

    // 时间选择对话框
    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = uiState.minutes / 60,
            initialMinute = uiState.minutes % 60,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        onTimeChange(timePickerState.hour * 60 + timePickerState.minute)
                        showTimePicker = false
                    },
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("取消") }
            },
            title = { Text("选择提醒时间") },
            text = { TimePicker(state = timePickerState) },
        )
    }
}

/**
 * 3. 预览层
 */
@Preview(showBackground = true)
@Composable
private fun ReminderScreenPreview() {
    ReminderScreen(
        uiState = ReminderUiState(enabled = true),
        onBack = {},
        onEnabledChange = {},
        onTimeChange = {},
    )
}
