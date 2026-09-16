package com.example.fitlog.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.fitlog.model.BodyPart
import com.example.fitlog.model.Exercise
import com.example.fitlog.model.Muscle
import com.example.fitlog.ui.theme.FitLogTheme

/**
 * 动作选择器：底部弹层 + 名称过滤 + 单击添加（已在列表中的动作置灰标记）。
 *
 * 训练会话页（添加动作）与数据导入编辑弹层共用；目录过滤为纯内存
 * contains 匹配（动作库约千余条，一次性全量内存过滤即可）。
 *
 * @param catalog 动作库目录
 * @param addedKeys 已添加动作的 id 集合（置灰防重）
 * @param onSelect 选中回调（选择后由调用方关闭弹层）
 * @param onDismiss 关闭回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExercisePickerSheet(
    catalog: List<Exercise>,
    addedKeys: Set<String>,
    onSelect: (Exercise) -> Unit,
    onDismiss: () -> Unit,
) {
    if (LocalInspectionMode.current) {
        // Preview 模式下避免使用 ModalBottomSheet，直接以抽屉样式容器渲染
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
                ExercisePickerContent(
                    catalog = catalog,
                    addedKeys = addedKeys,
                    onSelect = onSelect,
                )
            }
        }
    } else {
        ModalBottomSheet(onDismissRequest = onDismiss) {
            ExercisePickerContent(
                catalog = catalog,
                addedKeys = addedKeys,
                onSelect = onSelect,
            )
        }
    }
}

/** 动作选择器内容主体。 */
@Composable
private fun ExercisePickerContent(
    catalog: List<Exercise>,
    addedKeys: Set<String>,
    onSelect: (Exercise) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = if (query.isBlank()) {
        catalog
    } else {
        catalog.filter { it.name.contains(query.trim(), ignoreCase = true) }
    }

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
            contentPadding = PaddingValues(bottom = 24.dp),
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

/** 预览层 */
@Preview(showBackground = true)
@Composable
private fun ExercisePickerSheetPreview() {
    FitLogTheme {
        ExercisePickerSheet(
            catalog = listOf(
                Exercise(
                    id = "barbell-bench-press",
                    name = "杠铃卧推",
                    primaryMuscles = listOf(Muscle.CHEST),
                    bodyPart = BodyPart.CHEST,
                ),
                Exercise(
                    id = "cable-pushdown",
                    name = "绳索下压",
                    primaryMuscles = listOf(Muscle.TRICEPS),
                    bodyPart = BodyPart.UPPER_ARMS,
                ),
            ),
            addedKeys = setOf("barbell-bench-press"),
            onSelect = {},
            onDismiss = {},
        )
    }
}
