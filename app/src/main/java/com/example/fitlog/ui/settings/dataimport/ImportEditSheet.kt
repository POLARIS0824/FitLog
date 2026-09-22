package com.example.fitlog.ui.settings.dataimport

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.fitlog.model.Exercise
import com.example.fitlog.model.SetType
import com.example.fitlog.ui.components.ExercisePickerSheet
import com.example.fitlog.ui.components.FitLogCard
import com.example.fitlog.ui.theme.FitLogTheme
import com.example.fitlog.util.VolumeFormatter

/**
 * 编辑弹层的操作集合（聚合 ViewModel 的草稿编辑回调，避免弹层与宿主页参数爆炸）。
 *
 * @param onFeelingsChange 修改感受/备注
 * @param onExerciseNameChange 修改动作名（exerciseLocalId, name）
 * @param onSetChange 更新一组数值（exerciseLocalId, setLocalId, weightKg, reps）
 * @param onToggleSetType 翻转一组类型（exerciseLocalId, setLocalId）
 * @param onRemoveSet 删除一组（exerciseLocalId, setLocalId）
 * @param onAddSet 追加一组（复制末组，exerciseLocalId）
 * @param onRemoveExercise 移除动作（exerciseLocalId）
 * @param onAddExercise 从动作库添加动作
 * @param onSave 保存编辑（动作名全量重跑动作库匹配）
 * @param onDismiss 关闭弹层（不保存）
 */
data class ImportEditCallbacks(
    val onFeelingsChange: (String) -> Unit,
    val onExerciseNameChange: (Long, String) -> Unit,
    val onSetChange: (Long, Long, Float, Int) -> Unit,
    val onToggleSetType: (Long, Long) -> Unit,
    val onRemoveSet: (Long, Long) -> Unit,
    val onAddSet: (Long) -> Unit,
    val onRemoveExercise: (Long) -> Unit,
    val onAddExercise: (Exercise) -> Unit,
    val onSave: () -> Unit,
    val onDismiss: () -> Unit,
)

/**
 * 导入条目编辑弹层：AI 解析结果的轻量编辑表单。
 *
 * 编辑缓冲在 ViewModel（表单状态约定），确认前不落库。组数值文本框采用
 * 会话页同款提交模型：本地文本态 `remember(localId)` 隔离，逐键解析提交——
 * 非法输入解析为 0（占位组）时文本框保持空串视觉，不被重置为 "0"。
 *
 * @param title 弹层标题（文件名 · 日期）
 * @param draft 当前编辑的草稿
 * @param catalog 动作库目录（「添加动作」选择器）
 * @param callbacks 操作集合（见 [ImportEditCallbacks]）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportEditSheet(
    title: String,
    draft: ImportDraftWorkout,
    catalog: List<Exercise>,
    callbacks: ImportEditCallbacks,
    isSaving: Boolean = false,
) {
    var showPicker by remember { mutableStateOf(false) }

    if (LocalInspectionMode.current) {
        // Preview 模式下避免使用 ModalBottomSheet（Window 与手势动画在 Preview 渲染引擎中不支持），直接以抽屉样式容器渲染
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 48.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            tonalElevation = 6.dp,
        ) {
            Column {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(vertical = 12.dp)
                        .size(width = 32.dp, height = 4.dp)
                        .background(
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            shape = CircleShape,
                        )
                )
                ImportEditSheetContent(
                    title = title,
                    draft = draft,
                    callbacks = callbacks,
                    onShowPicker = { showPicker = true },
                    isSaving = isSaving,
                )
            }
        }
    } else {
        ModalBottomSheet(onDismissRequest = callbacks.onDismiss) {
            ImportEditSheetContent(
                title = title,
                draft = draft,
                callbacks = callbacks,
                onShowPicker = { showPicker = true },
                isSaving = isSaving,
            )
        }
    }

    if (showPicker) {
        ExercisePickerSheet(
            catalog = catalog,
            addedKeys = draft.exercises.mapNotNull { it.exerciseKey }.toSet(),
            onSelect = { exercise ->
                callbacks.onAddExercise(exercise)
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }
}

/** 编辑弹层的主体内容区域。 */
@Composable
private fun ImportEditSheetContent(
    title: String,
    draft: ImportDraftWorkout,
    callbacks: ImportEditCallbacks,
    onShowPicker: () -> Unit,
    isSaving: Boolean = false,
) {
    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            value = draft.feelings,
            onValueChange = callbacks.onFeelingsChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !isSaving,
            placeholder = { Text("训练感受/备注（可选）") },
        )

        draft.exercises.forEach { exercise ->
            ImportDraftExerciseCard(
                exercise = exercise,
                callbacks = callbacks,
                enabled = !isSaving,
            )
        }

        OutlinedButton(
            onClick = onShowPicker,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isSaving,
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text("添加动作")
        }

        if (draft.validExerciseCount == 0) {
            Text(
                "没有有效明细（每组次数需大于 0，空动作/空组导入时会被剔除，" +
                    "该条将无法作为完整记录导入）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(onClick = callbacks.onDismiss, modifier = Modifier.weight(1f)) {
                Text("取消")
            }
            Button(
                onClick = callbacks.onSave,
                modifier = Modifier.weight(1f),
                enabled = !isSaving,
            ) {
                Text("保存")
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** 编辑弹层中的单个动作卡：名称（含匹配状态）+ 组录入行 + 添加组/移除动作。 */
@Composable
private fun ImportDraftExerciseCard(
    exercise: ImportDraftExercise,
    callbacks: ImportEditCallbacks,
    enabled: Boolean = true,
) {
    FitLogCard(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = exercise.name,
                onValueChange = { callbacks.onExerciseNameChange(exercise.localId, it) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                enabled = enabled,
                label = { Text("动作名") },
                // 匹配状态跟随草稿（保存时全量重跑匹配后刷新）：null = 未关联，
                // 导入按自由文本名保存，合法但无动作库关联统计
                supportingText = {
                    Text(
                        if (exercise.exerciseKey != null) "已关联动作库" else "未关联动作库（按自由文本保存）",
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
            )
            IconButton(
                onClick = { callbacks.onRemoveExercise(exercise.localId) },
                enabled = enabled,
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "移除动作 ${exercise.name}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

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
            ImportDraftSetRow(
                index = index + 1,
                set = set,
                exerciseLocalId = exercise.localId,
                callbacks = callbacks,
                enabled = enabled,
            )
        }

        TextButton(
            onClick = { callbacks.onAddSet(exercise.localId) },
            enabled = enabled,
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text("添加一组")
        }
    }
}

/**
 * 单组录入行：重量/次数文本框本地态 `remember(set.localId)` + 逐键提交；
 * 类型 chip 直接绑定草稿（与训练会话页 SessionSetRow 同款提交模型）。
 */
@Composable
private fun ImportDraftSetRow(
    index: Int,
    set: ImportDraftSet,
    exerciseLocalId: Long,
    callbacks: ImportEditCallbacks,
    enabled: Boolean = true,
) {
    // 本地编辑态按 set.localId 隔离：草稿刷新不重置输入；0 值显示为空（占位行视觉中性）
    var weightText by remember(set.localId) {
        mutableStateOf(if (set.weightKg > 0f) VolumeFormatter.formatWeightKg(set.weightKg) else "")
    }
    var repsText by remember(set.localId) {
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
                callbacks.onSetChange(
                    exerciseLocalId,
                    set.localId,
                    text.toFloatOrNull() ?: 0f,
                    repsText.toIntOrNull() ?: 0,
                )
            },
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp),
            singleLine = true,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            textStyle = MaterialTheme.typography.bodyMedium,
        )
        OutlinedTextField(
            value = repsText,
            onValueChange = { text ->
                repsText = text
                callbacks.onSetChange(
                    exerciseLocalId,
                    set.localId,
                    weightText.toFloatOrNull() ?: 0f,
                    text.toIntOrNull() ?: 0,
                )
            },
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp),
            singleLine = true,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = MaterialTheme.typography.bodyMedium,
        )
        FilterChip(
            selected = set.setType == SetType.WORKING,
            onClick = { callbacks.onToggleSetType(exerciseLocalId, set.localId) },
            enabled = enabled,
            label = {
                Text(
                    if (set.setType == SetType.WORKING) "正式" else "热身",
                    style = MaterialTheme.typography.labelSmall,
                )
            },
            modifier = Modifier.width(64.dp),
        )
        IconButton(
            onClick = { callbacks.onRemoveSet(exerciseLocalId, set.localId) },
            enabled = enabled,
            modifier = Modifier.width(40.dp),
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "删除第 $index 组",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 预览层 */
@Preview(showBackground = true)
@Composable
private fun ImportEditSheetPreview() {
    FitLogTheme {
        ImportEditSheet(
            title = "2026-05-07.md · 2026-05-07",
            draft = ImportDraftWorkout(
                feelings = "状态不错",
                exercises = listOf(
                    ImportDraftExercise(
                        localId = 1,
                        name = "杠铃卧推",
                        exerciseKey = "barbell-bench-press",
                        sets = listOf(
                            ImportDraftSet(2, 60f, 10, SetType.WORKING),
                            ImportDraftSet(3, 40f, 12, SetType.WARMUP),
                        ),
                    ),
                ),
            ),
            catalog = emptyList(),
            callbacks = ImportEditCallbacks(
                onFeelingsChange = {},
                onExerciseNameChange = { _, _ -> },
                onSetChange = { _, _, _, _ -> },
                onToggleSetType = { _, _ -> },
                onRemoveSet = { _, _ -> },
                onAddSet = {},
                onRemoveExercise = {},
                onAddExercise = {},
                onSave = {},
                onDismiss = {},
            ),
        )
    }
}

