package com.example.fitlog.feature.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.fitlog.model.Exercise
import com.example.fitlog.model.SetType
import com.example.fitlog.ui.components.FitLogCard
import com.example.fitlog.ui.theme.fitLogColors
import com.example.fitlog.util.VolumeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

/**
 * 进行中训练会话视图：会话摘要（课次名 + 已练时长 + 实时组数/容量）、
 * 动作卡片列表（每组重量/次数/类型逐键落库）、添加动作选择器、
 * 结束（含感受录入）/放弃确认。
 *
 * 组编辑的提交模型：文本框本地状态 `remember(set.id)` 隔离，逐键解析提交 DB，
 * 由 [activeSession] 流反向驱动刷新——本地状态不随流重置，避免打字被覆盖。
 *
 * @param session 进行中的会话投影（非空）
 * @param exerciseCatalog 动作库目录（添加动作选择器）
 * @param onFinishSession 结束会话（参数为感受，可空）
 * @param onDiscardSession 放弃会话
 * @param onAddExercise 添加动作
 * @param onRemoveExercise 移除动作（参数 exerciseLogId）
 * @param onAddSet 追加一组（参数 exerciseLogId）
 * @param onUpdateSet 更新一组（setId, 重量kg, 次数, 组类型）
 * @param onToggleSetType 翻转一组类型（setId；SQL 侧按 DB 当前值原子取反）
 * @param onRemoveSet 删除一组（参数 setId）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveSessionView(
    session: ActiveSession,
    exerciseCatalog: List<Exercise>,
    onFinishSession: (String?) -> Unit,
    onDiscardSession: () -> Unit,
    onAddExercise: (Exercise) -> Unit,
    onRemoveExercise: (Long) -> Unit,
    onAddSet: (Long) -> Unit,
    onUpdateSet: (Long, Float, Int, SetType) -> Unit,
    onToggleSetType: (Long) -> Unit,
    onRemoveSet: (Long) -> Unit,
) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    var showFinishDialog by rememberSaveable { mutableStateOf(false) }
    var showDiscardDialog by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        // ── 会话摘要 ──
        SessionHeader(session = session)

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(session.exercises, key = { it.logId }) { exercise ->
                SessionExerciseCard(
                    exercise = exercise,
                    onRemove = { onRemoveExercise(exercise.logId) },
                    onAddSet = { onAddSet(exercise.logId) },
                    onUpdateSet = onUpdateSet,
                    onToggleSetType = onToggleSetType,
                    onRemoveSet = onRemoveSet,
                )
            }
            item(key = "add_exercise") {
                OutlinedButton(
                    onClick = { showPicker = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("添加动作")
                }
            }
        }

        // ── 底部操作栏（外层 Scaffold 的 innerPadding 已含底部系统栏 inset，
        //    此处不再叠加 navigationBarsPadding，避免双倍空隙）──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = { showDiscardDialog = true },
                modifier = Modifier.weight(1f),
            ) {
                Text("放弃")
            }
            Button(
                onClick = { showFinishDialog = true },
                enabled = session.hasLoggableContent,
                modifier = Modifier.weight(2f),
            ) {
                Text("结束训练")
            }
        }
    }

    if (showPicker) {
        ExercisePickerSheet(
            catalog = exerciseCatalog,
            addedKeys = session.exercises.mapNotNull { it.exerciseKey }.toSet(),
            onSelect = {
                onAddExercise(it)
                // 选择后关闭弹层：VM 侧防重守卫读的是投影快照，
                // 弹层不关时快速重复点击会在流重发前插入重复动作
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }

    if (showFinishDialog) {
        FinishSessionDialog(
            session = session,
            onConfirm = {
                showFinishDialog = false
                onFinishSession(it)
            },
            onDismiss = { showFinishDialog = false },
        )
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("放弃本次训练？") },
            text = { Text("已录入的组数与重量将全部删除，且不会计入训练记录。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog = false
                        onDiscardSession()
                    },
                ) {
                    Text("放弃", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text("继续训练")
                }
            },
        )
    }
}

/** 会话头部：课次名/自由训练 + 每秒跳动的已练时长 + 实时组数与容量。 */
@Composable
private fun SessionHeader(session: ActiveSession) {
    // 已练时长：每秒刷新的本地状态（纯展示，不进 VM）
    val elapsedMs by produceState(initialValue = 0L, key1 = session.startedAtMs) {
        while (true) {
            value = System.currentTimeMillis() - session.startedAtMs
            delay(1_000)
        }
    }
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(
            text = session.planSessionName ?: "自由训练",
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = formatElapsed(elapsedMs),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(16.dp))
            Text(
                text = "${session.loggedWorkingSets} 组 · ${VolumeFormatter.formatVolume(session.loggedVolumeKg)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 会话中的单个动作卡片：名称 + 目标处方 + 组录入行 + 添加组/移除动作。 */
@Composable
private fun SessionExerciseCard(
    exercise: ActiveSessionExercise,
    onRemove: () -> Unit,
    onAddSet: () -> Unit,
    onUpdateSet: (Long, Float, Int, SetType) -> Unit,
    onToggleSetType: (Long) -> Unit,
    onRemoveSet: (Long) -> Unit,
) {
    FitLogCard(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = exercise.name,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    exercise.targetText?.let { target ->
                        Text(
                            text = "目标 $target",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "移除动作 ${exercise.name}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            // 表头（轻量提示行，非数据）
            Row(
                modifier = Modifier.padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "组",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(28.dp),
                )
                Text(
                    "重量(kg)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "次数",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "类型",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(64.dp),
                )
                Spacer(Modifier.width(40.dp))
            }

            exercise.sets.forEachIndexed { index, set ->
                SessionSetRow(
                    index = index + 1,
                    set = set,
                    onUpdate = { weightKg, reps, type -> onUpdateSet(set.id, weightKg, reps, type) },
                    onToggleType = { onToggleSetType(set.id) },
                    onRemove = { onRemoveSet(set.id) },
                )
            }

            TextButton(onClick = onAddSet) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("添加一组")
            }
    }
}

/**
 * 单组录入行：重量/次数文本框本地态 `remember(set.id)` + 逐键提交；类型 chip 切换。
 */
@Composable
private fun SessionSetRow(
    index: Int,
    set: ActiveSessionSet,
    onUpdate: (Float, Int, SetType) -> Unit,
    onToggleType: () -> Unit,
    onRemove: () -> Unit,
) {
    // 本地编辑态按 set.id 隔离：流刷新不重置输入；0 值显示为空（占位行视觉中性）
    var weightText by remember(set.id) {
        mutableStateOf(if (set.weightKg > 0f) formatWeight(set.weightKg) else "")
    }
    var repsText by remember(set.id) {
        mutableStateOf(if (set.reps > 0) set.reps.toString() else "")
    }

    Row(
        modifier = Modifier.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = index.toString(),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(28.dp),
        )
        OutlinedTextField(
            value = weightText,
            onValueChange = { text ->
                weightText = text
                onUpdate(text.toFloatOrNull() ?: 0f, repsText.toIntOrNull() ?: 0, set.setType)
            },
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp),
            singleLine = true,
            enabled = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            textStyle = MaterialTheme.typography.bodyMedium,
        )
        OutlinedTextField(
            value = repsText,
            onValueChange = { text ->
                repsText = text
                onUpdate(weightText.toFloatOrNull() ?: 0f, text.toIntOrNull() ?: 0, set.setType)
            },
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = MaterialTheme.typography.bodyMedium,
        )
        FilterChip(
            selected = set.setType == SetType.WORKING,
            // 翻转逻辑在 SQL 侧按 DB 当前值原子取反（onToggleType）：
            // 若以 set.setType 取反提交，流未及时重发时双击会写回同一值卡在热身
            onClick = onToggleType,
            label = {
                Text(
                    if (set.setType == SetType.WORKING) "正式" else "热身",
                    style = MaterialTheme.typography.labelSmall,
                )
            },
            modifier = Modifier.width(64.dp),
        )
        IconButton(onClick = onRemove, modifier = Modifier.width(40.dp)) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "删除第 $index 组",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 动作选择器：底部弹层 + 名称过滤 + 单击添加（已在会话中的动作置灰）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExercisePickerSheet(
    catalog: List<Exercise>,
    addedKeys: Set<String>,
    onSelect: (Exercise) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = if (query.isBlank()) {
        catalog
    } else {
        catalog.filter { it.name.contains(query.trim(), ignoreCase = true) }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Text("添加动作", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("搜索动作名称") },
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
            ) {
                items(filtered, key = { it.id }) { exercise ->
                    val added = exercise.id in addedKeys
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = exercise.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (added) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                            Text(
                                text = exercise.bodyPart.name.lowercase().replace('_', ' '),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (added) {
                            Text(
                                text = "已添加",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            TextButton(onClick = { onSelect(exercise) }) {
                                Text("添加")
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
                if (filtered.isEmpty()) {
                    item {
                        Text(
                            text = "没有匹配的动作",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 24.dp),
                        )
                    }
                }
            }
        }
    }
}

/** 结束训练对话框：内容摘要 + 可选感受输入；无有效内容时按钮禁用。 */
@Composable
private fun FinishSessionDialog(
    session: ActiveSession,
    onConfirm: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var feelings by rememberSaveable { mutableStateOf("") }
    val durationMin = ((System.currentTimeMillis() - session.startedAtMs) / 60_000L).coerceAtLeast(0)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("结束训练") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = "${session.loggedWorkingSets} 个正式组 · " +
                        VolumeFormatter.formatVolume(session.loggedVolumeKg) +
                        " · 约 $durationMin 分钟",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = feelings,
                    onValueChange = { feelings = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("今天的感受（可选）") },
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(feelings) }) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("继续训练")
            }
        },
    )
}

/** 毫秒 → "M:SS"（满一小时进 "H:MM:SS"）；数字口径固定 Locale.US。 */
private fun formatElapsed(elapsedMs: Long): String {
    val totalSeconds = (elapsedMs / 1000L).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}

/** 重量显示：整数值去掉小数尾巴（统一走 [VolumeFormatter.formatWeightKg] 收口）。 */
private fun formatWeight(weightKg: Float): String = VolumeFormatter.formatWeightKg(weightKg)
