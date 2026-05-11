package com.example.fitlog.feature.workout.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.fitlog.feature.workout.ExerciseUiModel

/**
 * 单个动作的卡片组件。
 *
 * 展示动作名称、组列表（[SetRow]）、添加组按钮和删除动作按钮。
 *
 * @param exercise 动作的 UI 模型
 * @param index 动作在训练列表中的索引
 * @param onAddSet 添加组的回调，参数为动作索引
 * @param onRemoveExercise 删除动作的回调，参数为动作索引
 * @param onRemoveSet 删除组的回调，参数为 (exerciseIndex, setIndex)
 * @param onUpdateSet 更新组的回调，参数为 (exerciseIndex, setIndex, weightKg, reps)
 */
@Composable
fun ExerciseCard(
    exercise: ExerciseUiModel,
    index: Int,
    onAddSet: (Int) -> Unit,
    onRemoveExercise: (Int) -> Unit,
    onRemoveSet: (Int, Int) -> Unit,
    onUpdateSet: (Int, Int, Float?, Int?) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = exercise.name,
                    style = MaterialTheme.typography.titleMedium,
                )
                IconButton(onClick = { onRemoveExercise(index) }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "删除动作",
                    )
                }
            }

            exercise.sets.forEachIndexed { setIndex, set ->
                SetRow(
                    set = set,
                    exerciseIndex = index,
                    setIndex = setIndex,
                    onRemove = onRemoveSet,
                    onUpdate = onUpdateSet,
                )
            }

            TextButton(
                onClick = { onAddSet(index) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                )
                Text("添加组")
            }
        }
    }
}
